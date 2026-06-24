#ifndef RESPONSE_H
#define RESPONSE_H

#pragma once
#include <string>
#include <cstring>
#include <vector>
#include <cstdint>

struct Response {
	int code;
	std::vector<uint8_t> body;
	std::string headers;
	std::string content_type;
	int content_length;
	
	Response(int code, const std::vector<uint8_t>& body = {}, const std::string& ctype = "text/plain") : code(code), body(body), content_type(ctype) {
		content_length = body.size();
	}

	Response(int code, const std::string& body, const std::string& ctype = "text/plain") : code(code), content_type(ctype) {
		content_length = body.length();
		this->body.assign(body.begin(), body.end());
	}
};

#endif // RESPONSE_H