#ifndef HTTP_PARSER_H
#define HTTP_PARSER_H

#pragma once

#include "request.h"
#include "platform.h"

// Parse HTTP request from socket and return Request structure
// Returns nullptr if parsing fails
Request* parse_http_request(socket_t client_fd);

#endif // HTTP_PARSER_H