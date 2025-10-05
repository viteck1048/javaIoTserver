#pragma once
#include "request.h"
#include "platform.h"

#define GET 1001
#define OPTIONS 1002
#define HEAD 1003
#define POST 1004
#define PUT 1005
#define PATCH 1006
#ifdef DELETE
	#undef DELETE
#endif
#define DELETE 1007
#define TRACE 1008
#define CONNECT 1009

// Forward declarations

int obrobka_get(socket_t fd_client, Request* rq, bool head = false);
int obrobka_post(socket_t fd_client, Request* rq);
int obrobka_put(socket_t fd_client, Request* rq);
int obrobka_delete(socket_t fd_client, Request* rq);

// Device management handlers
void handle_device_status(socket_t fd_client, const Request* rq);
void handle_register_device(socket_t fd_client, const Request* rq);