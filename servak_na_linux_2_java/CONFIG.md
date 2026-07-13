# Конфігурація Java-сервера

Довідка по всіх ключах `confMijServak.ini` / `confRadM.ini`.

Самі `.ini` **не версіонуються** (`.gitignore`) — у них лежать паролі, токени й приватні
ключі. Тому тут скрізь плейсхолдери, а не реальні значення.

---

## Запуск і вибір файлу конфігу

```
java -cp ".:obj:lib/*" Servak [-c <файл>.ini] [-p ключ=значення ...]
```

| | |
|---|---|
| **Дефолт** | `config.ini` — саме це ім'я зашите в `Servak.java:21`, якщо `-c` не передали. |
| **`-c <файл>.ini`** | Явно вказати файл. |
| **`-p ключ=значення ...`** | Перевизначити ключі конфігу з командного рядка. |

`confMijServak.ini` і `confRadM.ini` — це **не** щось особливе для коду. Це просто імена,
які обрано в `compilAndRun.sh`:

```bash
exec java -cp ".:obj:lib/*" Servak -c confMijServak.ini
```

У проді той самий скрипт запускає systemd (`strt-stp_srvrs_linux/javaSrvr.service` →
`ExecStart=/bin/bash ./compilAndRun.sh`). Щоб перемкнути конфіг — правиться `compilAndRun.sh`,
а не код.

### Валідація імені файлу — неочевидна

Ім'я після `-c` розбивається по крапках і приймається **лише якщо частин рівно дві, а друга
— `ini`**. Тобто:

| Ім'я | Результат |
|---|---|
| `confMijServak.ini` | ✅ |
| `config.ini` | ✅ |
| `my.conf.ini` | ❌ три частини — відкидається, мовчки береться дефолт `config.ini` |
| `config` | ❌ немає розширення |

При відкиданні друкується `Invalid config file name: …`, але сервер **не падає** — він
просто тихо стартує з `config.ini`. Якщо такого файлу немає, помилка вилізе значно пізніше
й виглядатиме зовсім не пов'язаною.

### `-p` — перевизначення з командного рядка

```
java ... Servak -c confMijServak.ini -p https_run=false avr=false
```

`-p` з'їдає всі наступні аргументи, доки не натрапить на такий, що **не містить `=`**.
Значення кладуться в ту саму мапу параметрів уже після читання файлу, тож **перебивають
його**. Зручно для разових запусків без правки конфігу.

---

## Що перевіряється на старті

`Configs.validate()` викликається одразу після читання файлу і `-p`. Якщо перевірка не
проходить — сервер друкує `Missing param: <ключ>` та `Invalid configs` і **виходить, не
запустившись**.

**Потрібні завжди:**
`invite`, `host`, `www_directory`, `www80_directory`, `dbg_post_message_path`,
`db_file`, `db_user`, `db_password`, `keyStoreFile`, `keyStorePassword`.

**Умовно потрібні** — лише якщо відповідний сервіс увімкнено:

| Якщо | То обов'язкові |
|---|---|
| `https_run=true` | `keyStoreAlias`, `acme_server_url`, `acme_contact`, `acme_account_key_file`, `acme_domain_key_file`, `acme_certificate_file`, `acme_challenge_path` |
| `avr=true` | `avr_port` (≠ 0), `avr_path`, `avr_user_agent` |
| `liraCalc=true` | `port_liraCalc_server` (≠ 0), `ip_liraCalc_server` |
| `esp=true` | `port_relay_server` (≠ 0), `ip_relay_server` |

Усе інше `validate()` **не перевіряє** — відсутність такого ключа виявиться вже в рантаймі.

---

## Як влаштований парсер

`Configs.init()` читає файл рядок за рядком, формат `ключ=значення`.

