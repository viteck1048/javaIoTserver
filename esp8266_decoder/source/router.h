#ifndef ROUTER_H
#define ROUTER_H
#include "response.h"
#include "request.h"
#include "platform.h"
#pragma once

// Forward declarations

Response* obrobka_get(Request* rq);
Response* obrobka_post(Request* rq);
Response* obrobka_put(Request* rq);
Response* obrobka_delete(Request* rq);

#endif // ROUTER_H