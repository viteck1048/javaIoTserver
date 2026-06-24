#ifndef REQUEST_H
#define REQUEST_H

#pragma once
#include <string>
#include <cstring>
#include <vector>
#include <cstdint>

#define GET 1590
#define HEAD 1591
#define POST 1592
#define PUT 1593
#ifdef DELETE
	#undef DELETE
#endif
#define DELETE 1594
#define PATCH 1595
#define OPTIONS 1596
#define TRACE 1597
#define CONNECT 1598

struct Query {
	std::string param;
	std::string znach;
};

struct Request {
	int method;
	std::string path;
	std::string prot;
	std::string header;
	std::vector<uint8_t> body;
	std::string body_str;
	std::vector<Query> query;
	std::vector<Query> headers;
	enum q_arr{
		QUERY,
		HEADERS
	};
	
	bool my_strcmp(const char* str1, const char* str2) const {
		while(*str1 && *str2) {
			if(*str1 != *str2) {
				return false;
			}
			str1++;
			str2++;
		}
		return *str1 == *str2;
	}

	bool param(const char* pr, q_arr arr = QUERY) const {
		if(arr == QUERY) {
			for(int i = 0; i < query.size(); i++)
				if(my_strcmp(query[i].param.c_str(), pr))
					return true;
		} else {
			for(int i = 0; i < headers.size(); i++)
				if(my_strcmp(headers[i].param.c_str(), pr))
					return true;
		}
		return false;
	}

	bool check_param(const char* pr, const char* zn, q_arr arr = QUERY) const {
		if(arr == QUERY) {
			for(int i = 0; i < query.size(); i++)
				if(my_strcmp(query[i].param.c_str(), pr))
					if(my_strcmp(query[i].znach.c_str(), zn))
						return true;
		} else {
			for(int i = 0; i < headers.size(); i++)
				if(my_strcmp(headers[i].param.c_str(), pr))
					if(my_strcmp(headers[i].znach.c_str(), zn))
						return true;
		}
		return false;
	}

	void cln_query_arr() {
		query.clear();
		headers.clear();
	}

	const char* znach(const char* pr, q_arr arr = QUERY) const {
		if(arr == QUERY) {
			for(int i = 0; i < query.size(); i++)
				if(my_strcmp(query[i].param.c_str(), pr))
					return query[i].znach.c_str();
		} else {
			for(int i = 0; i < headers.size(); i++)
				if(my_strcmp(headers[i].param.c_str(), pr))
					return headers[i].znach.c_str();
		}
		return "";
	}

	void deleteParam(const char* pr, q_arr arr = QUERY) {
		if(arr == QUERY) {
			for(int i = 0; i < query.size(); i++)
				if(my_strcmp(query[i].param.c_str(), pr)) {
					query.erase(query.begin() + i);
					break;
				}
		} else {
			for(int i = 0; i < headers.size(); i++)
				if(my_strcmp(headers[i].param.c_str(), pr)) {
					headers.erase(headers.begin() + i);
					break;
				}
		}
	}

	void push_query(const std::string& param, const std::string& znach) {
		query.push_back({param, znach});
	}

	std::string strToLowerCase(const std::string& str) {
		std::string result = str;
		for (int i = 0; i < result.size(); i++) {
			if (result[i] >= 'A' && result[i] <= 'Z') {
				result[i] += 32;
			}
		}
		return result;
	}

	void push_header(const std::string& param, const std::string& znach) {
		std::string lower_param = strToLowerCase(param);
		std::string trimmed_znach = znach;
		if (trimmed_znach.empty()) {
			return;
		}
		int i = 0, start = 0, len = 0;
		for (; i < trimmed_znach.size(); i++) {
			if (trimmed_znach[i] == ' ') {
				start++;
			}
			else {
				break;
			}
		}
		for (; i < trimmed_znach.size(); i++) {
			if (trimmed_znach[i] != '\r' && trimmed_znach[i] != '\n') {
				len++; 
			}
			else {
				break;
			}
		}
		trimmed_znach = trimmed_znach.substr(start, len);
		headers.push_back({lower_param, trimmed_znach});
	}

	void prnt() const {
		puts("------------------- REQUEST -------------------");
		//printf("%s", header.c_str());
		printf("method - ");
		switch(method) {
			case GET: printf("GET"); break;
			case POST: printf("POST"); break;
			case PUT: printf("PUT"); break;
			case DELETE: printf("DELETE"); break;
			case PATCH: printf("PATCH"); break;
			case HEAD: printf("HEAD"); break;
			case OPTIONS: printf("OPTIONS"); break;
			default: printf("UNKNOWN"); break;
		}
		puts("");

		puts(path.c_str());
		puts(prot.c_str());
		puts("-----------------headers------------------");
		if(headers.size() > 0) {
			for(int i = 0; i < headers.size(); i++)
				printf("%s = %s\n", headers[i].param.c_str(), headers[i].znach.c_str());
		}
		puts("-----------------------------------------------");
		printf("param = %d\n", query.size());
		for(int i = 0; i < query.size(); i++)
			printf("%s = %s\n", query[i].param.c_str(), query[i].znach.c_str());
		puts("-----------------bodyQuery---------------------");
		if(body.size() > 0) {
			//printf("body = %s\n", body.data());
			printf("%s\n", (char*)body.data());
			for(int i = 0; i < (int)body.size(); i++) {
				printf("%02x ", (unsigned char)body[i]);
				if((i + 1) % 16 == 0) {
					puts("");
				}
			}
			puts("");
		} 
		else {
			printf("body.size = %d", body.size());
		}
		puts("\n===============================================\n");
	}
	Request() : method(-1) {}
};

#endif // REQUEST_H