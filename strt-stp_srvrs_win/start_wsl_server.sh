#!/bin/bash
cd /mnt/c/gdrive/cpp/servak_na_linux/server
g++ KM_server.cpp my_time.cpp -o server.out -lfbclient -lpthread
sudo -E ./server.out
