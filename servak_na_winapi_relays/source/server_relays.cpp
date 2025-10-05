#include <iostream>
#include <cstdlib>
#include <vector>
#include <string>
#include <sstream>
#include <fstream>
#include <cstring>
#include <csignal>
#include "platform.h"
#include "sqlite_helper.h"
#include "relay.h"
#include "my_time.h"
#include "request.h"
#include "http_utils.h"
#include "relay_http_handlers.h"

// For Linux compatibility
#ifndef _WIN32
	#include <netdb.h>
	#include <unistd.h>
	#include <arpa/inet.h>
	#include <netinet/in.h>
	#include <sys/socket.h>
	#include <signal.h>
#endif

#define PORT 8081

int decod_request(Request* rq, socket_t fd_client);

int main() {
	char buffer[1024] = {0};
	
	if (GET_CURRENT_DIR(buffer, sizeof(buffer)) == 0) {
		printf("Failed to get current directory.\n");
		return 1;
	}

	printf("Current directory: %s\n", buffer);
	
	// Network initialization
#ifdef _WIN32
	WSADATA wsaData;
	if (WSAStartup(MAKEWORD(2,2), &wsaData) != 0) {
		std::cerr << "WSAStartup failed" << std::endl;
		return 1;
	}
#else
    // For Linux, ensure SIGPIPE doesn't kill the process
    signal(SIGPIPE, SIG_IGN);
    
    // Initialize network on Linux (no special initialization needed for Linux)
    WSADATA wsaData;  // Dummy variable for compatibility
#endif

	// 2. Створення сокета
	socket_t server_fd = socket(AF_INET, SOCK_STREAM, 0);
	if (server_fd == INVALID_SOCKET_HANDLE) {
		std::cout << "socket failed" << std::endl;
#ifdef _WIN32
		WSACleanup();
#endif
		return 1;
	}

	// 3. Опції сокета
	int opt = 1;
	int send_buffer_size = 4 * 1024 * 1024; // 4 MB

	// Встановлення розміру буфера відправки
	setsockopt(server_fd, SOL_SOCKET, SO_SNDBUF, (char*)&send_buffer_size, sizeof(send_buffer_size));

	// Встановлення опції повторного використання адреси
	if (setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, (char*)&opt, sizeof(opt)) == SOCKET_ERROR_HANDLE) {
		std::cout << "setsockopt failed" << std::endl;
		CLOSE_SOCKET(server_fd);
#ifdef _WIN32
		WSACleanup();
#endif
		return 1;
	}
	sockaddr_in address;
	address.sin_family = AF_INET;
	if (inet_pton(AF_INET, "127.0.0.1", &address.sin_addr) != 1) {
		std::cerr << "inet_pton failed for 127.0.0.1" << std::endl;
		CLOSE_SOCKET(server_fd);
		WSACleanup();
		return 1;
	}
 //   address.sin_addr.s_addr = INADDR_ANY;
 
	address.sin_port = htons(PORT);

	// 4. Прив'язка
	if (bind(server_fd, (struct sockaddr*)&address, sizeof(address)) == SOCKET_ERROR_HANDLE) {
		std::cout << "bind failed" << std::endl;
		CLOSE_SOCKET(server_fd);
#ifdef _WIN32
		WSACleanup();
#endif
		return 1;
	}

	if (listen(server_fd, SOMAXCONN) == SOCKET_ERROR_HANDLE) {
		std::cout << "listen failed" << std::endl;
		CLOSE_SOCKET(server_fd);
#ifdef _WIN32
		WSACleanup();
#endif
		return 1;
	}
	std::cout << "Relay server run on port " << PORT << std::endl;

	// 5. SQLite: створення/відкриття БД
	SqliteHelper db("relay_data.db");
	db.exec("CREATE TABLE IF NOT EXISTS log(id INTEGER PRIMARY KEY, msg TEXT);");
	hide_log = 1;
	// 6. Основний цикл HTTP-сервера
	while (true) {
		socket_t new_socket = accept(server_fd, NULL, NULL);
		if (new_socket == INVALID_SOCKET_HANDLE) {
			std::cout << "accept failed" << std::endl;
			continue;
		}
		Request rq;
		if (decod_request(&rq, new_socket)) {
			CLOSE_SOCKET(new_socket);
			std::cout << "decod_request failed" << std::endl;
			continue;
		}
		// Мінімальна перевірка
		if (!rq.f_path || !rq.f_host || !rq.f_user_agent || rq.method == -1) {
			printf("%s header invalid\n", my_time_str().c_str());
			rq.prnt();
			//send_err(new_socket, 400);
			//closesocket(new_socket);
			//continue;
		}
		/*if(!strcmp(rq.path.c_str(), "/update_state") && rq.method == POST) {
			printf("'%s'\n", rq.path.c_str());
			rq.prnt();
		}*/
		printf("\r %s\t ", my_time_str().c_str());
		if(rq.param("command")) {
			if(rq.check_param("command", "exit")) {
				send_err(new_socket, 200);
				CLOSE_SOCKET(new_socket);
#ifdef _WIN32
				WSACleanup();
#endif
				return 0;
			}
			if(rq.check_param("command", "log_on")) {
				hide_log = 0;
			}
			if(rq.check_param("command", "log_off")) {
				hide_log = 1;
			}
			if(rq.check_param("command", "log_file")) {
				if(!strcmp(rq.path.c_str(), "/view_log_order.html")) {
					if(rq.param("sn")) {
						set_get_log_file_flag(atoi(rq.znach("sn")), atoi(rq.znach("userID")), true);
						rq.deleteParam("sn");
					}
				}
				else if(!strcmp(rq.path.c_str(), "/clear_get_log_file_flag")) {
					if(rq.param("sn")) {
						set_get_log_file_flag(atoi(rq.znach("sn")), atoi(rq.znach("userID")), false);
						rq.deleteParam("sn");
					}
					send_err(new_socket, 200);
					continue;
				}
			}
			rq.deleteParam("command");
		}
		// Делегування обробки
		int res = 0;
		switch (rq.method) {
			case GET:
				res = obrobka_get(new_socket, &rq);
				break;
			case HEAD:
				res = obrobka_get(new_socket, &rq, true);
				break;
			case POST:
				res = obrobka_post(new_socket, &rq);
				break;
			case PUT:
				res = obrobka_put(new_socket, &rq);
				break;
			default:
				send_err(new_socket, 405);
				break;
		}
		
		shutdown(new_socket, SD_SEND);
		CLOSE_SOCKET(new_socket);
	}
	CLOSE_SOCKET(server_fd);
