# Reverse-Proxy Handlers (Java gateway)

Reference for the handlers in `source/*Handler.java`. `ClientHandler` hands the parsed
`HTTPRequest` to `Router` (`Router.route`), which does the actual dispatch decision — path/port
matching, `allPortsRoute`/`webRoute`/`specialPortRoute` — and either answers directly or sets
`httpRequest.revers` to one of the reverse types below. `ReverseProxy.handleReverseRequest`
then does the final step: it switches on `revers` and calls the matching handler. All handlers
are utility classes (private constructor) with static methods that forward to a backend whose
address comes from the config file.

The AVR gadget protocol (base16/base64 relay wire format) is a separate, non-reverse branch —
`Router.specialPortRoute` calls `DBClass.requestFromGadget` directly on the AVR port, it never
goes through `ReverseProxy`. See `DBClass.java` / `AvrRele.java`, not covered here.

Config keys referenced below are documented in [CONFIG.md](CONFIG.md).

> Drafted with a local LLM (qwen2.5-coder), then checked and completed by hand against the
> code on 2026-06-18. Updated 2026-07-13 and 2026-07-26.

---

## AiChatHandler — `AI_CHAT`
Proxy to an external **OpenAI-compatible** LLM service (`/v1/chat/completions`, over SSL).
- **Backend:** `ai_assist_url` (host:port), optionally `ai_assist_token` /
  `ai_assist_authorization_header`, model from `ai_assist_model`.
- **Single-flight guard:** when `ai_assist_parallel_requests=false`, holds an
  `AtomicBoolean aiChatBusy` — a second concurrent request immediately gets `503`.
- Builds the system prompt (default or `ai_assist_prompt` / `ai_assist_prompt_file`), taking
  `Accept-Language` and the user's name into account; wraps user content in
  `<page>` / `<chat_history>` / `<user_message>` tags.
- Timeout 5 min (`setSoTimeout(300000)`). The LLM reply is parsed by hand
  (`extractJsonContent`) and returned as `text/html`.
- **Public:** `aiChatResend(HTTPRequest)` → `HTTPResponse`.

## PhpFpmHandler — `PHP_FPM`
A full **binary FastCGI protocol client** for php-fpm — not a forward at all.

Every other handler passes HTTP through: HTTP goes in, HTTP comes out, and you can dump the
bytes and read them. This one **destroys the request and rebuilds it in a foreign
representation**, then reconstructs an HTTP response out of something that was never HTTP.
That work is spread across two places.

**1. `sendCgiParams` — builds the CGI environment inside the handler.**
The reverse type is decided in `Router.webRoute` by a path match on `*.php` (also
`.php3/.php4/.php5/.phtml`). `PhpFpmHandler` itself derives `SCRIPT_NAME`/`PATH_INFO` from
`httpRequest.path` (split on the first `.php`) and sends each fixed CGI variable directly via
`sendParam`:

```
GATEWAY_INTERFACE  SERVER_SOFTWARE  SERVER_PROTOCOL  SERVER_PORT      SERVER_NAME
REQUEST_METHOD     DOCUMENT_ROOT    SCRIPT_FILENAME  SCRIPT_NAME      REQUEST_URI
QUERY_STRING        REMOTE_ADDR      REMOTE_PORT      SERVER_ADDR      HTTPS (if port 443)
REQUEST_SCHEME     PATH_INFO (if present)
```

On `POST`/`PUT`/`DELETE` with a non-empty body and a `content-type` header, it also sends
`CONTENT_LENGTH` and `CONTENT_TYPE` **unprefixed**, as CGI/RFC 3875 requires — this is what
php-fpm's SAPI uses to decide whether and how to parse the body into `$_POST`; without it the
script still runs and returns `200`, but the body is never parsed.

Then it loops `httpRequest.headers` (already lower-cased by the parser) and sends every one of
them translated through `toCGIHeader` (`"HTTP_" + upper-case + "-"/" " → "_"`) — this includes
`HTTP_CONTENT_TYPE`/`HTTP_CONTENT_LENGTH` too, alongside the unprefixed pair above.

Also here: the authorization gate. `php_non_login=true` skips it entirely; otherwise the same
`autorizUser` check as the other handlers (see "Authorization" below) runs first and returns
`401` on failure.

