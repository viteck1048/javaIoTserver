# esp8266_decoder — MachineTime HTTP Server

C++ HTTP server for tracking and persisting runtime statistics of industrial devices (ESP8266 or compatible software emulators). Supports up to 18 channels per device, stores per-day event history and aggregated runtime seconds in SQLite.

---

## Architecture

The server is split into a generic HTTP layer and application-specific code:

```
server.cpp          — entry point: config, initialization, port threads
├── WorkerPool      — per-port thread pool (worker_pool.cpp/h)
├── http_parser.cpp — HTTP request parser              ┐
├── http_utils.cpp  — static file server (www/)        │ generic HTTP layer
├── router.cpp      — method dispatcher                ┘
├── setup.cpp       — INI config parser with CLI override support
├── my_time.cpp     — time utilities, timezone handling
└── MachineTime.cpp — MachineTime business logic (time tracking, events, SQLite)
```

`router.cpp` is the seam between the two layers: generic requests fall through to `http_utils` (static files), while `/MachineTime18Channels/` routes are dispatched to `MachineTime.cpp`.

---

## Multi-port support

The server supports up to 256 independent TCP ports, each with its own worker pool. Ports are configured in `conf.ini` (see `conf.ini.example`).

Each active port gets a dedicated listener thread that accepts connections and dispatches them to a `WorkerPool`. Pools are independent — an overloaded port does not affect others.

---

## Static file serving

`http_utils.cpp` is a self-contained generic static file server. It resolves the request path relative to `www/` and serves the file with an appropriate `Content-Type`. Any frontend placed under `www/` is served automatically with no additional configuration.

---

## MachineTime module

One `MachineTime` instance = one device, identified by the `id` parameter.

**Protocol:** the device sends PUT requests with an encrypted binary body — an array of 20 int32 values:
- slots 0–10, 12–19 — accumulated seconds for channels 0–18
- slot 11 — new session flag (baseline reset)

**Time accounting:**

Data is stored in two layers:
- `this_session` — current open session (since the last reset)
- `month` / `old_month` — two months kept in memory (current and previous)

On a session reset, accumulated time is merged into the current day. On a day boundary, the day is persisted to SQLite. On a month boundary, the old month is evicted from memory (but remains in the DB).

**Start/stop event detection:**

A channel is considered running when its time value increases between two packets, and stopped when it stays the same. A hysteresis of **3 consecutive confirmations** is required before the state flips, to suppress single spurious packets.

**State persistence across restarts:**

On shutdown, the current session is saved to the `channel_session` table. On startup it is restored and, if it belongs to a different day, closed into its own day rather than the current one.

---

## SQLite schema

| Table | Contents |
|---|---|
| `channel_history` | Aggregated seconds per channel per day |
| `channel_events` | Start/stop event timestamps (2-month rolling window) |
| `channel_session` | Current open session, for recovery after restart |
| `channel_names` | Human-readable names for channels 1–18 |

---

## HTTP API

All MachineTime endpoints are under `/MachineTime18Channels/` and require the `id` parameter identifying the device. GET requests pass additional parameters in the query string; non-GET requests from browser clients pass them in the request body.

| Method | Parameters | Action |
|---|---|---|
| POST | `id` | Handshake (connection setup, timezone negotiation) |
| PUT | `id` | Receive encrypted data packet from device |
| GET | `id` | List available day epochs |
| GET | `id`, `day` | Channel data for a given day |
| GET | `id`, `day`, `channel` | Start/stop events for a channel on a given day |
| GET | `ids` | List all registered device IDs |
| PUT | `id`, `name`, `channel` | Set channel name |
| DELETE | `id`, `channel` | Clear channel name |

Any other path is handled by the generic static file server.

---

## Configuration

See `conf.ini.example` for a full annotated template. Copy it to `conf.ini` and fill in real values.

The config format is plain `key=value`, one per line. Section headers (`[...]`) and lines starting with `#` are ignored. Everything after `=` to end of line is the value — inline comments are not supported.

**Command-line arguments:**
```
-c <file>        alternative config file (default: conf.ini)
-p key=value     override a config parameter (repeatable)
```

---

## Build

```bash
# Linux
./compilAndRun.sh

# Linux (debug)
./compilAndRunDbg.sh
```

Dependencies: C++17 standard library, SQLite3 (amalgamation in `source/`, not tracked by git).

The `.c` compilation block in the build scripts is intentionally commented out —
SQLite (`sqlite3.c`) is the only `.c` file in the project and only needs to be
compiled once. Uncomment that block on first build or after updating the SQLite
amalgamation, then comment it back out.

---

## Web frontend

The generic file server automatically serves everything under `www/`. The MachineTime frontend lives at `www/MachineTime18Channels/` and is one application running on top of the generic layer — it provides per-day runtime charts, start/stop event timeline, and multi-language support.
