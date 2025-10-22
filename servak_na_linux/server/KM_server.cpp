

//#define __THREADS
//#define __FORK
//#define TEST_DB
//www


#ifdef __FORK
	#undef __THREADS
#endif

#ifdef _WIN32
	#include <stdio.h>
	#include <stdlib.h>
	#include <winsock2.h>
	#include <Windows.h>
	#include <conio.h>
	#include <WS2tcpip.h>	
	#include <sstream>
	#include <fstream>
	#include <string>
	
	#pragma comment(lib, "ws2_32.lib")
	#pragma warning(disable : 4996)
	
//	typedef unsigned long int uint32_t;
	
	#define PORT 8081
	#define TEST_DB
	
#elif __linux__ 
	#include <stdio.h>
	#include <stdlib.h>
	#include <unistd.h>
	#include <sys/types.h>
	#include <sys/socket.h>
	#include <sys/select.h>
	#include <netinet/in.h>
	#include <netdb.h>
	#include <arpa/inet.h>
	#include <string.h>
	#include <sys/stat.h>
	#include <sstream>
	#include <fstream>
	#include <string>
	#include <fcntl.h>
	#include <termios.h>

	#ifndef PTHREAD_H
		#include <pthread.h>
	#endif
	
	#define PORT 8080
	
#endif
#include <signal.h>
#include <iostream>
#include <regex>
#include <time.h>
#include <malloc.h>
//#include <signal.h>


//#define PORT 8080
#define SA struct sockaddr

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

#ifdef __linux__
//	typedef int SOCKET;
	#define SOCKET int

int kbhit() {
    struct timeval tv = {0, 0};
    fd_set readfds;
    FD_ZERO(&readfds);
    FD_SET(STDIN_FILENO, &readfds);
    return select(1, &readfds, NULL, NULL, &tv);
}

char _getch() {
	return getchar();
}
	
#endif

#include "my_time.h"

#define MAIN_SERVER
#include "status.cpp"
#include "db.cpp"
#include "index.cpp"
#include "get_json.cpp"
#include "add.cpp"
#include "save.cpp"
#include "delete.cpp"
#include "dwnldcnf.cpp"
#include "relay.cpp"

pthread_mutex_t send_mutex;
pthread_mutex_t resv_mutex;

my_time_cls mt;

struct ThreadArgs {
	SOCKET fd_client;
	char *buff;
	int fl_exit;
	// Інші параметри, якщо потрібно
};


int _send(SOCKET fd_client, const char* buff, int length)
{
	int rr;
//	pthread_mutex_lock(&send_mutex);//puts("268-----------------------------close");
	
#ifdef _WIN32	
		rr = send(fd_client, buff, length, 0);
#elif __linux__ 		
		rr = send(fd_client, buff, length, MSG_NOSIGNAL);

#endif
//	pthread_mutex_unlock(&send_mutex);//puts("275=========================open");
	return rr;
}


int _resv(SOCKET fd_client, char* buff, int length)
{
	int rr = 0;
	int dd;
#ifdef __THREADS
	
//	pthread_mutex_lock(&send_mutex);//puts("398---------------------------close");
#endif
	
	rr = recv(fd_client, buff, length, 0);

#ifdef __THREADS
	
//	pthread_mutex_unlock(&send_mutex);//puts("400=========================open");
#endif
	
	return rr;
}


struct Query{
	std::string param;
	std::string znach;
	bool aa;
	Query() {
		aa = 0;
	}
};


