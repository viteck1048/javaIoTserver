# Servak na С++ Relays

A Windows service application built with C++ and WinAPI for managing relay devices.

## Features

- Relay device management
- Web interface for control and monitoring
- Cross-platform build support (Windows/Linux)
- SQLite database for data persistence

## Hardware Components

This server works with ESP32-relay devices from the [rvs2](https://github.com/viteck1048/rvs2) repository. It represents the evolution of the original `relay.cpp` component with critical bugs fixed and inherited general structure from the liracalc server. Unlike the liracalc server, this service is not fully functional by itself - the Java proxy server provides SSL encryption and user authorization besides administering parallel connections.

## Building

### Windows
```bash
compil_and_run_server_relays.bat
```

### Linux
```bash
chmod +x build_linux.sh
./build_linux.sh
```

## Project Structure

- `source/` - Source code files
- `www/` - Web interface files

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
