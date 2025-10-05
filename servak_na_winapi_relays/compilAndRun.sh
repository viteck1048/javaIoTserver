#!/bin/bash

echo "Compiling relays server..."

# Компілюємо всі .cpp
for f in source/*.cpp; do
  g++ -I. -c "$f" -o "source/lin_obj/$(basename "${f%.*}").o"
  if [ $? -ne 0 ]; then
    echo "Compilation failed at $f"
    exit 1
  fi
done

# Компілюємо всі .c
for f in source/*.c; do
  gcc -I. -c "$f" -o "source/lin_obj/$(basename "${f%.*}").o"
  if [ $? -ne 0 ]; then
    echo "Compilation failed at $f"
    exit 1
  fi
done

echo "Linking objects..."
g++ source/lin_obj/*.o -o server_relays.out -pthread -ldl
if [ $? -ne 0 ]; then
  echo "Linking failed!"
  exit 1
fi

echo "Running relays server..."
./server_relays.out
