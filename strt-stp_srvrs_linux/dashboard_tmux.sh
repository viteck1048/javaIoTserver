#!/bin/bash
# Виробничий дашборд (як на робочому сервері).
# Сервери тут НЕ запускаються — ними керує systemd (*.service). Цей скрипт лише
# піднімає tmux-сесію з вікнами, що тейлять журнали кожного сервісу.
# Кладеться в /home/debuser/autorun_tmux.sh і викликається з ~/.bash_profile при autologin.

set -x
exec > /tmp/autorun_tmux.log 2>&1

SESSION="debuser"

# Створити сесію у фоні
tmux new-session -d -s $SESSION -n btop 'btop; bash'

# Додати вкладку mc
/usr/bin/tmux new-window -t $SESSION:1 -n mc 'mc; bash'

# Додати вкладку bash (персональний банер — опційно, якщо файл існує)
/usr/bin/tmux new-window -t $SESSION:2 -n bash '[ -x /home/debuser/privit_banner.sh ] && /home/debuser/privit_banner.sh; bash'

# Вікна-журнали сервісів (керуються systemd, тут лише перегляд логів)
/usr/bin/tmux new-window -t $SESSION:6 -n machineTimeSrvr 'journalctl -u machineTimeSrvr -n 1000 -f -a -o cat; bash'
/usr/bin/tmux new-window -t $SESSION:7 -n relaysSrvr 'journalctl -u relaysSrvr -n 1000 -f -a -o cat; bash'
/usr/bin/tmux new-window -t $SESSION:8 -n oldSrvr 'sudo journalctl -u oldSrvr -n 1000 -f -a -o cat; bash'
/usr/bin/tmux new-window -t $SESSION:9 -n javaSrvr 'sudo journalctl -u javaSrvr -n 1000 -f -a -o cat; bash'

# Переключитися на вкладку bash
/usr/bin/tmux select-window -t $SESSION:2

# Підключитися до сесії
tmux attach-session -t $SESSION
