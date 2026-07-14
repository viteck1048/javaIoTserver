# Java Server — Configuration Reference

Every key the server reads, what it does, and what happens if it is missing.

Config files themselves are **not tracked in git** — they hold passwords, tokens and
private keys. Only placeholders appear below, never real values.

---

## Launching and choosing a config file

```
java -cp ".:obj:lib/*" Servak [-c <name>.ini] [-p key=value ...]
```

| Argument | Meaning |
|---|---|
| *(none)* | Falls back to the built-in default name **`config.ini`** (`Servak.java:21`). |
| `-c <name>.ini` | Use a different file. This is how the launch script (`compilAndRun.sh`) supplies the real one; systemd runs that script, so switching configs means editing the script, not the code. |
| `-p key=value ...` | Override individual keys from the command line. |

### Filename validation is stricter than it looks

The name given to `-c` is split on dots and accepted **only if it has exactly two parts and
the second is `ini`**:

| Name | Result |
|---|---|
| `server.ini` | accepted |
| `config.ini` | accepted |
| `my.server.ini` | **rejected** — three parts |
| `config` | **rejected** — no extension |

On rejection the server prints `Invalid config file name: …` but **does not stop**. It
silently falls back to `config.ini`. If that file does not exist, the failure surfaces much
later and looks unrelated to the filename.

### `-p` overrides

```
java ... Servak -c server.ini -p https_run=false avr=false
```

`-p` consumes every following argument until it meets one **without an `=`**. Values land in
the same parameter map after the file has been read, so they **take precedence over it**.
Useful for one-off runs without touching the config.

---

## Startup validation

`Configs.validate()` runs right after the file and `-p` overrides are loaded. On failure the
server prints `Missing param: <key>` (or `Invalid param: …`) followed by `Invalid configs`,
and **exits without starting**.

**Always required:**
`invite`, `host`, `www_directory`, `www80_directory`, `dbg_post_message_path`,
`db_file`, `db_user`, `db_password`, `keyStoreFile`, `keyStorePassword`.

**Also checked:** `key_expiration_time` must parse to a value **greater than 0**. A missing
key parses as `0`, so this single check catches both "absent" and "zero".

**Conditionally required** — only when the matching service is enabled:

| If | Then required |
|---|---|
| `https_run=true` | `keyStoreAlias`, `acme_server_url`, `acme_contact`, `acme_account_key_file`, `acme_domain_key_file`, `acme_certificate_file`, `acme_challenge_path` |
| `avr=true` | `avr_port` (≠ 0), `avr_path`, `avr_user_agent` |
| `liraCalc=true` | `port_liraCalc_server` (≠ 0), `ip_liraCalc_server` |
| `esp=true` | `port_relay_server` (≠ 0), `ip_relay_server` |

Nothing else is validated. A missing key outside these lists surfaces only at runtime.

---

## Parser semantics

Format is plain `key=value`, one per line (`Configs.init()`).

### Comments and blank lines

A line is skipped entirely if it starts with `#`, `;`, or `[`, or is blank.

**A `#` in the middle of a line is not stripped.** It becomes part of the value and will
break numeric parsing:

```ini
port=8080 # main port     ← WRONG: value becomes "8080 # main port", parses as 0
```

Put comments on their own lines.

### Values

The line is split on the **first** `=` only, so a value may itself contain `=`
(useful for tokens and header strings). Leading and trailing whitespace is trimmed.

### Accessors and their fallbacks

| Method | Returns | If key is missing, empty, or unparseable |
|---|---|---|
| `getParam(k)` | String | `null` |
| `getInt(k)` | int | **`0`** — silently, including on a malformed number |
| `getLong(k)` | long | **`0`** — same |
| `getDouble(k)` | double | **`0.0`** — same |
| `getBoolean(k)` | boolean | `false` |
| `getDefine(k)` | boolean | `true` only if the key exists **and its value is non-empty** |
| `getList(k)` | List\<String\> | empty list unless `loadList(k)` ran first |

Three consequences worth knowing:

1. **Numeric getters never throw.** A typo in a number silently becomes `0`. Ports, timeouts
   and sizes fail quietly rather than loudly.
