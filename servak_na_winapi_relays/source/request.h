#pragma once
#include <string>
#include <cstring>
#include <vector>
#include <cstdint>

struct Query {
	std::string param;
	std::string znach;
	bool aa;
	Query() : aa(0) {}
	Query& operator=(const Query& other) {
		param = other.param;
		znach = other.znach;
		aa = other.aa;
		return *this;
	}
};

struct Request {
	int method;
	std::string path;
	std::string accept_language;
	std::string host;
	std::string user_agent;
	std::string prot;
	std::string header;
	std::vector<uint8_t> body;
	Query query[256];
	int query_len;
	bool f_path, f_accept_language, f_host, f_user_agent, f_prot;
	bool param(const char* pr) const {
		for(int i = 0; i < query_len; i++)
			if(!strcmp(query[i].param.c_str(), pr))
				return true;
		return false;
	}
	bool check_param(const char* pr, const char* zn) const {
		for(int i = 0; i < query_len; i++)
			if(!strcmp(query[i].param.c_str(), pr))
				if(!strcmp(query[i].znach.c_str(), zn))
					return true;
		return false;
	}
	void cln_query_arr() {
		for(int i = 0; i < 256; i++)
			query[i].aa = 0;
		query_len = 0;
	}
	const char* znach(const char* pr) const {
		for(int i = 0; i < query_len; i++)
			if(!strcmp(query[i].param.c_str(), pr))
				return query[i].znach.c_str();
		return nullptr;
	}
	void deleteParam(const char* pr) {
		for(int i = 0; i < query_len; i++)
			if(!strcmp(query[i].param.c_str(), pr)) {
				for(int j = i; j < query_len - 1; j++)
					query[j] = query[j + 1];
				query_len--;
				break;
			}
	}
	void prnt() const {
		puts("------------------- REQUEST -------------------");
		puts(header.c_str());
		puts("-----------------------------------------------");
		printf("param = %d\n", query_len);
		for(int i = 0; i < query_len; i++)
			printf("%s = %s\n", query[i].param.c_str(), query[i].znach.c_str());
		puts("-----------------------------------------------");
		if(body.size() > 0) {
			//printf("body = %s\n", body.data());
			for(int i = 0; i < (int)body.size(); i++)
				printf("%c", (char)body[i]);
		}
		puts("-----------------------------------------------");
	}
	Request() : method(-1), query_len(0), f_path(0), f_accept_language(0), f_host(0), f_user_agent(0), f_prot(0) {}
};
