# Пам'ятки по репозиторію `servers`

## Збірка і деплой java-сервера (`servak_na_linux_2_java`)

Перевірка змін перед розкоткою — збірка на лаптопі, деплой класів через sshfs:

1. Скопіювати `Servak.java` + `source/*.java` **без `*_old.java`** у `~/servertest` на лаптопі (там же `lib/`, `obj/`).
2. `javac --release 17 -d obj/ -classpath "lib/*" -sourcepath ".:source/" Servak.java source/*.java`
3. Скопіювати `obj/*.class` у `servak_na_linux_2_java/obj/` на сервері, **попередньо прибравши старі `*.class`** — інакше лишаються осиротілі внутрішні класи від попередніх збірок.

**`--release 17` обов'язковий.** На сервері тільки OpenJDK 17 **i386** (`/usr/lib/jvm/java-17-openjdk-i386`), на лаптопі JDK 21; без прапорця виходить major 65 і `UnsupportedClassVersionError`. Перевірка версії байткоду: `od -An -t u1 -j 6 -N 2 X.class` → 61 = Java 17, 65 = Java 21.

`compilAndRun.sh` збирає через `-sourcepath` лише те, що досяжне від `Servak.java`, — тому `*_old.java` у `source/` туди не потрапляють самі собою.

**C++ беки на лаптопі не збирати** — сервер i386, лаптоп x86_64. Їх перезбирає сам systemd-юніт (`ExecStart` викликає `compilAndRun.sh`). Локально доступний лише `g++ -fsyntax-only`, та й той для `KM_server.cpp` не пройде: немає Firebird-заголовків (`ibase.h`).

### Особливості sshfs-маунта

`/home/viktor/mnt/sshfs_debuser` — це корінь ФС сервера, не лише home.

- `git` по ньому дуже повільний: `git status` може висіти понад 2 хв, ставити таймаути в кілька хвилин;
- `/proc` крізь маунт не читається — стан процесів на сервері звідси не перевірити.

## Контракт заголовків: ява → C++ беки

Ява зберігає ключі заголовків у **нижньому регістрі** (`HTTPRequest`: `headers.put(headerName.toLowerCase(), ...)`), а `getHeaders()` віддає їх на бек як є. Тому беки порівнюють імена саме з lowercase-літералами:

- `servak_na_linux/server/KM_server.cpp` — `accept-language`, `host`, `user-agent`, `content-length`
- `servak_na_winapi_relays/source/server_relays.cpp` — ті самі чотири

До беків **ніхто не ходить напряму, весь трафік іде через яву** — тому регістронезалежне порівняння (`strcasecmp`) не потрібне, свідомо лишений простий `strcmp`.

Наслідки:

- будь-який новий заголовок, який читає бек, теж має порівнюватись у нижньому регістрі;
- у `PhpFpmHandler.toCGIHeader()` нижній регістр нешкідливий — воно саме робить `toUpperCase()`;
- кукі **не** переводяться в нижній регістр: вони парсяться окремо з `Cookie` і зберігають регістр, `X-Session-ID` шукається саме так (за RFC імена кукі регістрозалежні).

Ламається мовчки — бек просто перестає бачити заголовок. Міняючи роботу з заголовками в `HTTPRequest`, одразу перевіряти `grep 'strcmp("' ` в обох беках.

## Перезапуск сервісів на проді

Зупинку і запуск робить Віктор сам — не перезапускати сервіси й не підміняти файли під живим процесом без його команди. Порядок: підготувати збірку → сказати, що готово → він зупиняє → за командою піднімає.

Юніти в `strt-stp_srvrs_linux/` (`javaSrvr`, `oldSrvr`, `relaysSrvr`, `machineTimeSrvr`); `ExecStart` кожного викликає `compilAndRun.sh`, тобто **restart сам перекомпілює**:

```
sudo systemctl restart oldSrvr
journalctl -u oldSrvr -n 30 --no-pager
```

Альтернатива — tmux-сесія `autorun` (`strt-stp_srvrs_linux/autorun_tmux.sh`), деталі в README того каталогу.

## Відкладені задачі

Після міґрації хендлерів на новий `HTTPRequest` API (завершена 2026-07-25, коміт `91b2d3f`) лишились дві домовленості:

1. **Дописати `Configs.validate()`** — блоки перевірки за прапорцем сервісу є (`https_run`, `avr`, `liraCalc`, `esp`), але перевіряють лише ip/port. Не валідуються шляхи, на яких `Router` робить `getParam(...)` + `startsWith(...)` → NPE при відсутньому ключі: `esp_path` (під `esp`), `liraCalc_path` (під `liraCalc`), `mach_time_path` (блоку взагалі немає), `ai_assist_api_chat` (блоку немає), `homepage` (глобально).
2. **Причесати `Router`** — свідомо відкладено, «головне не поламати логіку»: надлишкові вкладені `if(httpResponse == null)`, `webRoute` що змішує повернення відповіді з мутацією `revers`, мертвий `else`-403 у `handleWWWScripts`.