2. **`getBoolean` accepts `true`, `1`, `yes`, `on`** (case-insensitive). Anything else,
   including a missing key, is `false` — so "off" and "forgotten" are indistinguishable
   unless you also call `getDefine`.
3. **`key=` with an empty value counts as undefined.** `getDefine` returns `false` for it,
   which means optional keys fall back to their defaults.

### Lists

`loadList(k)` treats the value of `k` as a **filename** and reads that file: one entry per
line, blank lines and `#` comments skipped. Used for `whitePathList`, `blackPathList` and
`ai_assist_path_list`.

---

## `[web]` — core

| Key | Type | Description |
|---|---|---|
| `host` | String | Server domain name. Used in redirects and ACME. |
| `version` | String | Version string, printed to the log. |
| `invite` | String | Prefix of the registration invite code. |
| `www_directory` | String | Static root served over HTTPS (443). |
| `www80_directory` | String | Static root served over HTTP (80). |
| `homepage` | String | Path to the landing page, e.g. `/index.html`. Excluded from the generated page menu. |
| `https_run` | bool | Bring up port 443. |
| `test_all_services` | bool | Probe every configured service on startup. |
| `dbg_post_message_path` | String | Path used for debug POSTs. |

## `[photo]` — photo upload

| Key | Type | Description |
|---|---|---|
| `photo_upload` | bool | Enable photo intake. |
| `photo_post_message_path` | String | Path photos are posted to. |
| `path_to_save_photo` | String | Default destination directory. |
| `individual_user_photo_path_<login>` | String | Per-user destination. The key is **built dynamically** by appending the login to the prefix. |

## `[download]`

| Key | Type | Description |
|---|---|---|
| `download` | bool | Enable the downloads section. |
| `dwnld_directory` | String | Directory holding the files. The menu lists them **newest first**, sorted by mtime. |

## `[php]` — PHP-FPM

| Key | Type | Description |
|---|---|---|
| `php_fpm` | bool | Enable proxying to PHP-FPM. |
| `ip_php_fpm_server` | String | FPM address. |
| `port_php_fpm_server` | int | FPM port. |
| `php_fpm_timeout_ms` | int | *(optional)* Socket read timeout while waiting for FPM, in ms. Defaults to `30000`. Must cover the script's whole execution time: nothing arrives on the socket until the script produces output. |
| `php_directory` | String | PHP root, relative to the project. |
| `php_directory_abs` | String | The same root as an **absolute** path — FPM requires this form. |
| `php_prefix` | String | *(optional)* URL prefix for PHP. |
| `php_non_login` | bool | Allow PHP access without authentication. |

> **Dead keys:** `php_redirect`, `php_test` — present in the shipped config, **read nowhere**
> in the code.

## `[ban_response]` — response server for banned clients

| Key | Type | Description |
|---|---|---|
| `ban_response` | bool | Enable. |
| `ip_ban_response_server` | String | Address. |
| `port_ban_response_server` | int | Port. |

## `[database]`

| Key | Type | Description |
|---|---|---|
| `db_file` | String | SQLite file. |
| `db_user` | String | User. |
| `db_password` | String | **Secret.** Password. |
| `key_expiration_time` | int | Session key lifetime, in **minutes**. Validated at startup: must be **> 0**. Read once into a `static final` field (`KeyManager.java:79`), so it is not re-read at runtime. |

## `[avr]` — AVR relays

| Key | Type | Description |
|---|---|---|
| `avr` | bool | Enable. Also controls the "AVR Remote Control" menu entry. |
| `avr_port` | int | Port. |
| `avr_log` | bool | Verbose logging. |
| `avr_path` | String | Path the device posts to. |
| `avr_user_agent` | String | Expected device User-Agent. |
| `private_key_1..4` | String | **Secrets.** Device authentication keys. |

## `[LC server]` — LiraCalc

| Key | Type | Description |
|---|---|---|
| `liraCalc` | bool | Enable. Also controls the "LiraCalc ConfigEditor" menu entry. |
| `ip_liraCalc_server` | String | Address of the LiraCalc C++ server. |
| `port_liraCalc_server` | int | Its port. |
| `revers_log` | bool | Reverse-proxy logging. |