struct Request{
	int method;
	std::string path;
	std::string accept_language;
	std::string host;
	std::string user_agent;
	std::string prot;
	std::string header;
	Query query[256];
	int query_len;
	bool f_path, f_accept_language, f_host, f_user_agent, f_prot;
	bool param(const char* pr) {
		for(int i = 0; i < query_len; i++) {
			if(!strcmp(query[i].param.c_str(), pr)) {
				return true;
			}
		}
		return false;
	}
	bool check_param(const char* pr, const char* zn) {
		for(int i = 0; i < query_len; i++) {
			if(!strcmp(query[i].param.c_str(), pr)) {
				if(!strcmp(query[i].znach.c_str(), zn))
					return true;
			}
		}
		return false;
	}
	const char* znach(const char* pr) {
		for(int i = 0; i < query_len; i++) {
			if(!strcmp(query[i].param.c_str(), pr)) {
				return query[i].znach.c_str();
			}
		}
		return NULL;
	}
	void prnt() {
	//	printf("%d\n", method);
	//	puts(path.c_str());
	//	puts(accept_language.c_str());
	//	puts(host.c_str());
	//	puts(user_agent.c_str());
	//	puts(prot.c_str());
//		my_time_cls mt;
//		mt.update(getLocalTimeOffset());
		puts("------------------------------------------------------------------------------------------------------------------------------------------------");
//		printf("%s %s %s:%s:%s\n\n", mt.day(2), mt.mon(3), mt.hor(2), mt.min(), mt.sec());
		puts(header.c_str());
		puts("------------------------------------------------------------------------");
		printf("param = %d\n", query_len);
		for(int i = 0; i < query_len; i++)
			printf("%s = %s\n", query[i].param.c_str(), query[i].znach.c_str());
		puts("------------------------------------------------------------------------------------------------------------------------------------------------");
	}
	Request() {
		method = -1;
		query_len = 0;
		f_path = 0;
		f_accept_language = 0;
		f_host = 0;
		f_user_agent = 0;
		f_prot = 0;
	}
};


std::string convert_string(const char* buff, int lnght)
{
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
		else {
			buff2 += (char)((buff[i + 1] <= '9' ? buff[i + 1] - '0' : buff[i + 1] - 'A' + 10) * 16 + (buff[i + 2] <= '9' ? buff[i + 2] - '0' : buff[i + 2] - 'A' + 10));
			i += 3;
		}
	}
	return buff2;
}


void send_kap(SOCKET fd_client, int cod, int length, int typ, const char* typstr = NULL)
{
	std::stringstream buff;
	buff << "HTTP/1.1 ";
	switch(cod) {
		case 200:
			buff << cod << " OK\r\n";
			break;
		case 400:
			buff << cod << " Bad Request\r\n";
			break;
		case 404:
			buff << cod << " Not Found\r\n";
			break;
		case 405:
			buff << cod << " Method Not Allowed\r\nAllow: GET, HEAD, POST, PUT, DELETE\r\n";
			break;
		case 501:
			buff << cod << " Not Implemented\r\n";
			break;
		case 503:
			buff << cod << " Service Unavailable\r\n";
			break;
		case 505:
			buff << cod << " HTTP Version Not Supported\r\n";
			break;
		case 415:
			buff << cod << " Unsupported Type\r\n";
			break;
		default:
			buff << (int)500 << " Internal Server Error\r\n";
	}
	if(cod == 200) {
		buff << "Content-Length: " << length << "\r\n";
		if(typ != 0) {
			if(typ == 1)
				buff << "Content-Type: text/html; charset=UTF-8\r\n";
			if(typ == 2)
				buff << "Content-Type: text/plain; charset=UTF-8\r\n";
			if(typ == 3)
				buff << "Content-Type: text/json; charset=UTF-8\r\n";
			if(typ == 4)
				buff << "Content-Type: application/octet-stream\r\n";
			if(typ == 5)
				buff << "Content-Type: text/plain; charset=windows-1251\r\n";
		}
		else {
			buff << "Content-Type: " << typstr << "\r\n";
		}
	}
	buff << "Cache-Control: no-cache\r\n";
	buff << "Server: My_Kursach_rskk\r\n";
	buff << "Date: ";
//	my_time_cls mt;
	char bbb[30];
	mt.update(0);
	sprintf(bbb, "%s, %s %s %s %s:%s:%s GMT\r\n", mt.daytoweek(3), mt.day(2), mt.mon(3), mt.ear(4), mt.hor(2), mt.min(), mt.sec());
	buff << bbb;
	if(cod != 200)
		buff << "Connection: Closed\r\n";
	buff << "\r\n";
//	buff.seekg(0, std::ios::end);
//	length = (int)buff.tellg();
//	buff.seekg(0, std::ios::beg);
	_send(fd_client, buff.str().c_str(), buff.str().length());
}


