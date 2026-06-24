# esp8266_decoder — MachineTime HTTP Server

C++ HTTP server for tracking and persisting runtime statistics of industrial devices (ESP8266 or compatible software emulators). Supports up to 18 channels per device, stores per-day event history and aggregated runtime seconds in SQLite.

---

## Architecture

```
server.cpp          — entry point: config, initialization, port threads
├── WorkerPool      — per-port thread pool (worker_pool.cpp/h)
├── router.cpp      — HTTP method dispatcher (GET/POST/PUT/DELETE)
├── MachineTime.cpp — core business logic (time tracking, events, SQLite)
├── http_parser.cpp — HTTP request parser
├── http_utils.cpp  — static file serving from www/
├── setup.cpp       — INI config parser with command-line override support
└── my_time.cpp     — time utilities, timezone handling
```

---

## Multi-port support

The server supports up to 256 independent TCP ports, each with its own worker pool. Ports are configured in `conf.ini`:

```ini
[port_0]
port_0=true
port_0_port=18081
port_0_workers=14
```

Each active port gets a dedicated listener thread that accepts connections and dispatches them to a `WorkerPool`. Pools are independent — an overloaded port does not affect others.

---

## MachineTime module

One `MachineTime` instance = one device, identified by the `?id=` query parameter.

**Protocol:** PUT request with an encrypted binary body — an array of 20 int32 values:
- slots 0–10, 12–19 — accumulated seconds for channels 0–18
- slot 11 — new session flag (baseline reset)

**Time accounting:**

Data is stored in two layers:
- `this_session` — current open session (since the last reset)
- `month` / `old_month` — two months kept in memory (current and previous)

On a session reset (`flag_new_session`), accumulated time is merged into the current day. On a day boundary, the day is persisted to SQLite. On a month boundary, the old month is evicted from memory (but remains in the DB).

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

All requests go to `/MachineTime18Channels/?id=<device_id>`.

| Method | Parameters | Action |
|---|---|---|
| POST | — | Handshake (connection setup, timezone negotiation) |
| PUT | — | Receive data packet |
| GET | — | List available day epochs |
| GET | `?day=<epoch>` | Channel data for a given day |
| GET | `?day=<epoch>&channel=<n>` | Start/stop events for a channel on a given day |
| GET | `?ids` | List registered device IDs |
| PUT | `?name&channel=<n>&name=<str>` | Set channel name |
| DELETE | `?channel=<n>` | Clear channel name |

---

## Configuration

```ini
serverName=MyServer
db_path=mashine_time.db

[port_0]
port_0=true
port_0_port=18081
port_0_workers=14

[get18ChanalsMashineTimeWorks]
myRandKey1=111111
myRandKey2=222222
myRandKey3=333333
myRandKey4=444444
myStaticKeyResponce=your_response_key_here
myStaticKeyRequest=your_request_key_here

[agents]
save_db_agent=false        # enable background periodic save agent
save_db_agent_period=30    # save interval in minutes
```

Full template: `conf.ini.example`. Real config with keys: `conf.ini` (in `.gitignore`).

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

---

## Web frontend

`www/MachineTime18Channels/` — single-page app for viewing channel statistics: per-day runtime charts, start/stop event timeline, multi-language support (`translations.js`).
