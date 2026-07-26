# Liracalc Server

## Overview

This is a C++ web server application designed for creating, validating, and managing configuration files for a metalworking machine calculator program called "liracalc". The server provides a web interface for configuration creation with validation, stores configurations in a Firebird database, and serves them in a custom .cnf format for download.

## Main Purpose

The primary function of this server is to facilitate the creation and management of configuration files for a calculator program that assists in setting up metalworking machines. Key features include:

- **Configuration Creation**: Web-based interface for creating machine configurations with validation
- **Data Persistence**: Storage of configurations in Firebird SQL database
- **Format Export**: Serving configurations in custom .cnf format via `dwnldcnf.cpp`
- **RESTful API**: Full CRUD operations for configuration management

## Features

- **HTTP Server**: Full-featured HTTP server supporting GET, POST, PUT, DELETE, HEAD methods
- **Database Integration**: Uses Firebird SQL database for data persistence
- **Web Interface**: HTML/CSS/JavaScript frontend for configuration management
- **REST API**: RESTful API for programmatic access to configuration data
- **Internationalization**: Supports multiple languages (Ukrainian, English, Bulgarian)
- **Configuration Validation**: Input validation for configuration parameters

## Architecture

### Technology Stack

- **Backend**: C++ with POSIX sockets for cross-platform networking
- **Database**: Firebird SQL database with direct ibase.h integration
- **Frontend**: HTML/CSS/JavaScript for configuration interface
- **Build System**: GCC compiler

### Note on Implementation

**This is a university coursework project demonstrating REST API concepts.** As such, it contains intentionally complex solutions typical of academic projects:

- Direct Firebird integration using ibase.h (not recommended for production)
- Maximally complex database schema design for educational purposes
- Single-threaded architecture (runs in single-threaded mode)
- Simplified connection handling

## Project Status

⚠️ **Development Status**: The server is **unfinished** and not production-ready.

### Current Limitations

- **Single-threaded only**: Runs in single-threaded mode - if one browser holds a socket, no other connections can be established
- **No proper multi-threading**: Multi-threading implementation is incomplete
- **Connection management**: Requires external proxy/reverse proxy for proper connection handling

### Recommended Deployment

For production use, run through a reverse proxy that manages connections and threads:

```bash
# During development (Caddy proxy)
# caddy reverse-proxy --to :8080

# Current deployment (Java server proxy)
# Custom Java server handles connection management and forwards requests
```

## Core Components

- **KM_server.cpp**: Main HTTP server application with request routing
- **Database Layer**: Firebird integration for configuration storage
- **Web Interface**: HTML pages for configuration management and demonstration
- **Business Logic**: Configuration validation and processing handlers
- **dwnldcnf.cpp**: Custom .cnf format export functionality

## Installation

### Prerequisites

- **Linux**: GCC compiler, Firebird SQL client library
- **Windows**: Visual Studio or MinGW with Firebird client
- Firebird SQL server installed and running

### Dependencies

```bash
# Linux dependencies
sudo apt-get install gcc g++ libfbclient2 firebird-dev

# Windows: Install Firebird SQL client library

## Building and Running

### Compilation

```bash
# Linux build
g++ KM_server.cpp my_time.cpp -o server.out -lfbclient -lpthread

# Debug build
g++ -DDEBUG KM_server.cpp my_time.cpp -o server_debug.out -lfbclient -lpthread
```

### Starting the Server

```bash
# Linux (single-threaded mode) - REQUIRES SUDO due to Firebird integration
sudo ./server.out