**2. The handler — FastCGI framing.**
- **Backend:** `ip_php_fpm_server` / `port_php_fpm_server`.
- Builds the records itself: `FCGI_BEGIN_REQUEST` → `FCGI_PARAMS` (name/value lengths ≥ 128
  encoded as 4 bytes, each record padded to a multiple of 8) → `FCGI_STDIN` (body split into
  65535-byte chunks).
- Reads the reply through 8-byte record headers, assembles `FCGI_STDOUT`, logs `FCGI_STDERR`,
  stops on `FCGI_END_REQUEST`.
- **Public:** `phpFpmResend(HTTPRequest)` → `HTTPResponse`.
  (private `sendCgiParams(...)`, `sendParam(...)` — build and send the records).

**3. Post-processing — `HTTPResponse.normalizeHeaders(HTTPRequest)`.**
What comes back is **CGI output, not an HTTP response**: no status line, and the headers are
whatever the script chose to print. So `normalizeHeaders` scans the raw byte body for the
first `\r\n\r\n`, splits it there, and rebuilds a real HTTP response around it. Called from
`ClientHandler:185` (and by `UniProxyHendler:153` for the same "raw bytes in, HTTP out"
reason).

> **Nothing here is optional.** Get the padding wrong and php-fpm hangs silently; get the
> length encoding wrong for a 130-byte header and the payload is quietly corrupted. Compare
> with `UNI_PRXY`, where most of the branching is opt-in behaviour that can simply be turned
> off.

## UniProxyHendler — `UNI_PRXY`  *(the most complex one)*
Generic reverse proxy with per-port configuration and LLM streaming support.
- **Per-port config:** `Configs.getKeyForUniPrxyPort(port)` → `prxyKey`, then
  `<prxyKey>_dial_host` / `_dial_port` / `_dial_ssl`.
- **Authorization:** optional check of `<prxyKey>_authorization_header` (otherwise `401`);
  optional `userID` authorization (`<prxyKey>_authorization_userID`) with an
  `authorization=check` endpoint and a `reestr` route → `RegistrUsers.reestr`.
- **Debug:** flexible flags in `<prxyKey>_dbg_options` (`request_headers`, `request_body`,
  `response_headers`, `response_body`, `response_llm_thinking`, `response_llm_finally`).
- **Streaming:** on `Transfer-Encoding: chunked`, relays chunks to the client on the fly while
  parsing LLM fields `thinking` / `reasoning` / `content` (handles both OpenAI and Ollama
  shapes). Otherwise reads by `Content-Length`.
- **Public:** `uniPrxyResend(HTTPRequest)` → `HTTPResponse`.
  (private `formatJson`, `getContent`, `addIndent`).

## RelaysServerHandler — `RELAYS_SERVER`
Forwards to the relay server, injecting `userID`.
- **Backend:** `ip_relay_server` / `port_relay_server`.
- `GET` on `/relay_servak/` — appends `userID` to the query string of the request line.
  `POST` / `PUT` / `DELETE` with `application/x-www-form-urlencoded` — injects `userID` into
  the body and recomputes `Content-Length`.
- **Public:** `relaysServerResend(HTTPRequest)` → `HTTPResponse`.
- ⚠️ Nit: the response message still reads `"revers to old server"` — a copy-paste leftover,
  worth changing to `relay`.

## MachineTimeProxyHandler — `MACHINE_TIME`
Forwards to the ESP8266 decoder that serves MachineTime. Plain forward, **no SSL**
(`new NetworkClient(host, port, false)`).
- **Backend:** `mach_time_ip` / `mach_time_port`.
- The path is **not stripped** — the target server handles `/MachineTime18Channels/` itself.
- **Injects `userID`**, by the same pattern as `RelaysServerHandler`:
  - `GET` — appends `userID=<n>` to the query string of the request line, choosing `?` or `&`
    depending on whether a query is already present;
  - `POST` / `PUT` / `DELETE` with `application/x-www-form-urlencoded` — injects
    `&userID=<n>` into the body, recomputes `contentLength` and patches the `Content-Length`
    header.
