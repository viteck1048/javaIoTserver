# Запуск та автостарт серверів (Linux)

Тут лежить усе для підняття платформи на Linux двома способами.

| Файл | Призначення |
|------|-------------|
| `autorun_tmux.sh` | **Спосіб A — «склонував і запустив».** Одна tmux-сесія `autorun`, кожен сервер крутиться прямо у своєму вікні через `compilAndRun.sh`. systemd не потрібен. |
| `dashboard_tmux.sh` | **Спосіб B — продакшн.** Сесія `debuser`: сервери НЕ запускає (ними керує systemd), а лише тейлить `journalctl` кожного сервісу. |
| `javaSrvr.service`, `oldSrvr.service`, `relaysSrvr.service`, `machineTimeSrvr.service` | systemd-юніти для способу B. |

Усі чотири сервери компілюються «на місці» при старті — кожен `*.service` / вкладка викликає `compilAndRun.sh` у своєму каталозі, тож компіляторні залежності (`g++`, `libcurl`, `pthread`, …) мають бути в системі.

---

## Спосіб A — швидкий запуск без systemd

```bash
cd servers/strt-stp_srvrs_linux
./autorun_tmux.sh
```

Підніметься сесія `autorun` із вікнами `machineTimeSrvr / relaysSrvr / oldSrvr / javaSrvr`,
кожне саме компілює та запускає свій сервер. Підходить, щоб швидко глянути, як воно працює.

> ⚠️ Скрипт використовує абсолютні шляхи `/home/debuser/servers/...`. Якщо клонуєш в інше
> місце — поправ шляхи (або зроби симлінк `/home/debuser/servers`).

---

## Спосіб B — як налаштовано на бойовому сервері

Сервери живуть як systemd-сервіси (стартують самі на буті, рестартяться при падінні),
а на фізичній консолі автоматично відкривається tmux-дашборд із журналами.

### 1. Встановити сервіси

```bash
sudo cp javaSrvr.service oldSrvr.service relaysSrvr.service machineTimeSrvr.service \
        /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now javaSrvr oldSrvr relaysSrvr machineTimeSrvr
```

Перевірка:

```bash
systemctl --no-pager status javaSrvr oldSrvr relaysSrvr machineTimeSrvr | grep -E 'Loaded|Active'
```

Зауваги:
- `javaSrvr` та `oldSrvr` працюють від `root` (потрібні привілейовані порти / robота з сертифікатами),
  `relaysSrvr` і `machineTimeSrvr` — від `debuser`.
- `javaSrvr` стартує лише після того, як на `:8080` і `:8081` почнуть слухати back-end'и
  (див. `ExecStartPre` у `javaSrvr.service`).

### 2. Дашборд із журналами на консолі

```bash
cp dashboard_tmux.sh /home/debuser/autorun_tmux.sh
```

Викликати його при автологіні. У `/home/debuser/.bash_profile`:

```bash
/home/debuser/autorun_tmux.sh
```

### 3. Автологін на tty1

Щоб дашборд піднімався сам після ребуту — autologin користувача `debuser` на `tty1`.
`/etc/systemd/system/getty@tty1.service.d/override.conf`:

```ini
[Service]
ExecStart=
ExecStart=-/sbin/agetty --autologin debuser --noclear %I $TERM
```

(Опційно: `fbterm.service` на `tty1` дає приємніший фреймбуфер-шрифт у консолі.)

### Підсумок ланцюга автостарту

```
boot
 ├─ systemd → javaSrvr / oldSrvr / relaysSrvr / machineTimeSrvr  (самі сервери)
 └─ getty@tty1 --autologin debuser → ~/.bash_profile → autorun_tmux.sh
        └─ tmux "debuser": btop · mc · bash · journalctl -f кожного сервісу
```

> `privit_banner.sh` — персональний косметичний банер, у репо не входить; `dashboard_tmux.sh`
> запускає його тільки якщо файл існує.
