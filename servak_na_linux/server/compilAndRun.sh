#!/bin/bash

echo "Compiling KM_server.cpp..."
g++ KM_server.cpp my_time.cpp -o server.out -lfbclient -lpthread
if [ $? -ne 0 ]; then
  echo "Compilation failed!"
  exit 1
fi

echo "Running server..."
sudo ./server.out