## `[ESP server]` — ESP relays

| Key | Type | Description |
|---|---|---|
| `esp` | bool | Enable. Also controls the "ESP Remote Control" menu entry. |
| `ip_relay_server` | String | Relay server address. |
| `port_relay_server` | int | Its port. |

## `[keyStore]` — TLS

| Key | Type | Description |
|---|---|---|
| `keyStoreFile` | String | JKS file. |
| `keyStorePassword` | String | **Secret.** Keystore password. |
| `keyStoreAlias` | String | Certificate alias. |

## `[acme]` — Let's Encrypt

| Key | Type | Description |
|---|---|---|
| `acme` | bool | Automatic certificate renewal. |
| `acme_server_url` | String | Production or staging ACME directory. |
| `acme_contact` | String | `mailto:` address for notifications. |
| `acme_account_key_file` | String | ACME account key. |
| `acme_domain_key_file` | String | Domain key. |
| `acme_certificate_file` | String | Issued certificate. |
| `acme_challenge_path` | String | HTTP-01 challenge path (`/.well-known/acme-challenge/`). |
| `acme_renewal_threshold_hours` | int | Renew this many hours before expiry. |

## `[LAN]`

| Key | Type | Description |
|---|---|---|
| `lanSettings` | bool | Enable local-network handling. |
| `localIP` | String | Server IP on the LAN. |
| `localMask` | String | Netmask. |

## `[logs]`

| Key | Type | Description |
|---|---|---|
| `logToConsole` | bool | Log to stdout. |
| `logToFile` | bool | Log to file. |
| `log_banresp_prnt_header` | bool | Print headers of banned requests. |
| `log_err_prnt_header` | bool | Print headers on errors. |
| `maxLogFileSize` | long | *(optional)* Rotate the log once it exceeds this many **bytes**. Without the key there is no size-based rotation. `Servak.java:65` |
| `maxLogBackupIndex` | int | *(optional)* How many rotated log files to keep. `Servak.java:67` |

## `[Cache Agent]` — in-RAM file cache

The cache is **coherent**: an entry's freshness is checked when the file is served, by
comparing mtime and size against disk. There is no background filesystem walk. The agent only
reclaims memory.

| Key | Type | Default | Description |
|---|---|---|---|
| `runCacheAgent` | bool | `true` | Run the cleanup agent. |
| `timeCacheAgent` | int | `600` | Agent period, in **seconds**. |
| `cacheIdleTime` | long | `1800` | Seconds an entry may sit unread before the agent evicts it. |
| `maxCacheSize` | long | `67108864` (64 MB) | Total cache size limit. On overflow the least-recently-requested entry is evicted. |
| `maxCacheFileSize` | long | `8388608` (8 MB) | Files larger than this are **not cached** — they are streamed from disk. Without this cap a single large download would stay resident in the heap forever. |

Manual flush: `GET /www_scripts/clear_cache` (requires an authenticated session).

## `[Firewall]`

| Key | Type | Default | Description |
|---|---|---|---|
| `FirewallRun` | bool | — | Master switch. |
| `whitePathList` | String | — | Name of the file listing allowed paths. Loaded via `loadList`. |
| `blackPathList` | String | — | Same, for denied paths. |
| `ipBanLifeTime` | long | `3600` | Seconds an IP stays banned. |
| `quantToTriger` | int | `5` | Suspicious requests before a ban is issued. |
| `countriesBan` | String | — | Comma-separated country codes, e.g. `ru,by,cn,ir`. |
| `phpLearning` | bool | — | PHP firewall learning mode. |
| `phpLearningDataFile` | String | — | File accumulating learned data. |

Manual flush of the ban list: `GET /www_scripts/clear_banlist` (requires an authenticated
session).

> **Dead key:** `banresp_log_headers` — read nowhere in the code.

## `[AI-Assistent]`