| Метод | Повертає | Поведінка, якщо ключа немає |
|---|---|---|
| `getParam(k)` | String | `null` |
| `getInt(k)` / `getLong(k)` / `getDouble(k)` | число | падає або 0 — **не покладатись** |
| `getBoolean(k)` | boolean | `false` |
| `getDefine(k)` | boolean | `true`, якщо ключ **присутній** у файлі |
| `getList(k)` | List\<String\> | вимагає попереднього `loadList(k)` |

Звідси два типи ключів:

* **обов'язкові** — код читає їх напряму (`Configs.getInt("key_expiration_time")`).
  Немає ключа → сервер поводиться непередбачувано;
* **необов'язкові** — код спершу перевіряє `getDefine(k)` і має власний дефолт.
  У таблицях нижче такі позначені колонкою «дефолт».

### Дві пастки парсингу

1. **Коментар — тільки окремим рядком.** `#` у кінці рядка зі значенням не обрізається,
   а стає частиною значення й ламає розбір числа. Тобто `port=8080 # основний` — помилка.
2. **`getBoolean` = `false` і за відсутності ключа, і за `=false`.** Розрізнити «вимкнено»
   і «забули» можна лише через `getDefine`.

---

## `[web]` — базове

| Ключ | Тип | Опис |
|---|---|---|
| `host` | String | Доменне ім'я сервера. Використовується в редиректах і ACME. |
| `version` | String | Версія, показується в логах. |
| `invite` | String | Префікс інвайт-коду для реєстрації. |
| `www_directory` | String | Корінь статики HTTPS (443). |
| `www80_directory` | String | Корінь статики HTTP (80). |
| `homepage` | String | Шлях до головної, напр. `/index.html`. Виключається зі списку сторінок меню. |
| `https_run` | bool | Піднімати 443. |
| `test_all_services` | bool | Перевіряти доступність усіх сервісів на старті. |
| `dbg_post_message_path` | String | Шлях для налагоджувальних POST. |

## `[photo]` — завантаження фото

| Ключ | Тип | Опис |
|---|---|---|
| `photo_upload` | bool | Увімкнути приймання фото. |
| `photo_post_message_path` | String | Шлях, на який шлють фото. |
| `path_to_save_photo` | String | Куди складати за замовчуванням. |
| `individual_user_photo_path_<логін>` | String | Персональна тека для конкретного користувача. Ключ **динамічний**: код склеює префікс із логіном. |

## `[download]`

| Ключ | Тип | Опис |
|---|---|---|
| `download` | bool | Увімкнути розділ завантажень. |
| `dwnld_directory` | String | Тека з файлами. Список у меню сортується за датою зміни, свіжіші вгорі. |

## `[php]` — PHP-FPM

