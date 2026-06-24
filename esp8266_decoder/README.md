# MashineTimeWorks Service

> **Note on the folder name:** this directory is still called `esp8266_decoder/` for
> historical reasons — it started life as a small C++ microservice that decoded ESP8266
> POST payloads. It has since been rewritten into **MashineTimeWorks**, a multi-port
> C++ service that stores and serves machine-time data for 18 channels backed by SQLite.
> The build output binary is `server_8266_decoder.out`.

## What it does

- Serves an HTTP API for machine-time data over 18 channels (`/MachineTime18Channels/`).
- Persists data in a local SQLite database (`mashine_time.db`).
- Serves static files from the `www/` directory (falls back to `index.html`).
- Designed to sit behind the main Java gateway's reverse proxy (`uniproxy`).

## Architecture

- **Multi-port listeners** — `port_0` … `port_255` can each be enabled independently in
  `conf.ini`, every active port gets its own listener thread.
- **Per-port worker pools** — each listener owns a `WorkerPool` (size set by
  `port_N_workers`) that handles accepted connections.
- **Router** (`router.cpp`) dispatches by method + path to the `MachineTime` handlers.
- **Platform abstraction** (`platform.h`) — builds on both Linux and Windows.
- Crypto helpers (`MyCrypter.h`) and shared-key auth via the `conf.ini` keys.

## HTTP API

Base endpoint: `/MachineTime18Channels/`

| Method | Path                        | Purpose                                |
|--------|-----------------------------|----------------------------------------|
| GET    | `/MachineTime18Channels/?…` | Read channel data (requires query)     |
| GET    | `/MachineTime18Channels/`   | Serve `index.html` (no query)          |
| POST   | `/MachineTime18Channels/?…` | Create channel record                  |
| PUT    | `/MachineTime18Channels/?…` | Update channel record                  |
| DELETE | `/MachineTime18Channels/?…` | Delete channel record                  |
| GET    | any other path              | Serve matching static file from `www/` |

The mutating methods (POST/PUT/DELETE) require a non-empty query string.

## Build

```bash
cd servers/esp8266_decoder
make
# or use the helper scripts:
./compilAndRun.sh       # build + run
./compilAndRunDbg.sh    # build + run with debug flags
```

## Run

```bash
./server_8266_decoder.out                       # uses ./conf.ini by default
./server_8266_decoder.out -c other.ini          # custom config
./server_8266_decoder.out -p port_0_port=19000  # override a single param
```

CLI options:
- `-c, --config <file>` — config file (default `conf.ini`)
- `-p, --param <id>=<val>` — override a config value (repeatable)
- `-h, --help` — usage

## Configuration (`conf.ini`)

> ⚠️ `conf.ini` contains secret keys and is **excluded from Git** (`*.ini` in `.gitignore`).
> Create it manually on each deployment. Example skeleton:

```ini
serverName=MashineTimeWorks
db_path=mashine_time.db

[port_0]
port_0=true
port_0_port=18081
port_0_workers=14

[get18ChanalsMashineTimeWorks]
myRandKey1=...
myRandKey2=...
myRandKey3=...
myRandKey4=...
myStaticKeyResponce=...
myStaticKeyRequest=...

[agents]
save_db_agent=false
save_db_agent_period=3
```

- `serverName` — service identifier.
- `db_path` — path to the SQLite database file.
- `[port_N]` — enable a listener: `port_N=true`, `port_N_port`, `port_N_workers`.
- `[get18ChanalsMashineTimeWorks]` — shared keys for request/response authentication.
- `[agents]` — background DB-flush agent (`save_db_agent`, period in `save_db_agent_period`).

## Test

```bash
curl "http://127.0.0.1:18081/MachineTime18Channels/?<your-query>"
```