void send_err(SOCKET fd_client, int cod)
{
	if(cod > 1000 && cod < 1999) {
		std::string mes = "prykolno, yak ce robytsja";
		send_kap(fd_client, cod - 1000, mes.length(), 0);
		_send(fd_client, mes.c_str(), mes.length());
	}
	else
		send_kap(fd_client, cod, 0, 0);
}


void obrobka_post(SOCKET fd_client, Request* rq)
{
	std::stringstream buff;
//	puts("255");///////////////////////////////////////////////////////////////////////////////			255
//	rq->prnt();
	if(rq->param("search_m"))
		buff << index_cpp_get(POST, -1, rq->znach("search_m"));
	
	else if(rq->query_len == 4 && !strcmp(rq->path.c_str(), "/add-mash"))
		buff << post_add_mash(rq->znach("mash[NAME]"), rq->znach("mash[M1]"));
	
	else if(rq->query_len == 1 && !strcmp(rq->path.c_str(), "/add-lira"))
		buff << add_lira(atoi(rq->znach("m_id")));
	
	else if(rq->query_len == 1 && !strcmp(rq->path.c_str(), "/add-umova"))
		buff << add_umova(atoi(rq->znach("l_id")));
	
	else if(rq->query_len == 1 && !strcmp(rq->path.c_str(), "/add-zm-npp"))
		buff << add_zm_npp(atoi(rq->znach("zz_id")));
	
	else if(rq->query_len == 3 && !strcmp(rq->path.c_str(), "/add-zminna"))
		buff << add_zminna(atoi(rq->znach("l_id")), *(rq->znach("bukva")), atoi(rq->znach("zm_poz")));
	
	else if(rq->query_len == 5 && !strcmp(rq->path.c_str(), "/lialiapam"))
		buff << obrobnyk_relay(atoi(rq->znach("sn")), rq->znach("sn"), rq->znach("relays"), atoi(rq->znach("acp")), rq->znach("acp"), rq->check_param("init", "true"), atoi(rq->znach("cs")), rq->znach("init"));
	
	else if(rq->query_len == 1 && !strcmp(rq->path.c_str(), "/lialiapam/")) {
		puts(rq->znach("param"));
		buff << "привіт, жорстокий світе!";
	}
	
	else {
		puts("7777777777777777777777\t\t329\t\tundeclarated POST");
		rq->prnt();
		return;
	}
	buff.seekg(0, std::ios::end);
	int length = (int)buff.tellg();
	buff.seekg(0, std::ios::beg);
	send_kap(fd_client, 200, length, 1);
	_send(fd_client, buff.str().c_str(), length);
}


void obrobka_delete(SOCKET fd_client, Request* rq)
{
	std::stringstream buff;
//	puts("345");///////////////////////////////////////////////////////////////////////////////			345
//	rq->prnt();
	if(rq->query_len == 1 && !strcmp(rq->path.c_str(), "/delete-lira"))
		buff << delete_lira(atoi(rq->znach("l_id")));
	
	else if(rq->query_len == 1 && !strcmp(rq->path.c_str(), "/delete-mash"))
		buff << delete_mash(atoi(rq->znach("m_id")));
	
	else if(rq->query_len == 1 && !strcmp(rq->path.c_str(), "/delete-um"))
		buff << delete_um(atoi(rq->znach("u_id")));
	
	else if(rq->query_len == 2 && !strcmp(rq->path.c_str(), "/delete-zm"))
		buff << delete_zm(atoi(rq->znach("l_id")), atoi(rq->znach("z_id")));
	
	else if(rq->query_len == 1 && !strcmp(rq->path.c_str(), "/delete-zm-npp"))
		buff << delete_zm_npp(atoi(rq->znach("n_id")));
	
	else if(rq->query_len == 1 && !strcmp(rq->path.c_str(), "/delete-zm-npp-all"))
		buff << delete_zm_npp_all(atoi(rq->znach("z_id")));
	
	else {
		puts("7777777777777777777777\t\t366\t\tundeclarated DELETE");
		rq->prnt();
		return;
	}
	buff.seekg(0, std::ios::end);
	int length = (int)buff.tellg();
	buff.seekg(0, std::ios::beg);
	send_kap(fd_client, 200, length, 1);
	_send(fd_client, buff.str().c_str(), length);
}