# Windows
server.exe
```

**Note**: Server runs on port 8080 (Linux) / 8081 (Windows) in single-threaded mode.

## Important Runtime Notes

### Firebird Integration Requirements

⚠️ **Sudo Required**: The server must be started with `sudo` due to Firebird database integration requirements.

### Process Management

⚠️ **Critical**: Once the server has made even a single database request, the process **cannot be terminated** with Ctrl+C.

**Process Behavior**:
- Normal Ctrl+C termination works only before any database operations
- After first database request: Process becomes unkillable via Ctrl+C
- Plain `kill` (SIGTERM) does not work either, for the same reason
- `kill -9` does work, but tears the Firebird attachment apart mid-flight — not a clean stop

### Proper Shutdown Procedure

For **correct server shutdown**:

1. **Send shutdown command**:
   ```bash
   curl "http://localhost:8080/?command=exit"
   # or from browser: http://localhost:8080/?command=exit
   ```

2. **Shutdown reverse proxy first** (if using one) to ensure socket cleanup

3. **Only then terminate the server process** if needed

**Why this happens**: the server is linked against `-lfbclient`, and `db.cpp` attaches to
`database.fdb` on the first request. From that moment the Firebird client library owns the
process's signal handling and swallows `SIGINT`/`SIGTERM`, so neither Ctrl+C nor `kill` ever
reaches the accept loop in `main()` — which, on top of that, is a bare `while(1)` with no exit
condition, so the process has no way to terminate itself.

`?command=exit` (`KM_server.cpp`, checked before any method dispatch, so it works on any path)
is therefore not a convenience: it is the **only** clean way to stop this server. Calling
`exit(0)` from inside the request handler unwinds normally and lets fbclient detach from the
database properly.

This is also why the crutch is specific to LiraCalc. `servak_na_winapi_relays` was cloned from
the same code body and inherited the same command, but it links no Firebird (`-pthread -ldl`)
and does not need it — a plain Ctrl+C stops it.

Note that `exit(0)` is a *clean* exit, so systemd's `Restart=on-failure` in `oldSrvr.service`
does not resurrect the process. That is a lucky side effect, not the reason the command exists:
it predates the service units, the build scripts and the tmux windows entirely.

## Header Contract With the Java Reverse Proxy

`servak_na_linux_2_java` (`OldServakHandler`) is the only client this server sees in production
— everything else is stripped and forwarded through it, and it sends header **names in
lowercase** (`content-length`, not `Content-Length`). `KM_server.cpp` (`decod_request` in
`KM_server.cpp`) matches header names with a plain lowercase `strcmp` against
`accept-language`/`host`/`user-agent`/`content-length`. If you test this server directly
(bypassing the Java proxy) with a client that sends canonical-case headers, lowercase the header
name yourself before comparing, or the field is silently ignored.

Request bodies are read into an initial 2999-byte buffer alongside the headers; if the declared
`Content-Length` is larger than what already arrived, the buffer is `realloc`ed to fit and the
remainder is read in a loop (bounded by `MAX_BODY_LEN`, 64 MB) until the full declared length is
received.

## Configuration Management

### Entities Overview

The system manages several types of configurations:

| Entity | Purpose | Description |
|--------|---------|-------------|
| **mash** | Machines | Metalworking machine definitions |
| **lira** | Calculator configs | Main calculator configuration entities |
| **zminny** | Variables | Configuration variables with validation |
| **umovy** | Conditions | Conditional logic for calculations |
| **npp** | Setpoints | Numerical setpoint values |

### API Endpoints

#### Machine Management
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/?m_id=X` | Load machine configuration |
| POST | `/add-mash` | Create new machine |
| PUT | `/save-mash` | Update machine |
| DELETE | `/delete-mash` | Delete machine |

#### Lira Configuration
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/add-lira` | Add lira configuration |
| PUT | `/save-lira` | Update lira config |
| DELETE | `/delete-lira` | Delete lira config |

#### Variables
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/add-zminna` | Add variable |
| PUT | `/save-zm` | Update variable |
| DELETE | `/delete-zm` | Delete variable |

#### Conditions
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/add-umova` | Add condition |
| PUT | `/save-usl` | Update condition |
| DELETE | `/delete-um` | Delete condition |

#### Setpoints
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/add-zm-npp` | Add setpoint |
| PUT | `/save-npp` | Update setpoint |
| DELETE | `/delete-zm-npp` | Delete setpoint |

#### File Operations
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/download?m_id=X` | Download .cnf configuration |
| GET | `/preview?m_id=X` | Preview configuration |

## Project Structure

```
servak_na_linux/server/
├── KM_server.cpp              # Main HTTP server application
├── db.cpp                     # Database operations
├── add.cpp                    # Add entity handlers
├── save.cpp                   # Update entity handlers
├── delete.cpp                 # Delete entity handlers
├── get_json.cpp               # JSON response handlers
├── dwnldcnf.cpp               # .cnf format export functionality
├── relay.cpp                  # Legacy relay control (deprecated) - quickly written code for an important master's course project on distributed embedded systems that implements a "cloud" for ESP32 IoT devices. Later its functionality was developed into a separate service "servak_na_winapi_relay", and this code was left as a memory. WARNING: Contains critical bug causing segmentation fault when deleting devices from active list under certain conditions
├── index.cpp                  # Main page generation
├── my_time.cpp/.h             # Time utilities
├── setup.cpp/.h               # Configuration utilities
├── json.hpp                   # JSON parsing library
├── database.fdb               # Firebird database file
├── rest.yaml                  # API documentation (partial)
└── www/                       # Web interface (demonstration)
    ├── *.html                 # Demo interface pages
    ├── css/                   # Stylesheets
    ├── js/                    # JavaScript files
    ├── DBscripts/firebird.sql # Firebird database schema
    └── instr/                 # Help documentation (placeholder files)
        ├── uk/                # Ukrainian documentation (demo files only)
        ├── en/                # English documentation (demo files only)
        └── bg/                # Bulgarian documentation (demo files only)
