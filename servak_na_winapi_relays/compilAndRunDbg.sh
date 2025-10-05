#!/bin/bash

echo "Compiling relays server for debugging..."

# Компіляція .cpp з -g
for f in source/*.cpp; do
  g++ -I. -c "$f" -g -o "source/lin_obj/$(basename "${f%.*}").o"
  if [ $? -ne 0 ]; then
    echo "Compilation failed at $f"
    exit 1
  fi
done

# Компіляція .c з -g
for f in source/*.c; do
  gcc -I. -c "$f" -g -o "source/lin_obj/$(basename "${f%.*}").o"
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

echo "Starting gdb with relays server..."
gdb ./server_relays.out
