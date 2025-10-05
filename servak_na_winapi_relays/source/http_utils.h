#pragma once
#include <string>
#include <cstdint>
#include <cstring>  // For memset
#include "platform.h"

// Platform-specific includes
#ifndef _WIN32
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#endif

inline int _send(socket_t fd_client, const void* buff, int length) {
    int total = 0;
    const char* char_buff = static_cast<const char*>(buff);
    while (total < length) {
        int sent = send(fd_client, char_buff + total, length - total, 0);
        if (sent == SOCKET_ERROR_HANDLE) {
            return SOCKET_ERROR_HANDLE;
        }
        total += sent;
    }
    return total;
}

inline int _send(socket_t fd_client, const char* buff, int length) {
    return _send(fd_client, static_cast<const void*>(buff), length);
}

inline int _send(socket_t fd_client, const uint8_t* buff, int length) {
    return _send(fd_client, static_cast<const void*>(buff), length);
}

inline int _resv(socket_t fd_client, char* buff, int length) {
    return recv(fd_client, buff, length, 0);
}

std::string convert_string(const char* buff, int lnght);
void send_kap(socket_t fd_client, int cod, int length, int typ, const char* typstr = nullptr, bool get_log_file_flag = false);
void send_err(socket_t fd_client, int cod);
