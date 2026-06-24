#ifndef HTTP_UTILS_H
#define HTTP_UTILS_H

#pragma once
#include <string>
#include <cstdint>
#include <cstring>  // For memset
#include "platform.h"
#include "response.h"
#include "request.h"

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
    //printf("%s\n", char_buff);
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
    int total = 0;
    while (total < length) {
        int received = recv(fd_client, buff + total, length - total, 0);
        if (received == SOCKET_ERROR_HANDLE) {
            return SOCKET_ERROR_HANDLE;
        }
        total += received;
    }
    return total;
}

inline int _resv(socket_t fd_client, uint8_t* buff, int length) {
    return _resv(fd_client, reinterpret_cast<char*>(buff), length);
}

inline std::string _resv_line(socket_t fd_client) {
    std::string result;
    char ch;
    int received;
    while ((received = recv(fd_client, &ch, 1, 0)) > 0) {
        result += ch;
        if (ch == '\n') {
            break;
        }
    }
    if (received == SOCKET_ERROR_HANDLE) {
        return "";
    }
    return result;
}

std::string convert_string(const char* buff, int lnght);
void send_header(socket_t fd_client, int cod, int length, int typ, const char* typstr = nullptr);
void send_err(socket_t fd_client, int cod);
Response* recv_file(Request* rq);
void sendResponse(socket_t fd_client, Request* rq, Response* response);

#endif // HTTP_UTILS_H