| Key | Type | Description |
|---|---|---|
| `ai_assist` | bool | Enable the assistant widget. |
| `ai_assist_api_chat` | String | Chat API path. |
| `ai_assist_path_list` | String | File listing the pages the widget appears on. |
| `ai_assist_url` | String | `host:port` of the model backend. |
| `ai_assist_url_ssl` | bool | Reach it over TLS. |
| `ai_assist_parallel_requests` | bool | Allow concurrent requests to the model. |
| `ai_assist_token` | String | **Secret.** Bearer token. |
| `ai_assist_model` | String | Model name. |
| `ai_assist_prompt` | String | System prompt, inline. |
| `ai_assist_prompt_file` | String | System prompt from a file. Takes precedence over `ai_assist_prompt`. |
| `ai_assist_authorization_header` | String | *(optional)* **Secret.** Full authorization header line appended to the request, e.g. `Authorization: Bearer …`. Alternative to `ai_assist_token`. `AiChatHandler.java:46` |

## `[UniversProxys 1-256]` — generic proxies

`Servak.main()` walks `prxy_1` … `prxy_256` and starts a thread for each enabled block. The
number is just an identifier; gaps in the numbering are fine.

| Key | Type | Description |
|---|---|---|
| `prxy_<N>` | bool | Enable this proxy. The remaining keys are read only when this is `true`. |
| `prxy_<N>_listen_port` | int | Listening port. **Must be > 2000 and not 80 or 443**, otherwise the block is ignored. Also registered in a port→proxy map at init. |
| `prxy_<N>_listen_ssl` | bool | Listen over TLS. |
| `prxy_<N>_dial_host` | String | Upstream host. |
| `prxy_<N>_dial_port` | int | Upstream port. |
| `prxy_<N>_dial_ssl` | bool | Reach upstream over TLS. |
| `prxy_<N>_authorization_header` | String | **Secret.** Header injected into the upstream request. |
| `prxy_<N>_authorization_userID` | bool | Require an authenticated user. |
| `prxy_<N>_dbg_options` | String | Space-separated log switches: `request_headers`, `request_body`, `response_headers`, `response_body`, `response_llm_thinking`, `response_llm_finally`. |

## `[machine time reverse]`

| Key | Type | Description |
|---|---|---|
| `mach_time_rev` | bool | Enable. Also controls the "MachineTime" menu entry. |
| `mach_time_ip` | String | MachineTime server address. |
| `mach_time_port` | int | Port. |
| `mach_time_path` | String | Path, e.g. `/MachineTime18Channels/`. |

Reads (`GET` / `HEAD`) through this proxy **do not require authentication**.

## `[MachineTimeForwarders 1-256]`

Each `mt_fwd_<N>` is one virtual 18-input MachineTime device. As with `prxy_`, `Servak.main()`
starts a thread per enabled block.

| Key | Type | Description |
|---|---|---|
| `mt_fwd_<N>` | bool | Enable. |
| `mt_fwd_<N>_timezone` | String | Device timezone, e.g. `Europe/Sofia`. |
| `mt_fwd_<N>_device1..18` | long | Serial numbers (`sn_mega`, unsigned dword). **An input is identified by its serial, not by its position in the list.** Unset or unmatched → the input is free and reads a permanent 0. |
| `mt_fwd_<N>_dial_host` | String | Destination host. |
| `mt_fwd_<N>_dial_port` | int | Destination port. |
| `mt_fwd_<N>_dial_ssl` | bool | Use TLS. |
| `mt_fwd_<N>_dial_path` | String | Destination path. |
| `mt_fwd_<N>_user_agent` | String | User-Agent the forwarder presents. |
| `mt_fwd_<N>_modulID` | String | Module identifier. |
| `mt_fwd_<N>_private_key_1..4` | String | **Secrets.** Authentication keys. |
| `mt_fwd_<N>_myStaticKeyRequest` | String | **Secret.** Static key sent in the request. |
| `mt_fwd_<N>_myStaticKeyResponce` | String | **Secret.** Expected static response. |

---

## Keys read by the code but absent from the shipped config

Defaults are hardcoded. Adding them is optional, but they exist.

| Key | Type | Default | Read at | Description |
|---|---|---|---|---|
| `socket_read_timeout` | int | `30000` | `ClientHandler.java:25` | Socket read timeout, in **milliseconds**. |
| `socket_last_request_timeout` | int | `60000` | `ClientHandler.java:28` | Keep-alive idle timeout before the connection is dropped, in **milliseconds**. |
