#include "http_utils.h"
#include "platform.h"
#include "my_time.h"
#include "g_setup.h"
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
		} 
		else if (i + 2 < lnght) {
			buff2 += (char)((buff[i + 1] <= '9' ? buff[i + 1] - '0' : buff[i + 1] - 'A' + 10) * 16 + (buff[i + 2] <= '9' ? buff[i + 2] - '0' : buff[i + 2] - 'A' + 10));
			i += 3;
		}
		else {
			break;
		}
	}
	return buff2;
}

void send_header(socket_t fd_client, int cod, int length, int typ, const char* typstr) {
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
		} else if(typstr) {
			buff << "Content-Type: " << typstr << "\r\n";
		}
	}
	buff << "Cache-Control: no-cache\r\n";
	buff << "Server: " << g_setup.get("serverName") << "\r\n";
	buff << "Date: " << my_time_str() << "\r\n";
	
	buff << "\r\n";
	buff.seekg(0, std::ios::end);
	length = (int)buff.tellg();
	buff.seekg(0, std::ios::beg);
	_send(fd_client, buff.str().c_str(), length);
}

void send_err(socket_t fd_client, int cod) {
	if(cod > 1000 && cod < 1999) {
		std::string mes = "prykolno, yak ce robytsja";
		send_header(fd_client, cod - 1000, (int)mes.length(), 0, nullptr);
		_send(fd_client, mes.c_str(), (int)mes.length());
	} else {
		send_header(fd_client, cod, 0, 0, nullptr);
	}
}

Response* recv_file(Request* rq) {
	// --- Static file serving from www ---
	std::string path = rq->path;
	if (path.find("/..") != std::string::npos) {
		return new Response(403);
	}
	if (path.empty() || path == "/") path = "/index.html";
	std::string file_path = "www" + path;
	std::ifstream file(file_path, std::ios::binary);
	if (!file) {
		return new Response(404);
	}
	printf("File found: %s response 200\n", file_path.c_str());
	// --- Content-Type detection ---
	std::string content_type = "application/octet-stream";
	std::string ext;
	size_t dot = path.find_last_of('.');
	
	if (dot != std::string::npos)
	{
		ext = path.substr(dot);
		// Перевести у нижній регістр
		for (size_t i = 0; i < ext.length(); ++i) ext[i] = (char)tolower((unsigned char)ext[i]);
		
		if (strcmp(ext.c_str(), ".html") == 0) content_type = "text/html";
		else if (strcmp(ext.c_str(), ".css") == 0) content_type = "text/css";
		else if (strcmp(ext.c_str(), ".js") == 0) content_type = "application/javascript";
		else if (strcmp(ext.c_str(), ".png") == 0) content_type = "image/png";
		else if (strcmp(ext.c_str(), ".jpg") == 0) content_type = "image/jpeg";
		else if (strcmp(ext.c_str(), ".jpeg") == 0) content_type = "image/jpeg";
		else if (strcmp(ext.c_str(), ".gif") == 0) content_type = "image/gif";
		else if (strcmp(ext.c_str(), ".svg") == 0) content_type = "image/svg+xml";
		else if (strcmp(ext.c_str(), ".ico") == 0) content_type = "image/x-icon";
		else if (strcmp(ext.c_str(), ".xml") == 0) content_type = "text/xml";
		else if (strcmp(ext.c_str(), ".txt") == 0) content_type = "text/plain";
		else if (strcmp(ext.c_str(), ".bin") == 0) content_type = "application/octet-stream";
		else if (strcmp(ext.c_str(), ".apk") == 0) content_type = "application/vnd.android.package-archive";
	}
	// ---
	file.seekg(0, std::ios::end);
	int length = (int)file.tellg();
	file.seekg(0, std::ios::beg);
	std::vector<uint8_t> content;
	content.resize(length);
	file.read((char*)content.data(), length);
	
	return new Response(200, content, content_type);
}

void sendResponse(socket_t fd_client, Request* rq, Response* response) {
	if(response->code == 200) {
		send_header(fd_client, response->code, response->content_length, 0, response->content_type.c_str());
		if (rq->method != HEAD && response->content_length > 0) {
			_send(fd_client, response->body.data(), response->content_length);
//			for(int i = 0; i < response->content_length; i++) {
//				printf("%c", (char)response->body.data()[i]);
//			}
//			printf("%s\n", (const char*)(&(response->body.data()[0])));
		}
	}
	else {
		send_err(fd_client, response->code);
		rq->prnt();
	}
}
