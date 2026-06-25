#!/bin/bash

set -x
exec > /tmp/autorun_tmux.log 2>&1

SESSION="autorun"

# Створити сесію у фоні
tmux new-session -d -s $SESSION -n btop 'btop; bash'

# Додати вкладку mc
/usr/bin/tmux new-window -t $SESSION:1 -n mc 'mc; bash'

# Додати вкладку bash
/usr/bin/tmux new-window -t $SESSION:2 -n bash '/home/debuser/privit_banner.sh; bash'

# Додати вкладку machineTimeSrvr
/usr/bin/tmux new-window -t $SESSION:6 -n machineTimeSrvr 'cd /home/debuser/servers/esp8266_decoder && ./compilAndRun.sh; bash'

# Додати вкладку relaysSrvr
/usr/bin/tmux new-window -t $SESSION:7 -n relaysSrvr 'cd /home/debuser/servers/servak_na_winapi_relays && ./compilAndRun.sh; bash'

# Додати вкладку oldSrvr
/usr/bin/tmux new-window -t $SESSION:8 -n oldSrvr 'cd /home/debuser/servers/servak_na_linux/server && ./compilAndRun.sh; bash'

# Додати вкладку javaSrvr
/usr/bin/tmux new-window -t $SESSION:9 -n javaSrvr 'cd /home/debuser/servers/servak_na_linux_2_java && ./compilAndRun.sh; bash'

# Переключитися на першу вкладку (htop)
/usr/bin/tmux select-window -t $SESSION:2

# Підключитися до сесії
tmux attach-session -t $SESSION

