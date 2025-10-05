#!/bin/bash

cd /mnt/c/gdrive/cpp/servak_na_winapi_relays

mkdir -p source/lin_obj && \
for f in source/*.cpp; do g++ -I. -c "$f" -o "source/lin_obj/$(basename ${f%.*}).o"; done && \
for f in source/*.c; do gcc -I. -c "$f" -o "source/lin_obj/$(basename ${f%.*}).o"; done && \
g++ source/lin_obj/*.o -o server_relays.out -pthread -ldl


./server_relays.out