void obrobka_put(SOCKET fd_client, Request* rq)
{
	std::stringstream buff;
//	puts("322");///////////////////////////////////////////////////////////////////////////////			322
//	rq->prnt();
	
	char lng[3];
	lng[0] = rq->accept_language[0];
	lng[1] = rq->accept_language[1];
	lng[2] = 0;
	
	if(rq->query_len == 2 && !strcmp(rq->path.c_str(), "/lialiapam") && rq->param("sn") && rq->param("button"))
		buff << rr_put_status(atoi(rq->znach("sn")), atoi(rq->znach("button")));
	
	else if(rq->query_len == 7 && !strcmp(rq->path.c_str(), "/save-lira"))
		buff << save_lira(atoi(rq->znach("lira[M_ID]")), atoi(rq->znach("lira[L_ID]")), rq->znach("lira[NAME]"), atoi(rq->znach("lira[MAGAZ]")), atoi(rq->znach("lira[BR_KOL_LIR]")), rq->znach("lira[FORM]"), rq->znach("lira[FORM_ZV]"), lng);
	
	else if(rq->query_len == 4 && !strcmp(rq->path.c_str(), "/save-mash"))
		buff << save_mash(atoi(rq->znach("mash[M_ID]")), rq->znach("mash[NAME]"), rq->znach("mash[M1]"), rq->znach("mash[M2]"), lng);
	
	else if(rq->query_len == 5 && !strcmp(rq->path.c_str(), "/save-npp"))
		buff << save_npp(atoi(rq->znach("npp[N_ID]")), atoi(rq->znach("npp[Z_ID]")), atof(rq->znach("npp[ZNACHENNJA]")), rq->znach("npp[UMOVA]"), rq->znach("npp[COMENT]"), lng);
	
	else if(rq->query_len == 3 && !strcmp(rq->path.c_str(), "/save-usl"))
		buff << save_usl(atoi(rq->znach("usl[U_ID]")), atoi(rq->znach("usl[L_ID]")), rq->znach("usl[UMOVA]"), lng);
	
	else if(rq->query_len == 7 && !strcmp(rq->path.c_str(), "/save-zm"))
		buff << save_zm(atoi(rq->znach("zm[M_ID]")), atoi(rq->znach("zm[L_ID]")), atoi(rq->znach("zm[Z_ID]")), *(rq->znach("zm[BUKVA]")), atoi(rq->znach("zm[NPP_S]")), rq->znach("zm[NAME]"), atof(rq->znach("zm[ZNACHENNJA]")), lng);
	
	else {
		puts("7777777777777777777777\t\t377\t\tundeclarated PUT");
		rq->prnt();
		return;
	}
	
	buff.seekg(0, std::ios::end);
	int length = (int)buff.tellg();
	buff.seekg(0, std::ios::beg);
	send_kap(fd_client, 200, length, 1);
	_send(fd_client, buff.str().c_str(), length);
}


