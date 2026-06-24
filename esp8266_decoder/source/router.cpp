#include "router.h"
#include "http_utils.h"
#include "platform.h"
#include "request.h"
#include "response.h"
#include "MachineTime.h"
#include <sstream>
#include <fstream>
#include "my_time.h"
#include <iostream>
#include <cstring>
#include <cctype>
#include <ctime>
#include <vector>
#include <algorithm>
#include <climits>
#include <string>

// Platform-specific includes
#ifndef _WIN32
	#include <unistd.h>
	#include <arpa/inet.h>
	#include <netinet/in.h>
	#include <sys/socket.h>
#endif

Response* obrobka_get(Request* rq) {
	if (false)
		return new Response(400);

	else if (rq->path == "/MachineTime18Channels/" && rq->query.size() > 0)
		return getMachineTime18Channels(rq);

	else if (rq->path == "/MachineTime18Channels/") {
		rq->path += "index.html";
		return recv_file(rq);
	}

	else
		return recv_file(rq);
	
	return new Response(500);
}

Response* obrobka_post(Request* rq) {
	std::stringstream buff;
	int typ = 0;

	if (false)
		return new Response(400);

	else if (rq->path == "/MachineTime18Channels/" && rq->query.size() > 0)
		return postMachineTime18Channels(rq);

	else 
		return new Response(400);
	
	return new Response(500);
}


Response* obrobka_put(Request* rq) {
	std::stringstream buff;
	int typ = 0;
	if (false)
		return new Response(400);

	else if (rq->path == "/MachineTime18Channels/" && rq->query.size() > 0)
		return putMachineTime18Channels(rq);

	else 
		return new Response(400);
		
	return new Response(500);
}

Response* obrobka_delete(Request* rq) {
	std::stringstream buff;
	if (false)
		return new Response(500);
	
	else if (rq->path == "/MachineTime18Channels/" && rq->query.size() > 0)
		return deleteMachineTime18Channels(rq);

	else 
		return new Response(400);
	
	return new Response(500);
}
