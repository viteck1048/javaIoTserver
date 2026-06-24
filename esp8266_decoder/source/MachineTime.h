#ifndef MACHINE_TIME_H
#define MACHINE_TIME_H

#include "request.h"
#include "platform.h"
#include "response.h"

void init_machine_time_db(const char* path);
void start_machine_time_agent_and_signals();
Response* getMachineTime18Channels(Request* rq);
Response* postMachineTime18Channels(Request* rq);
Response* putMachineTime18Channels(Request* rq);
Response* deleteMachineTime18Channels(Request* rq);

#endif // MACHINE_TIME_H
