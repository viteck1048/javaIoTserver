#!/bin/bash

echo "Compiling Servak.java..."
javac -d "obj/" -classpath "lib/*" -sourcepath ".:source/" Servak.java -Xdiags:verbose
if [ $? -ne 0 ]; then
  echo "Compilation failed!"
  exit 1
fi

echo "Running Servak..."
sudo java -cp ".:obj:lib/*" Servak -c confMijServak.ini