void send_res(SOCKET fd_client, Request* rq, bool head = false)
{
	std::ifstream fl;
	int length, typ = 0;
	std::string bb, typstr;
	int lngf = -1;
	int fl_content = 0;
	if(!strcmp(rq->path.c_str(), "/lialiapam") && rq->param("sn")) {
		bb = rr_get_status(atoi(rq->znach("sn")));
		typ = 3;
		fl_content = 1;
//		rq->prnt();
	}
	else if(!strcmp(rq->path.c_str(), "/download") && rq->param("m_id")) {
		bb = dwnldcnf(atoi(rq->znach("m_id")));
		typ = 4;
		fl_content = 1;
	}
	else if(!strcmp(rq->path.c_str(), "/preview") && rq->param("m_id")) {
		bb = dwnldcnf(atoi(rq->znach("m_id")));
		typ = 5;
		fl_content = 1;
	}
	else if(rq->path.c_str()[0] == '/' && rq->path.c_str()[1] == 0) {
		
		if(rq->query_len == 0) {
			bb = index_cpp_get(GET, -1, NULL);
			typ = 1;
		}
		else if(rq->query_len == 1 && rq->param("m_id")) {
			bb = index_cpp_get(GET, atoi(rq->znach("m_id")), NULL);
			typ = 1;
		}
		else if(rq->query_len == 3 && rq->check_param("json", "liry")) {
			bb = get_json_liry(atoi(rq->znach("m_id")), rq->znach("name"));
			typ = 3;
		}
		else if(rq->query_len == 2 && rq->check_param("json", "mash")) {
			bb = get_json_mash(rq->znach("name"));
			typ = 3;
		}
		else if(rq->query_len == 3 && rq->check_param("json", "npp")) {
			bb = get_json_npp(atoi(rq->znach("z_id")), atoi(rq->znach("l_id")));
			typ = 3;
		}
		else if(rq->query_len == 2 && rq->check_param("json", "umovy")) {
			bb = get_json_umovy(atoi(rq->znach("l_id")));
			typ = 3;
		}
		else if(rq->query_len == 3 && rq->check_param("json", "zminny")) {
			bb = get_json_zminny(atoi(rq->znach("z_id")), atoi(rq->znach("zm_poz")));
			typ = 3;
		}
		else if(rq->query_len == 1 && rq->check_param("index", "add-mash")) {
			bb = get_add_mash();
			typ = 1;
		}
		else {
			puts("7777777777777777777777\t\t324\t\tundeclarated GET");
			rq->prnt();
			return;
		}
		fl_content = 1;
	}
	else if(!strcmp(rq->path.c_str(), "/script-coment") && rq->param("res")) {
		typ = 2;
		char lng[3];
		lng[0] = rq->accept_language[0];
		lng[1] = rq->accept_language[1];
		lng[2] = 0;
		char res2[100];
		sprintf(res2, "www/instr/%s/%s.txt", lng, rq->znach("res"));
		fl.open(res2, std::ios::binary);
		if(!fl.good()) {
			sprintf(res2, "www/instr/en/%s.txt", rq->znach("res"));
			fl.open(res2, std::ios::binary);
			if(!fl.good()) {
				puts("7777777777777777777777\t\t324\t\tundeclarated instr");
				rq->prnt();
				return;
			}
		}
		std::cout << "reqwest\t\t" << res2 << std::endl;
		fl_content = 2;
	}
	else if(!strcmp(rq->path.c_str(), "/FireBird")) {
		typ = 1;
		std::cout << "GEN FireBird_DB.html" << std::endl;
		bb = request_db(0);
		fl_content = 1;
	}
	else {
		std::string res2 = "www" + rq->path;
		for(int gg = 0; rq->path[gg + 1] != 0; gg++) {
			if(rq->path[gg] == '.' && rq->path[gg + 1] == '.') {
				if(head == false)
					send_err(fd_client, 1404);
				else
					send_err(fd_client, 404);
				std::cout << "1404 noreqwest\t\t" << res2 << std::endl;
				return;
			}
		}
		fl.open(res2.c_str(), std::ios::binary);
		if(fl.good()) {
			int i = 0;
			for(; rq->path[i] != 0; i++);
			for(; rq->path[i] !='.' && i; i--);
			if(!strcmp(&(rq->path[i + 1]), "css")) {
				typstr = "text/css";
			}else 
			if(!strcmp(&(rq->path[i + 1]), "csv")) {
				typstr = "text/csv";
			}else 
			if(!strcmp(&(rq->path[i + 1]), "html")) {
				typstr = "text/html; charset=UTF-8";
			}else 
			if(!strcmp(&(rq->path[i + 1]), "txt")) {
				typstr = "text/plain";
			}else 
			if(!strcmp(&(rq->path[i + 1]), "cnf")) {
				typstr = "application/octet-stream";
			}else 
			if(!strcmp(&(rq->path[i + 1]), "xml")) {
				typstr = "text/xml";
			}else 
			if(!strcmp(&(rq->path[i + 1]), "js")) {
				typstr = "text/javascript";
			}else 
			if(!strcmp(&(rq->path[i + 1]), "jpg")) {
				typstr = "image/jpeg";
			}else 
			if(!strcmp(&(rq->path[i + 1]), "ico")) {
				typstr = "image/x-icon";
			}else 
			if(!strcmp(&(rq->path[i + 1]), "gif")) {
				typstr = "image/gif";
			}else 
			if(!strcmp(&(rq->path[i + 1]), "png")) {
				typstr = "image/png";
			}else 
			if(!strcmp(&(rq->path[i + 1]), "gjvu")) {
				typstr = "image/vnd.gjvu";
			}else 
			if(!strcmp(&(rq->path[i + 1]), "svg+xml")) {
				typstr = "image/svg";
			}else
			if(!strcmp(&(rq->path[i + 1]), "mp4")) {
				typstr = "video/mp4";
			}else {
				send_err(fd_client, 415);
				return;
			}
			std::cout << "reqwest\t\t" << res2 << std::endl;
		}
		else {
			send_err(fd_client, 404);
			std::cout << "404 noreqwest\t\t" << res2 << std::endl;
			return;
		}
		fl_content = 2;
	}
	std::stringstream buff;
	if(fl_content == 2)
		buff << fl.rdbuf();
	else if(fl_content == 1)
		buff << bb;
	buff.seekg(0, std::ios::end);
	length = (int)buff.tellg();
	buff.seekg(0, std::ios::beg);
	send_kap(fd_client, 200, length, typ, typstr.c_str());
	if(!head) {
		_send(fd_client, buff.str().c_str(), length);
	}
	if(!typ)
		fl.close();
}


