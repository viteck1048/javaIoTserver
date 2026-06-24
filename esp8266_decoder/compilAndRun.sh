#!/bin/bash

echo "Compiling 8266 decoder server..."

echo "Compiling all .cpp files..."
for f in source/*.cpp; do
  g++ -I. -c "$f" -o "source/lin_obj/$(basename "${f%.*}").o"
  if [ $? -ne 0 ]; then
    echo "Compilation failed at $f"
    exit 1
  fi
done

#echo "Compiling all .c files..."
#for f in source/*.c; do
#  gcc -I. -c "$f" -o "source/lin_obj/$(basename "${f%.*}").o"
#  if [ $? -ne 0 ]; then
#    echo "Compilation failed at $f"
#    exit 1
#  fi
#done

echo "Linking objects..."
g++ source/lin_obj/*.o -o server_8266_decoder.out -pthread -ldl -lcurl
if [ $? -ne 0 ]; then
  echo "Linking failed!"
  exit 1
fi

echo "Running 8266_decoder server..."
exec ./server_8266_decoder.out


#gcc -O2 -DSQLITE_THREADSAFE=1 -DSQLITE_OMIT_LOAD_EXTENSION -c sqlite3.c -o lin_obj/sqlite3.o
