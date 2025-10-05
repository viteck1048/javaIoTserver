#include "request.h"
#include "http_utils.h"
#include "platform.h"
#include "my_time.h"
#include <sstream>
#include <fstream>
#include <iostream>
#include <cstdlib>
#include <cstring>

// Platform-specific includes
#ifndef _WIN32
	#include <unistd.h>
	#include <arpa/inet.h>
	#include <netinet/in.h>
	#include <sys/socket.h>
#endif

std::string convert_string(const char* buff, int lnght) {
	std::string buff2;
	int i = 0;
	while(i < lnght) {
		if(buff[i] != '%') {
			if(buff[i] == '+')
				buff2 += ' ';
			else
				buff2 += buff[i];
			i++;
		} else {
			buff2 += (char)((buff[i + 1] <= '9' ? buff[i + 1] - '0' : buff[i + 1] - 'A' + 10) * 16 + (buff[i + 2] <= '9' ? buff[i + 2] - '0' : buff[i + 2] - 'A' + 10));
			i += 3;
		}
	}
	return buff2;
}

void send_kap(socket_t fd_client, int cod, int length, int typ, const char* typstr, bool get_log_file_flag) {
	std::stringstream buff;
	buff << "HTTP/1.1 ";
	switch(cod) {
		case 200: buff << cod << " OK\r\n"; break;
		case 400: buff << cod << " Bad Request\r\n"; break;
		case 404: buff << cod << " Not Found\r\n"; break;
		case 405: buff << cod << " Method Not Allowed\r\nAllow: GET, HEAD, POST, PUT, DELETE\r\n"; break;
		case 501: buff << cod << " Not Implemented\r\n"; break;
		case 503: buff << cod << " Service Unavailable\r\n"; break;
		case 505: buff << cod << " HTTP Version Not Supported\r\n"; break;
		case 415: buff << cod << " Unsupported Type\r\n"; break;
		default: buff << 500 << " Internal Server Error\r\n";
	}
	if(cod == 200) {
		buff << "Content-Length: " << length << "\r\n";
		if(typ != 0) {
			if(typ == 1) buff << "Content-Type: text/html; charset=UTF-8\r\n";
			if(typ == 2) buff << "Content-Type: text/plain; charset=UTF-8\r\n";
			if(typ == 3) buff << "Content-Type: text/json; charset=UTF-8\r\n";
			if(typ == 4) buff << "Content-Type: application/octet-stream\r\n";
			if(typ == 5) buff << "Content-Type: text/html; charset=UTF-8\r\n";
		} else if(typstr) {
			buff << "Content-Type: " << typstr << "\r\n";
		}
	}
	buff << "Cache-Control: no-cache\r\n";
	buff << "Server: RelayServer\r\n";
	buff << "Date: " << my_time_str() << "\r\n";
	if(get_log_file_flag) {
		buff << "X-Get: log_file\r\n";
	}
	
	buff << "\r\n";
	buff.seekg(0, std::ios::end);
	length = (int)buff.tellg();
	buff.seekg(0, std::ios::beg);
	_send(fd_client, buff.str().c_str(), length);
}

void send_err(socket_t fd_client, int cod) {
	if(cod > 1000 && cod < 1999) {
		std::string mes = "prykolno, yak ce robytsja";
		send_kap(fd_client, cod - 1000, (int)mes.length(), 0, nullptr);
		_send(fd_client, mes.c_str(), (int)mes.length());
	} else {
		send_kap(fd_client, cod, 0, 0, nullptr);
	}
}

// decod_request will be implemented in server_relays.cpp (uses relay logic)
