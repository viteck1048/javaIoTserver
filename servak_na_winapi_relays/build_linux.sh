#!/bin/bash

# Create build directory if it doesn't exist
mkdir -p build_linux
cd build_linux

# Run CMake to generate Makefiles
cmake ..

# Build the project
make -j$(nproc)

# Copy the executable to the parent directory
cp server_relays ..

echo "Build complete. The executable is available as 'server_relays' in the project root."