#ifdef _WIN32
	WSACleanup();
#endif
	return 0;
}

// decod_request: копія адаптованої функції з KM_server.cpp
int decod_request(Request* rq, socket_t fd_client) {
	char *buff = (char*)calloc(1024 * 101, sizeof(char));
	int rr = _resv(fd_client, buff, 1024 * 101 * sizeof(char));
	rq->header = buff;
	int content_len = 0;
	if(buff[0] == 0) {
		free(buff);
		return 1;
	}
	int bbb = 0;
	int len = strlen(buff);
	int fl = 0;
	int i = 0, j = 0;
	for(j = i;j < len ; j++) {
		if(buff[j] == (char)13) {
			buff[j] = 0;
			if(!i) {
				for(int ii = i; ii < j; ii++) {
					if(buff[ii] == ' ') {
						buff[ii] = 0;
						if(!i) {
							if(!strcmp(&(buff[0]), "GET")) rq->method = GET;
							else if(!strcmp(&(buff[0]), "OPTIONS")) rq->method = OPTIONS;
							else if(!strcmp(&(buff[0]), "HEAD")) rq->method = HEAD;
							else if(!strcmp(&(buff[0]), "POST")) rq->method = POST;
							else if(!strcmp(&(buff[0]), "PUT")) rq->method = PUT;
							else if(!strcmp(&(buff[0]), "PATCH")) rq->method = PATCH;
							else if(!strcmp(&(buff[0]), "DELETE")) rq->method = DELETE;
							else if(!strcmp(&(buff[0]), "TRACE")) rq->method = TRACE;
							else if(!strcmp(&(buff[0]), "CONNECT")) rq->method = CONNECT;
							fl = 1;
							i = ii + 1;
						} else if(fl == 1) {
							int jj;
							for(jj = i; buff[jj] != 0; jj++) {
								if(buff[jj] == '?') {
									buff[jj] = 0;
									bbb = jj + 1;
									break;
								}
							}
							rq->path = convert_string(&(buff[i]), jj - i + 1);
							rq->f_path = 1;
							fl = 0;
							rq->prot = &(buff[ii + 1]);
							rq->f_prot = 1;
						}
					}
				}
			} else {
				int ii;
				for(ii = i; ii < j; ii++) {
					if(buff[ii] == ':') break;
				}
				buff[ii] = 0;
				ii += 2;
				if(!strcmp("Accept-Language", &(buff[i]))) {
					rq->accept_language = &(buff[ii]);
					rq->f_accept_language = 1;
				}
				if(!strcmp("Host", &(buff[i]))) {
					rq->host = &(buff[ii]);
					rq->f_host = 1;
				}
				if(!strcmp("User-Agent", &(buff[i]))) {
					rq->user_agent = &(buff[ii]);
					rq->f_user_agent = 1;
				}
				if(!strcmp("Content-Length", &(buff[i]))) {
					content_len = atoi(&(buff[ii]));
				}
			}
			if((j - i) <= 2 && fl != 5) fl = 5;
			if((j - i) <= 2 && fl == 5) {
				j += 2;
				i = j;
				break;
			}
			j += 2;
			i = j;
		}
	}
	i += 2;
	if(rq->method == 1001 && bbb != 0) {
		i = bbb;
	} else if((rq->method == 1004 || rq->method == 1005 || rq->method == 1007) && content_len + i > len) {
		int already = len - i;
		int need = content_len - already;
		int got = 0;
		while (got < need) {
			int n = _resv(fd_client, buff + len + got, need - got);
			if (n <= 0) break;
			got += n;
		}
		if(!(!strcmp(rq->path.c_str(), "/log_file") && rq->method == POST))
			rq->header += std::string(buff + i, content_len);
	}
	j = i;
	len = i;
	for(; buff[len] != 0; len++);
	while(j < len) {
		if(!strcmp(rq->path.c_str(), "/log_file") && rq->method == POST) {
			rq->body.push_back(buff[j]);
			//printf("%c", buff[j]);
			j++;
		} else {
			if(buff[j] == (char)13 || rq->query_len == 256) break;
			for(;buff[j] != '=' && j < len; j++);
			rq->query[rq->query_len].param = convert_string(&(buff[i]), j - i);
			j++;
			i = j;
			for(;buff[j] != '&' && j < len; j++);
			rq->query[rq->query_len].znach = convert_string(&(buff[i]), j - i);
			rq->query[rq->query_len].aa = true;
			rq->query_len++;
			if(buff[j] != 0) j++;
			i = j;
		}
	}
	free(buff);
	if(!strcmp(rq->path.c_str(), "/log_file") && rq->method == POST && false)
		rq->prnt();
	return 0;
}

