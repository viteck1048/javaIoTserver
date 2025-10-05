# Multi-Server IoT and Web Platform

This project is a comprehensive multi-server system designed to handle web requests, manage IoT devices, and provide flexible backend services. The platform consists of a main Java-based server and several C++ based microservices.

## System Architecture

The project is composed of the following key components:

- **`servak_na_linux_2_java`**: The primary server written in Java. It acts as the main entry point for HTTP/HTTPS requests, manages SSL certificates, supports PHP, and delegates tasks to other backend services.
- **`servak_na_winapi_relays`**: A C++ based server for managing ESP32-relay devices, built with the WinAPI for Windows but also compatible with Linux.
- **`servak_na_linux`**: An additional C++ backend server for other specific tasks.

The servers are designed to run concurrently and are managed by a set of startup scripts.

## Key Features

- **Modular, Multi-Server Design**: Separates concerns between a main Java server and specialized C++ services.
- **HTTP/HTTPS Handling**: The Java server processes web requests, including serving static files and PHP scripts.
- **IoT Device Management**: Core functionality for communicating with and managing IoT devices.
- **Automated SSL Management**: The Java server can automatically create and renew SSL certificates.
- **Cross-Platform**: Includes components and scripts for both Windows and Linux environments.
- **Centralized Management**: Startup scripts in `strt-stp_srvrs_*` directories allow for easy management of all servers.

## How to Run the System

The entire system can be started and stopped using the provided scripts.

### On Linux

The `autorun_tmux.sh` script is used to launch all servers within a `tmux` session, which is ideal for background operation on a server.

1.  **Navigate to the script directory**:
    ```bash
    cd strt-stp_srvrs_linux/
    ```

2.  **Make the script executable**:
    ```bash
    chmod +x autorun_tmux.sh
    ```

3.  **Run the script**:
    ```bash
    ./autorun_tmux.sh
    ```
    This will start all servers in a new `tmux` session named `autorun`.

### On Windows

Use the batch scripts provided in the `strt-stp_srvrs_win` directory.

1.  **Navigate to the script directory**:
    ```batch
    cd strt-stp_srvrs_win\
    ```

2.  **To start all servers**, run:
    ```batch
    start_servers.bat
    ```

3.  **To stop all servers**, run:
    ```batch
    stop_servers.bat
    ```

## Configuration (`config.ini`)

The main Java server (`servak_na_linux_2_java`) requires a configuration file named `config.ini` to be present in its root directory. This file contains critical parameters for database connections, web paths, SSL, and integrations with other services.

**Note:** This file is excluded from Git by `.gitignore` to prevent sensitive data from being committed. You must create this file manually before starting the server.

### Example Minimal Configuration

```ini
# -------------------
# General Settings
# -------------------
host = your.domain.com
invite = your_secret_invite_code
www_directory = secure_www_path
www80_directory = open_www_path
dbg_post_message_path = dbg_post_message_path

# -------------------
# Database
# -------------------
db_file = db_path
db_user = user
db_password = password

# -------------------
# SSL/TLS Keystore
# -------------------
keyStoreFile = keyStore_path
keyStorePassword = keyStore_password

# -------------------
# Optional Modules
# -------------------
# Enable HTTPS (true/false)
https_run = false

# Enable AVR integration (true/false)
avr = false

# Enable LiraCalc integration (true/false)
liraCalc = false

# Enable ESP integration (true/false)
esp = false
```

### All Configuration Parameters

#### General (Required)
- `host`: The domain name of the server.
- `invite`: A secret code or key used for specific functionalities.
- `www_directory`: The root directory for the main web server content (HTTPS).
- `www80_directory`: The root directory for content served on port 80 (used for ACME challenges).
- `db_file`: The file path for the SQLite database.
- `db_user`: The username for the database.
- `db_password`: The password for the database.
- `keyStoreFile`: The path to the Java KeyStore file (`.jks`) for SSL certificates.
- `keyStorePassword`: The password for the KeyStore.
- `dbg_post_message_path`: A file path for logging raw POST messages for debugging purposes.

#### HTTPS and ACME (Required if `https_run = true`)
- `https_run`: Set to `true` to enable the HTTPS server on port 443.
- `keyStoreAlias`: The alias for the certificate within the KeyStore.
- `acme_server_url`: The URL for the ACME server (e.g., Let's Encrypt's staging or production URL).
- `acme_contact`: A contact email for ACME registration (e.g., `mailto:you@example.com`).
- `acme_account_key_file`: Path to the file storing the ACME account private key.
- `acme_domain_key_file`: Path to the file storing the domain's private key.
- `acme_certificate_file`: Path to the file where the fetched SSL certificate will be stored.
- `acme_challenge_path`: The web-accessible path where ACME HTTP challenge files are placed.

#### AVR Integration (Optional)
- `avr`: Set to `true` to enable the AVR integration module.
- `avr_port`: The network port for the AVR server.
- `avr_path`: A specific URL path for AVR-related requests.
- `avr_user_agent`: The expected User-Agent string from AVR clients.

**Hardware Components**: The Java server works with AVR-relay devices from [AVR_Relay](https://github.com/viteck1048/AVR_Relay) repository with the network module from [WiFi-modul-for-AVR_Relay](https://github.com/viteck1048/WiFi-modul-for-AVR_Relay).

#### LiraCalc Integration (Optional)
- `liraCalc`: Set to `true` to enable integration with the LiraCalc server.
- `port_liraCalc_server`: The port of the external LiraCalc server.
- `ip_liraCalc_server`: The IP address of the LiraCalc server.

#### ESP Relay Integration (Optional)
- `esp`: Set to `true` to enable integration with the ESP relay server.
- `port_relay_server`: The port of the relay server.
- `ip_relay_server`: The IP address of the relay server.

#### Logging (Optional)
- `logToConsole`: Set to `true` or `false` to enable or disable logging to the console.
- `logToFile`: Set to `true` to enable logging to a file.
- `maxLogFileSize`: The maximum size of a log file in bytes before it gets rotated.
- `maxLogBackupIndex`: The maximum number of backup log files to keep.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