int decod_request(Request* rq, SOCKET fd_client)
{
	char *buff = (char*)calloc(2999, sizeof(char));
	
	int rr = _resv(fd_client, buff, 2999 * sizeof(char));
	rq->header = buff;
	
	int content_len = 0;
	if(buff[0] == 0) {			
		printf("\nphantom\n");
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
							if(!strcmp(&(buff[0]), "GET"))
								rq->method = GET;
							else if(!strcmp(&(buff[0]), "OPTIONS"))//відповісти списком підтримуваних методів
								rq->method = OPTIONS;
							else if(!strcmp(&(buff[0]), "HEAD"))
								rq->method = HEAD;
							else if(!strcmp(&(buff[0]), "POST"))//створити
								rq->method = POST;
							else if(!strcmp(&(buff[0]), "PUT"))//оновити
								rq->method = PUT;
							else if(!strcmp(&(buff[0]), "PATCH"))
								rq->method = PATCH;
							else if(!strcmp(&(buff[0]), "DELETE"))//видалити
								rq->method = DELETE;
							else if(!strcmp(&(buff[0]), "TRACE"))//Метод TRACE вимагає, щоб цільовий ресурс передав отриманий запит у тіло відповіді. Таким чином клієнт може побачити, які (якщо такі є) зміни чи доповнення внесли посередники.
								rq->method = TRACE;
							else if(!strcmp(&(buff[0]), "CONNECT"))
								rq->method = CONNECT;
							fl = 1;
							i = ii + 1;
			//				if(rq->method == DELETE || rq->method == POST) {
			//				printf("*******************************\n"); puts(buff + j + 1); printf("*******************************\n");}
						}
						else if(fl == 1) {
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
			}
			else {
				int ii;
				for(ii = i; ii < j; ii++) {
					if(buff[ii] == ':')
						break;
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
			
			if((j - i) <= 2 && fl != 5)
				fl = 5;
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
	if(rq->method == GET && bbb != 0) {
		i = bbb;
	}
	else if((rq->method == POST || rq->method == PUT || rq->method == DELETE) && content_len + i > len) {
		_resv(fd_client, buff + rr, content_len);
		rq->header += (buff + rr);
	}
	j = i;
	len = i;
	for(; buff[len] != 0; len++);
//	puts(&(buff[i]));
//	printf("%d\t%d\t%d\t\n", i, j, len);
	while(j < len) {
		if(buff[j] == (char)13 || rq->query_len == 256)
			break;
		for(;buff[j] != '=' && j < len; j++);
		rq->query[rq->query_len].param = convert_string(&(buff[i]), j - i);
		j++;
		i = j;
		for(;buff[j] != '&' && j < len; j++);
		rq->query[rq->query_len].znach = convert_string(&(buff[i]), j - i);
		rq->query[rq->query_len].aa = true;
		rq->query_len++;
		if(buff[j] != 0)
			j++;
		i = j;
	}
	free(buff);
//	pthread_mutex_unlock(&th_mutex);puts("514=========================open");
//	rq->prnt();
	return 0;
}


void _reqest_thread(SOCKET fd_client)//(void* arg)
{
	mt.update(getLocalTimeOffset());
	printf("\r%s %s %s:%s:%s\t\t", mt.day(2), mt.mon(3), mt.hor(2), mt.min(), mt.sec());
	Request rq;
	if(decod_request(&rq, fd_client)) {			
		return;
	}
	if(!rq.f_path || !rq.f_accept_language && strcmp(rq.path.c_str(), "/lialiapam") || !rq.f_host || !rq.f_user_agent || !rq.f_prot || rq.method == -1) {
		printf("%d %d %d %d %d %d ", rq.f_path, rq.f_accept_language, rq.f_host, rq.f_user_agent, rq.f_prot, rq.method);
		puts("EEERRROOORRR");
		rq.prnt();
	}
	if(rq.param("command")) {
		printf("Command %s\n", rq.znach("command"));
		if(rq.check_param("command", "exit")) {
			puts("--------------EXIT--------------");
			exit(0);
		}
		/* if(rq.check_param("command", "log")) {
			int stdout_fd = open("/dev/stdout", O_RDONLY);

		if (stdout_fd == -1) {
			std::cerr << "Failed to open stdout!" << std::endl;
			return;
		}

		// Читаємо дані з stdout
		const int buffer_size = 1024;
		char buffer[buffer_size];
		ssize_t bytesRead = read(stdout_fd, buffer, buffer_size);

		if (bytesRead == -1) {
			std::cerr << "Failed to read from stdout!" << std::endl;
			close(stdout_fd);
			return;
		}

		// Закриваємо файловий дескриптор
		close(stdout_fd);

		// Виводимо прочитані дані
		std::cout << "Read from stdout: " << std::string(buffer, bytesRead) << std::endl;

		}//*/
	}
	switch(rq.method) {
		case GET: {
			send_res(fd_client, &rq);
			break;
		}
		case HEAD: {
			send_res(fd_client, &rq, true);
			break;
		}
		case POST: {
			obrobka_post(fd_client, &rq);
			break;
		}
		case PUT: {
			obrobka_put(fd_client, &rq);
			break;
		}
		case DELETE: {
			obrobka_delete(fd_client, &rq);
			break;
		}
		case -1: {
			send_err(fd_client, 400);
			break;
		}
		default: {
//			send_res(fd_client, &rq);
			send_err(fd_client, 405);
			rq.prnt();
			break;
		}
	}
//	puts("------------OK");	
}

#ifdef _WIN32
DWORD WINAPI reqest_thread(void* arg)
#else
void* reqest_thread(void* arg)
#endif
{
	ThreadArgs *args = (ThreadArgs*)arg;
	SOCKET fd_client = args->fd_client;
	_reqest_thread(fd_client);
	shutdown(fd_client, SHUT_RDWR);
	close(fd_client);
	free(args);
#ifndef __THREADS	
	return NULL;
#else
	pthread_exit(0);
#endif
}

/* 
void handle_sigpipe(int signo) {
    // Обробка сигналу SIGPIPE
	printf("ERROR SIGPIPE signo = %d\n", signo);
}
 */


/* void sigpipe_handler(int unused)
{
	printf("SIGPIPE\n");
} */


int main(int argc , char* argv[])
{
#ifdef _WIN32	
	WSADATA ws;
	if(FAILED(WSAStartup(MAKEWORD(1, 1), &ws)))
	{
		printf("\nerr.WSA_Start: %d", WSAGetLastError());
		_getch();
		return 0;
	}
#endif
	/* struct sigaction sa;
    sa.sa_handler = sigpipe_handler;
    sa.sa_flags = 0;
//    sigemptyset(&sa.sa_mask);
	sigaction(SIGPIPE, &sa, NULL);
 */
//	signal(SIGPIPE, SIG_IGN);
#if defined(__THREADS) || defined(__FORK)
	pthread_mutexattr_t db_mutex_attr;
	pthread_mutexattr_t resv_mutex_attr;
	pthread_mutexattr_t send_mutex_attr;
	
	pthread_mutexattr_init(&db_mutex_attr);
	pthread_mutexattr_settype(&db_mutex_attr, PTHREAD_MUTEX_ERRORCHECK_NP);
	pthread_mutexattr_setpshared(&db_mutex_attr, PTHREAD_PROCESS_SHARED);
	
	pthread_mutex_init(&db_mutex, &db_mutex_attr);
	
	pthread_mutexattr_init(&resv_mutex_attr);
	pthread_mutexattr_settype(&resv_mutex_attr, PTHREAD_MUTEX_ERRORCHECK_NP);
	pthread_mutexattr_setpshared(&resv_mutex_attr, PTHREAD_PROCESS_SHARED);
	
	pthread_mutex_init(&resv_mutex, &resv_mutex_attr);
	
	pthread_mutexattr_init(&send_mutex_attr);
	pthread_mutexattr_settype(&send_mutex_attr, PTHREAD_MUTEX_ERRORCHECK_NP);
	pthread_mutexattr_setpshared(&send_mutex_attr, PTHREAD_PROCESS_SHARED);
	
	pthread_mutex_init(&send_mutex, &send_mutex_attr);
	/* 
	signal(SIGPIPE, handle_sigpipe);
	 */
#endif

#ifdef _WIN32		
	SOCKET fd_server, fd_client;
	socklen_t sin_len = sizeof(struct sockaddr_in);
	SOCKADDR_IN server_addr, client_addr;
	char truee = 1;
#elif __linux__ 		
	struct sockaddr_in server_addr, client_addr;
	socklen_t sin_len = sizeof(struct sockaddr_in);
	int fd_server, fd_client, truee = 1;
	pthread_t thread_id;
#endif	
	int len;
	fd_server = socket(AF_INET, SOCK_STREAM, 0);
	
	
	if(fd_server == -1){
		perror("Creating socket failed...");
#ifdef _WIN32			
		_getch();
		return 0;
#elif __linux__ 		
		exit(1);
#endif
	}else {
		printf("Socket successfully created..\n");
	}	 
	if(setsockopt(fd_server, SOL_SOCKET, SO_REUSEADDR, &truee , sizeof(truee)) < 0){
		perror("Setsockopt failed...");
#ifdef _WIN32			
		_getch();
		return 0;
#elif __linux__ 		
		exit(1);
#endif
	}
	server_addr.sin_family      = AF_INET;        
	server_addr.sin_addr.s_addr = htonl(INADDR_ANY);
	server_addr.sin_port        = htons(PORT);
	if(bind(fd_server , (SA*)&server_addr, sizeof(server_addr)) < 0) {
		perror("Bind failed...");
#ifdef _WIN32			
		closesocket(fd_server);
		_getch();
		return 0;
#elif __linux__ 		
		shutdown(fd_client, SHUT_RDWR);
		close(fd_server);
		exit(1);
#endif
	}else
		printf("Socket successfully binded..\n");
		
	while(1) {

		listen(fd_server, 8);
		len = sizeof(client_addr);
		fd_client = accept(fd_server, (SA*)&client_addr, &sin_len );
#ifdef __FORK
		if(!fork()) {
		close(fd_server);
#endif
		ThreadArgs* args = (ThreadArgs*)malloc(sizeof(ThreadArgs));
		args->fd_client = fd_client;
		args->fl_exit = 0;
#ifdef _WIN32
	#ifdef __THREADS 	
		if(CreateThread(NULL, 0, reqest_thread, args, 0, &thread_id) == NULL) {
			// Обробка помилки
			shutdown(fd_client, SHUT_RDWR);
			close(fd_server);
			return 1;
		}
	#else
		reqest_thread((void*)args);
	#endif
#elif __linux__
	#ifdef __THREADS 	
		if(pthread_create(&thread_id, NULL, reqest_thread, args) != 0) {
			// Обробка помилки
			shutdown(fd_client, SHUT_RDWR);
			close(fd_server);
			return 1;
		}
	#else
		reqest_thread((void*)args);
		#ifdef __FORK
		close(fd_client);
		exit(0);
		}
		#endif

	#endif
#endif
	}
#ifdef _WIN32			
	closesocket(fd_server);
	_getch();
	return 0;
#elif __linux__
	shutdown(fd_client, SHUT_RDWR);
	close(fd_server);
	pthread_join(thread_id, NULL);
	exit(0);
#endif
}






