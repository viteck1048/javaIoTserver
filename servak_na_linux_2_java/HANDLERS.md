# Reverse-Proxy Handlers (Java gateway)

Довідник по хендлерах у `source/*Handler.java` — кожен обробляє свій тип реверсу,
на який головний шлюз (`ClientHandler`/`ReverseProxy`) маршрутизує запит. Усі вони —
utility-класи (приватний конструктор) зі статичними методами, що форвардять на бекенд,
адреса якого береться з `config.ini`.

> Згенеровано в співпраці: чорнову прохідку зробила локальна LLM (qwen2.5-coder),
> звірено й доповнено вручну по факту коду 2026-06-18.

---

## AiChatHandler — `AI_CHAT`
Проксі до зовнішнього **OpenAI-сумісного** LLM-сервісу (`/v1/chat/completions`, через SSL).
- **Бекенд:** `ai_assist_url` (host:port), опц. `ai_assist_token`/`ai_assist_autorization_header`, модель `ai_assist_model`.
- **Single-flight guard:** якщо `ai_assist_paralel_requests=false`, тримає `AtomicBoolean aiChatBusy` — другий одночасний запит одразу отримує `503`.
- Збирає system-prompt (дефолтний або `ai_assist_prompt`) + врахування `Accept-Language` та імені користувача; user-content пакує в теги `<page>/<chat_history>/<user_message>`.
- Таймаут 5 хв (`setSoTimeout(300000)`). Відповідь LLM парситься вручну (`extractJsonContent`), повертається як `text/html`.
- **Public:** `aiChatResend(HTTPRequest)` → `HTTPResponse`.

## PhpFpmHandler — `PHP_FPM`
Повноцінний **клієнт бінарного протоколу FastCGI** до php-fpm (не просто форвард!).
- **Бекенд:** `ip_php_fpm_server` / `port_php_fpm_server`.
- Сам формує FastCGI-записи: `FCGI_BEGIN_REQUEST` → `FCGI_PARAMS` (з коректним кодуванням довжин ≥128 у 4 байти + padding до 8) → `FCGI_STDIN` (тіло ріжеться на чанки по 65535) .
- Читає відповідь по 8-байтових заголовках, збирає `FCGI_STDOUT`, логує `FCGI_STDERR`, завершує на `FCGI_END_REQUEST`.
- **Public:** `phpFpmResend(HTTPRequest)` → `HTTPResponse`. (приват. `sendPHPFPMRequest(...)` — збірка/відправка одного FastCGI-запису).

## UniProxyHendler — `UNI_PRXY`  *(найскладніший)*
Універсальний реверс-проксі з пер-портовою конфігурацією та підтримкою стрімінгу LLM.
- **Конфіг за портом:** `Configs.getKeyForUniPrxyPort(port)` → `prxyKey`, далі `<prxyKey>_dial_host/_dial_port/_dial_ssl`.
- **Авторизація:** опційна перевірка `<prxyKey>_authorization_header` (інакше `401`); опційна авторизація за `userID` (`_authorization_userID`) з ендпоінтом `authorization=check` та маршрутом `reestr` → `RegistrUsers.reestr`.
- **Дебаг:** гнучкі прапорці `<prxyKey>_dbg_options` (`request_headers`, `request_body`, `response_headers`, `response_body`, `response_llm_thinking`, `response_llm_finally`).
- **Стрімінг:** при `Transfer-Encoding: chunked` проксіює чанки клієнту на льоту, паралельно парсячи LLM-поля `thinking`/`reasoning`/`content` (підтримує і OpenAI, і Ollama формати). Інакше — читає за `Content-Length`.
- **Public:** `uniPrxyResend(HTTPRequest)` → `HTTPResponse`. (приват. `formatJson`, `getContent`, `addIndent`).

## RelaysServerHandler — `RELAYS_SERVER`
Форвард на сервер реле з ін'єкцією `userID`.
- **Бекенд:** `ip_relay_server` / `port_relay_server`.
- GET на `/relay_servak/` — додає `userID` у query першого рядка заголовка; POST/PUT/DELETE з `application/x-www-form-urlencoded` — вставляє `userID` у тіло й перераховує `Content-Length`.
- **Public:** `relaysServerResend(HTTPRequest)` → `HTTPResponse`.
- ⚠️ Дрібниця: повідомлення відповіді — `"revers to old server"` (копіпаст-залишок, варто поправити на relay).

## OldServakHandler — `OLD_SERVAK`
Простий форвард (header+body, без SSL) на **перший повноцінний C++ сервер** проєкту —
той, що лежить у `servak_na_linux/` (C++ з Firebird, етап 2 еволюції). Цей сервер
**і є LiraCalc-сервер**, тому конфіг-ключі названі коректно.
- **Бекенд:** `ip_liraCalc_server` / `port_liraCalc_server` (= старий C++ сервер `servak_na_linux/`, він же LiraCalc).
- **Public:** `oldServakResend(HTTPRequest)` → `HTTPResponse`.

## BanResponseHandler — `BANRESPONSE`
Форвард **тільки заголовків** (тіло обнуляється, `Content-Length: 0`) на бан-сервер.
- **Бекенд:** `ip_ban_response_server` / `port_ban_response_server`.
- **Public:** `banResponse(HTTPRequest)` → `HTTPResponse`.

---

### Спільні патерни
- Усі повертають `503`, якщо не вдалось підключитись до бекенду (`NetworkClient` кидає `IOException`).
- Усі використовують `NetworkClient` для send/recv (chunked + Content-Length вміє він сам).
- Бекенди й ключі авторизації — виключно з `config.ini` (тримається поза git).
