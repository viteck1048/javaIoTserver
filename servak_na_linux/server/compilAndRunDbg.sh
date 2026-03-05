#!/bin/bash

echo "Compiling KM_server.cpp for debugging..."
g++ KM_server.cpp my_time.cpp -o server.out -lfbclient -lpthread -g
if [ $? -ne 0 ]; then
  echo "Compilation failed!"
  exit 1
fi

echo "Starting gdb with server.out..."
sudo gdb server.out