| Ключ | Тип | Опис |
|---|---|---|
| `php_fpm` | bool | Увімкнути проксіювання в PHP-FPM. |
| `ip_php_fpm_server` | String | Адреса FPM. |
| `port_php_fpm_server` | int | Порт FPM. |
| `php_directory` | String | Корінь PHP відносно проєкту. |
| `php_directory_abs` | String | Той самий корінь **абсолютним** шляхом — FPM потребує саме його. |
| `php_prefix` | String | *(необов'язковий)* URL-префікс для PHP. |
| `php_non_login` | bool | Пускати в PHP без авторизації. |

> **Мертві ключі:** `php_redirect`, `php_test` — присутні у файлі, код їх **не читає ніде**.

## `[ban_response]` — сервер відповідей забаненим

| Ключ | Тип | Опис |
|---|---|---|
| `ban_response` | bool | Увімкнути. |
| `ip_ban_response_server` | String | Адреса. |
| `port_ban_response_server` | int | Порт. |

## `[database]`

| Ключ | Тип | Опис |
|---|---|---|
| `db_file` | String | Файл SQLite. |
| `db_user` | String | Користувач. |
| `db_password` | String | **Секрет.** Пароль. |
| `key_expiration_time` | int | Час життя сесійного ключа, **хвилини**. **Обов'язковий** — читається без `getDefine`. |

## `[avr]` — реле на AVR

| Ключ | Тип | Опис |
|---|---|---|
| `avr` | bool | Увімкнути. Керує і пунктом меню «AVR Remote Control». |
| `avr_port` | int | Порт. |
| `avr_log` | bool | Докладний лог. |
| `avr_path` | String | Шлях, на який стукає пристрій. |
| `avr_user_agent` | String | Очікуваний User-Agent пристрою. |
| `private_key_1..4` | String | **Секрети.** Ключі автентифікації пристрою. |

## `[LC server]` — LiraCalc

| Ключ | Тип | Опис |
|---|---|---|
| `liraCalc` | bool | Увімкнути. Керує пунктом меню «LiraCalc ConfigEditor». |
| `ip_liraCalc_server` | String | Адреса C++-сервера LiraCalc. |
| `port_liraCalc_server` | int | Його порт. |
| `revers_log` | bool | Лог реверс-проксі. |

## `[ESP server]` — реле на ESP

| Ключ | Тип | Опис |
|---|---|---|
| `esp` | bool | Увімкнути. Керує пунктом меню «ESP Remote Control». |
| `ip_relay_server` | String | Адреса релейного сервера. |
| `port_relay_server` | int | Його порт. |

## `[keyStore]` — TLS

| Ключ | Тип | Опис |
|---|---|---|
| `keyStoreFile` | String | Файл JKS. |
| `keyStorePassword` | String | **Секрет.** Пароль сховища. |
| `keyStoreAlias` | String | Аліас сертифіката. |

## `[acme]` — Let's Encrypt

| Ключ | Тип | Опис |
|---|---|---|
| `acme` | bool | Автоматичне поновлення сертифіката. |
| `acme_server_url` | String | Бойовий або staging-каталог ACME. |
| `acme_contact` | String | `mailto:` для сповіщень. |
| `acme_account_key_file` | String | Ключ акаунта ACME. |
| `acme_domain_key_file` | String | Ключ домену. |
| `acme_certificate_file` | String | Готовий сертифікат. |
| `acme_challenge_path` | String | Шлях для HTTP-01 (`/.well-known/acme-challenge/`). |
| `acme_renewal_threshold_hours` | int | За скільки годин до кінця поновлювати. |

## `[LAN]`

| Ключ | Тип | Опис |
|---|---|---|
| `lanSettings` | bool | Увімкнути логіку локальної мережі. |
| `localIP` | String | IP сервера в LAN. |
| `localMask` | String | Маска. |

## `[logs]`

| Ключ | Тип | Опис |
|---|---|---|
| Ключ | Тип | Опис |
|---|---|---|
| `logToConsole` | bool | Лог у stdout. |
| `logToFile` | bool | Лог у файл. |
| `log_banresp_prnt_header` | bool | Друкувати заголовки забанених запитів. |
| `log_err_prnt_header` | bool | Друкувати заголовки при помилках. |
| `maxLogFileSize` | long | *(необов'язковий)* Розмір лог-файлу, після якого йде ротація, **байти**. Без ключа — ротації за розміром немає. `Servak.java:65` |
| `maxLogBackupIndex` | int | *(необов'язковий)* Скільки старих лог-файлів тримати. `Servak.java:67` |

## `[Cache Agent]` — кеш файлів у RAM

Кеш **когерентний**: актуальність перевіряється в момент віддачі файлу (звірка mtime+size),
а не фоновим обходом ФС. Агент лише повертає пам'ять.

| Ключ | Тип | Дефолт | Опис |
|---|---|---|---|
| `runCacheAgent` | bool | `true` | Запускати агента прибирання. |
| `timeCacheAgent` | int | `600` | Період роботи агента, **секунди**. |
| `cacheIdleTime` | long | `1800` | Скільки секунд запис живе без звернень, поки його не витіснить агент. |
| `maxCacheSize` | long | `67108864` (64 МБ) | Ліміт сумарного обсягу кешу. За перевищення витісняється найдавніше запитуваний. |
| `maxCacheFileSize` | long | `8388608` (8 МБ) | Файли, більші за це, **не кешуються** — віддаються з диска повз кеш. Без цього один великий `.apk` із `download/` назавжди осідав би в heap. |

Ручне скидання: `GET /www_scripts/clear_cache` (за авторизацією).

## `[Firewall]`

| Ключ | Тип | Дефолт | Опис |
|---|---|---|---|
| `FirewallRun` | bool | — | Головний вимикач. |
| `whitePathList` | String | — | Ім'я файлу зі списком дозволених шляхів. Завантажується через `loadList`. |
| `blackPathList` | String | — | Те саме для заборонених. |
| `ipBanLifeTime` | long | `3600` | Скільки секунд IP лишається в бані. |
| `quantToTriger` | int | `5` | Скільки підозрілих запитів до бану. |
| `countriesBan` | String | — | Коди країн через кому, напр. `ru,by,cn,ir`. |
| `phpLearning` | bool | — | Режим навчання PHP-фаєрвола. |
| `phpLearningDataFile` | String | — | Файл накопичених даних. |

Ручне скидання бан-списку: `GET /www_scripts/clear_banlist` (за авторизацією).

> **Мертвий ключ:** `banresp_log_headers` — код його не читає.

## `[AI-Assistent]`

| Ключ | Тип | Опис |
|---|---|---|
| `ai_assist` | bool | Увімкнути віджет помічника. |
| `ai_assist_api_chat` | String | Шлях API чату. |
| `ai_assist_path_list` | String | Файл зі списком сторінок, де показувати віджет. |
| `ai_assist_url` | String | `хост:порт` бекенда моделі. |
| `ai_assist_url_ssl` | bool | Чи ходити до нього по TLS. |
| `ai_assist_parallel_requests` | bool | Дозволити паралельні запити до моделі. |
| `ai_assist_token` | String | **Секрет.** Bearer-токен. |
| `ai_assist_model` | String | Ім'я моделі. |
| `ai_assist_prompt` | String | Системний промпт текстом. |
| `ai_assist_prompt_file` | String | Або файл із промптом (`res/AI_SUPPORT.md`). Має пріоритет. |
| `ai_assist_autorization_header` | String | *(необов'язковий)* **Секрет.** Повний рядок заголовка авторизації, що дописується до запиту (напр. `Authorization: Bearer …`). Альтернатива до `ai_assist_token`. `AiChatHandler.java:46` |

> **Дві пастки в написанні ключів цієї секції.**
>
> 1. `ai_assist_autorization_header` — саме так, через **`autorization`**, без `h`.
>    Одрук у самому коді; писати «правильно» = ключ не спрацює.
> 2. `ai_assist_parallel_requests` — а тут навпаки, дві `l`. Раніше код читав
>    `paralel` з однією, і ключ ніколи не збігався з файлом, тобто налаштування просто
>    не діяло. Виправлено, тепер обидві сторони пишуть `parallel`.

## `[UniversProxys 1-256]` — універсальні проксі

`Servak.main()` перебирає `prxy_1` … `prxy_256` і піднімає окремий потік на кожен
увімкнений блок. Номер — просто ідентифікатор, дірки в нумерації дозволені.

| Ключ | Тип | Опис |
|---|---|---|
| `prxy_<N>` | bool | Увімкнути цей проксі. Решта ключів читається лише якщо тут `true`. |
| `prxy_<N>_listen_port` | int | Порт прослуховування. **Має бути > 2000 і не 80/443** — інакше блок ігнорується. |
| `prxy_<N>_listen_ssl` | bool | Слухати по TLS. |
| `prxy_<N>_dial_host` | String | Куди проксіювати. |
| `prxy_<N>_dial_port` | int | Порт призначення. |
| `prxy_<N>_dial_ssl` | bool | Чи ходити до нього по TLS. |
| `prxy_<N>_authorization_header` | String | **Секрет.** Заголовок, що додається до запиту вгору. |
| `prxy_<N>_authorization_userID` | bool | Вимагати авторизованого користувача. |
| `prxy_<N>_dbg_options` | String | Що логувати, через пробіл: `request_headers`, `request_body`, `response_headers`, `response_body`, `response_llm_thinking`, `response_llm_finally`. |

## `[machine time reverse]`

| Ключ | Тип | Опис |
|---|---|---|
| `mach_time_rev` | bool | Увімкнути. Керує пунктом меню «MachineTime». |
| `mach_time_ip` | String | Адреса сервера MachineTime. |
| `mach_time_port` | int | Порт. |
| `mach_time_path` | String | Шлях, напр. `/MachineTime18Channels/`. |

Читання (`GET`/`HEAD`) через цей проксі **не вимагає авторизації**.

## `[MachineTimeForwarders 1-256]`

Кожен `mt_fwd_<N>` — один віртуальний 18-входовий пристрій MachineTime. Як і з `prxy_`,
`Servak.main()` пускає окремий потік на кожен увімкнений блок.

| Ключ | Тип | Опис |
|---|---|---|
| `mt_fwd_<N>` | bool | Увімкнути. |
| `mt_fwd_<N>_timezone` | String | Часовий пояс пристрою, напр. `Europe/Sofia`. |
| `mt_fwd_<N>_device1..18` | long | Серійники (`sn_mega`, беззнаковий dword). **Вхід визначає саме серійник, а не позиція в списку.** Незаданий або не знайдений → вхід вільний, перманентний 0. |
| `mt_fwd_<N>_dial_host` | String | Куди слати. |
| `mt_fwd_<N>_dial_port` | int | Порт. |
| `mt_fwd_<N>_dial_ssl` | bool | TLS. |
| `mt_fwd_<N>_dial_path` | String | Шлях. |
| `mt_fwd_<N>_user_agent` | String | User-Agent, яким прикидається форвардер. |
| `mt_fwd_<N>_modulID` | String | Ідентифікатор модуля. |
| `mt_fwd_<N>_private_key_1..4` | String | **Секрети.** Ключі автентифікації. |
| `mt_fwd_<N>_myStaticKeyRequest` | String | **Секрет.** Статичний ключ у запиті. |
| `mt_fwd_<N>_myStaticKeyResponce` | String | **Секрет.** Очікувана статична відповідь. |

---

## Ключі, яких у `.ini` немає

Код їх читає, дефолти зашиті. Додавати не обов'язково — але знати варто.

| Ключ | Тип | Дефолт | Де читається | Опис |
|---|---|---|---|---|
| `socket_read_timeout` | int | `30000` | `ClientHandler.java:25` | Таймаут читання з сокета, **мілісекунди**. |
| `socket_last_request_timeout` | int | `60000` | `ClientHandler.java:28` | Таймаут очікування наступного запиту в keep-alive, **мілісекунди**. |

---

## Зведення проблем

| Що | Ключі |
|---|---|
| **Мертві** — є у файлі, код не читає ніде | `banresp_log_headers`, `php_redirect`, `php_test` |
| **Недокументовані** — код читає, у файлі немає | `socket_read_timeout`, `socket_last_request_timeout` |
| **Одруки в назвах** — писати саме так, як у коді | `ai_assist_autorization_header` (без `h`), `ai_assist_parallel_requests` (дві `l`) |

### `key_expiration_time` — тиха діра

Читається як `Configs.getInt("key_expiration_time")` (`KeyManager.java:79`) **без** захисту
`getDefine`. І `validate()` його **не перевіряє**. Тобто якщо ключ зникне з конфігу, сервер
спокійно стартує — а час життя сесійних ключів мовчки стане нулем або впаде вже в рантаймі.

Це єдиний числовий ключ у всьому конфізі, який не прикритий ані `getDefine` з дефолтом, ані
`validate()`. Варто або додати його до списку обов'язкових у `Configs.validate()`, або дати
йому дефолт.
