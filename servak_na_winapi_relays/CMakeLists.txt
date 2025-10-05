cmake_minimum_required(VERSION 3.10)
project(servak_na_winapi_relays)

# Set C++ standard
set(CMAKE_CXX_STANDARD 11)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Set source files
set(SOURCES
    source/server_relays.cpp
    source/http_utils.cpp
    source/relay_http_handlers.cpp
    source/relay.cpp
    source/my_time.cpp
    source/sqlite_helper.cpp
)

# Set include directories
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/source
)

# Platform-specific settings
if(WIN32)
    # Windows-specific settings
    add_definitions(-DWIN32 -D_WIN32 -D_WINDOWS)
    set(PLATFORM_LIBS ws2_32)
else()
    # Linux-specific settings
    add_definitions(-DLINUX)
    find_package(Threads REQUIRED)
    set(PLATFORM_LIBS ${CMAKE_THREAD_LIBS_INIT} dl)
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -pthread")
endif()

# Find SQLite3
find_package(SQLite3 REQUIRED)
include_directories(${SQLITE3_INCLUDE_DIRS})

# Add executable
add_executable(server_relays ${SOURCES})

# Link libraries
target_link_libraries(server_relays 
    ${PLATFORM_LIBS} 
    ${SQLITE3_LIBRARIES}
)

# Installation
install(TARGETS server_relays DESTINATION bin)