```

## Configuration

### Database Setup

1. Ensure Firebird SQL server is running
2. Database file `database.fdb` should be accessible
3. Update connection parameters in `db.cpp` if needed

### Server Configuration

- **Port**: Configurable in `KM_server.cpp` (8080 for Linux, 8081 for Windows)
- **Single-threaded mode**: Runs in single-threaded mode by default
- **Language Support**: Accept-Language header determines interface language

## Usage

### Web Interface

1. Start the server (preferably through a reverse proxy for multi-user access)
2. Access `http://localhost:8080` (Linux) or `http://localhost:8081` (Windows)
3. Use the web interface to:
   - Create and validate machine configurations
   - Manage lira calculator entities
   - Set up variables and conditions for calculations
   - Generate and download .cnf configuration files

### Programmatic Access

Use HTTP clients to interact with the REST API:

```bash
# Download configuration file
curl "http://localhost:8080/download?m_id=123" -o machine_config.cnf

# Add new machine
curl -X POST "http://localhost:8080/add-mash" \
  -d "mash[NAME]=Machine1&mash[M1]=config1"

# Update lira configuration
curl -X PUT "http://localhost:8080/save-lira" \
  -d "lira[M_ID]=1&lira[L_ID]=1&lira[NAME]=Config1&lira[MAGAZ]=100&lira[BR_KOL_LIR]=50"

# Update variable with validation
curl -X PUT "http://localhost:8080/save-zm" \
  -d "zm[M_ID]=1&zm[L_ID]=1&zm[Z_ID]=1&zm[BUKVA]=A&zm[NPP_S]=1&zm[NAME]=Variable1&zm[ZNACHENNJA]=10.5"
```

## Development

### Academic Project Characteristics

This project was developed as **university coursework** to demonstrate REST API concepts. As such, it includes several intentionally complex solutions:

- **Complex Database Schema**: Maximally complicated database structure for educational purposes
- **Direct Firebird Integration**: Using ibase.h for low-level database access demonstration
- **Multiple Implementation Patterns**: Various approaches to REST operations
- **Internationalization**: Multi-language support for comprehensive learning

### Code Organization

- **Main Server**: HTTP request parsing and routing in `KM_server.cpp`
- **Database Layer**: Direct Firebird SQL operations via `db.cpp`
- **Business Logic**: Configuration validation and processing in various handler files
- **Web Interface**: HTML generation and form handling for demonstration

### Adding New Features

1. Add new endpoints in `KM_server.cpp`
2. Implement handlers in appropriate modules (add.cpp, save.cpp, etc.)
3. Update database schema if needed
4. Add corresponding web interface elements in `www/`

## Troubleshooting

### Common Issues

1. **Single connection limitation**: Server runs in single-threaded mode - use reverse proxy for multiple users
2. **Database connection failed**: Check Firebird installation and database file permissions
3. **Port already in use**: Change PORT definition in `KM_server.cpp`
4. **Compilation errors**: Ensure Firebird development libraries are installed

### Debug Mode

For debugging, modify the build to include debug symbols:

```bash
g++ -DDEBUG KM_server.cpp my_time.cpp -o server_debug.out -lfbclient -lpthread
```

### Production Deployment

**⚠️ Not recommended for production use in current state**

For production deployment:

1. **Use reverse proxy**: Caddy, nginx, or custom Java server for connection management
2. **Implement proper multi-threading**: Complete the threading implementation
3. **Add connection pooling**: Better database connection management
4. **Enhanced validation**: Improve input validation and security
5. **Error handling**: More robust error management and logging

## License

University coursework project demonstrating REST API concepts and C++ web server development.

## Support

This project serves as educational material for understanding:
- REST API design and implementation
- C++ web server development
- Database integration patterns
- Web interface development

**Note on Documentation**: The help files in the `www/instr/` directory were intended to provide comprehensive multilingual instructions for form field completion but were never fully developed. They currently contain only placeholder/demo files that demonstrate the concept's feasibility rather than actual detailed instructions.