- **Reads do not require authentication.** The handler itself exempts `GET`/`HEAD`
  (`machineTimeRead`) from the authorization check. Consequence: an anonymous visitor still
  reaches the handler, and the injected value is **`userID=0`** — the backend must treat 0 as
  "anonymous". Every other method still requires a session.
- **Public:** `machineTimeResend(HTTPRequest)` → `HTTPResponse`.

## OldServakHandler — `OLD_SERVAK`
Plain forward (header + body, no SSL) to the project's **first full C++ server** — the one in
`servak_na_linux/` (C++ with Firebird, stage 2 of the evolution). That server **is** the
LiraCalc server, so the config key names are accurate.
- **Backend:** `ip_liraCalc_server` / `port_liraCalc_server` (= the old C++ server in
  `servak_na_linux/`, a.k.a. LiraCalc).
- **Public:** `oldServakResend(HTTPRequest)` → `HTTPResponse`.

## BanResponseHandler — `BANRESPONSE`
Forwards **headers only** (body zeroed, `Content-Length: 0`) to the ban server.
- **Backend:** `ip_ban_response_server` / `port_ban_response_server`.
- **Public:** `banResponse(HTTPRequest)` → `HTTPResponse`.

---

### NetworkClient — the transport every handler stands on

All eight handlers and forwarders talk to their backend through `NetworkClient`. It is the
reason the handlers stay as short as they are, and the reason so much of their behaviour is a
config switch rather than a code path.

- **TLS is a constructor boolean.** `new NetworkClient(host, port, useSSL)` — plain socket or
  `SSLSocketFactory`, decided at runtime. That single argument is what turns `_dial_ssl`,
  `ai_assist_url_ssl` and friends into config keys instead of duplicated code in every
  handler.
- **Response framing is handled for you.** `recvAll()` reads the headers, and if it finds
  `Transfer-Encoding: chunked` it de-chunks the body **and strips the header**, so the
  handler above simply receives a plain body. Otherwise it honours `Content-Length`.
  No handler contains chunk-parsing code.
- **Streaming out** is available where it is needed: `sendChunk()` / `sendFlush()` let
  `UniProxyHendler` relay an LLM response to the client as it arrives.
- **Socket tuning** in one place: `setSoTimeout`, `setKeepAlive`, `setTcpNoDelay`,
  send/receive buffer sizes. `AiChatHandler`'s 5-minute timeout is just a call here.

### Shared patterns
- All return `503` when the backend cannot be reached (`NetworkClient` throws `IOException`).
- Backends and authorization keys come exclusively from the config file, which is kept out of
  git. See [CONFIG.md](CONFIG.md).

### Authorization

There is no single gate. Each handler checks for itself, at its own entry point, using the same
predicate:

```java
boolean autorizUser = httpRequest.userID != 0 && httpRequest.isHttps;
```

A session id alone is not enough — the connection must also actually be (or convincingly
pretend to be) TLS. `isHttps` is not derived from the port number; it is whatever value
`ClientHandler` was constructed with. `SimpleHTTPSServer` (the real `SSLServerSocket` listener)
always constructs it with `true`; `ServerTask` (every plain-socket port — 80, the AVR port,
plain `prxy_` ports) constructs it with `Configs.getBoolean("authoriz_whithout_https")`, which
is `false` unless explicitly set. Setting that one key `true` makes every plain-socket port
satisfy the `isHttps` half of the check for every handler, without editing any handler — see
[CONFIG.md](CONFIG.md#web--core). The real per-user session lookup via `KeyManager` still runs
and still has to pass; only the transport half of the predicate is affected.

Per-handler policy:

| Handler | Check |
|---|---|
| `UniProxyHendler` | Per-proxy, gated by `<prxyKey>_authorization_userID`; when enabled, requires `autorizUser`. |
| `PhpFpmHandler` | `autorizUser`, unless `php_non_login=true` (skips the check entirely). |
| `BanResponseHandler` | None, by design. |
| `OldServakHandler` | `autorizUser`, `401` on failure. |
| `RelaysServerHandler` | `autorizUser`, `401` on failure. |
| `AiChatHandler` | `autorizUser`, `401` on failure. |
| `MachineTimeProxyHandler` | Exempt on `GET`/`HEAD` (public reads); `autorizUser` on every other method. |
