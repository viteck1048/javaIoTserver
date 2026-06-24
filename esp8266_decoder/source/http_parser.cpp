#include "http_parser.h"
#include "http_utils.h"
#include <cstring>
#include <iostream>

// Parse method string to integer code
int parse_method(const char* method_str) {
	if (strcmp(method_str, "GET") == 0) return GET;
	if (strcmp(method_str, "HEAD") == 0) return HEAD;
	if (strcmp(method_str, "POST") == 0) return POST;
	if (strcmp(method_str, "PUT") == 0) return PUT;
	if (strcmp(method_str, "DELETE") == 0) return DELETE;
	if (strcmp(method_str, "PATCH") == 0) return PATCH;
	if (strcmp(method_str, "OPTIONS") == 0) return OPTIONS;
	if (strcmp(method_str, "TRACE") == 0) return TRACE;
	if (strcmp(method_str, "CONNECT") == 0) return CONNECT;
	return -1;
}

// Parse query string (path?key=value&key2=value2)
void parse_query_string(Request* rq, const char* query_str) {
	if (!query_str || *query_str == '\0') {
		return;
	}
	
	const char* curr = query_str;
	int query_idx = 0;
	
	while (query_idx < 256 && *curr != '\0') {
		// Find key
		const char* key_start = curr;
		const char* key_end = strchr(curr, '=');
		if (!key_end) {
			break;
		}
		
		// Find value
		const char* val_start = key_end + 1;
		const char* val_end = strchr(val_start, '&');
		if (!val_end) {
			val_end = strchr(val_start, '\0');
		}
		
		// Copy key
		int key_len = (int)(key_end - key_start);
		if (key_len > 0) {
			std::string key = convert_string(key_start, key_len);
			std::string val = convert_string(val_start, val_end - val_start);
			rq->push_query(key, val);
		}
		
		if (*val_end == '\0') {
			break;
		}
		curr = val_end + 1;
	}
	
}

// Parse request line: METHOD /path?query HTTP/1.1
bool first_line_pars(Request* rq, std::string line) {
	const char* method_start = line.c_str();
	const char* method_end = strchr(line.c_str(), ' ');
	if (!method_end) return false;
	
	std::string method_str(method_start, method_end - method_start);
	rq->method = parse_method(method_str.c_str());
	if (rq->method == -1) return false;
	
	const char* path_start = method_end + 1;
	const char* path_end = strchr(path_start, ' ');
	if (!path_end) return false;
	
	std::string path_str(path_start, path_end - path_start);
	
	// Split path and query
	const char* query_pos = strchr(path_str.c_str(), '?');
	if (query_pos) {
		rq->path = std::string(path_str.c_str(), query_pos - path_str.c_str());
		parse_query_string(rq, query_pos + 1);
	} else {
		rq->path = path_str;
	}
	rq->path = convert_string(rq->path.c_str(), rq->path.size());
		
	const char* proto_start = path_end + 1;
	const char* proto_end = strchr(proto_start, '\r');
	if (!proto_end) {
		proto_end = strchr(proto_start, '\n');
	}
	if (proto_end) {
		rq->prot = std::string(proto_start, proto_end - proto_start);
	}
	
	int x = rq->path.find("/..");
	if (x != std::string::npos) { // security check for path traversal
		rq->path = rq->path.substr(0, x);
		return false;
	}
	
	return true;
}

int read_http_body(socket_t client_fd, Request* rq) {
	std::string len_ctr = rq->znach("content-length", Request::HEADERS);
	if (len_ctr.empty()) {
		return 0;
	}
	int len = atoi(len_ctr.c_str());
	if (len == 0) {
		return 0;
	}
	if (len < 0 || len > 1024 * 1024) { // 1MB max
		return -1;
	}
	std::vector<uint8_t> result(len);
	_resv(client_fd, result.data(), len);
	rq->body = result;
	for (int i = 0; i < result.size(); i++) {
		if (result[i] >= 32 && result[i] <= 127) {
			rq->body_str += result[i];
		}
		else
			break;
	}
	return result.size();
}

bool line_parse(Request* rq, const std::string& line) {
	// Parse header line: Key: value
	int colon = line.find(':');
	if (colon == std::string::npos) {
		return false;
	}
	
	std::string key = line.substr(0, colon);
	std::string val = line.substr(colon + 1);
	
	rq->push_header(key, val);
	return true;
}

// Read all HTTP data from socket
std::string read_http_headers(socket_t client_fd, Request* rq) {
	std::string result = "";
	bool first_line = true;
	while (true) {
		std::string line = _resv_line(client_fd);
		result += line;
		if (first_line) {
			first_line = false;
			first_line_pars(rq, line);
			continue;
		}
		if (line == "\r\n" || line == "\n") {
			break;
		}
		line_parse(rq, line);
	}
	rq->header = result;
	return result;
}

Request* parse_http_request(socket_t client_fd) {
	
	Request* rq = new Request();
	read_http_headers(client_fd, rq);
	read_http_body(client_fd, rq);

	if (rq->check_param("content-type", "application/x-www-form-urlencoded", Request::HEADERS)) {
		parse_query_string(rq, rq->body_str.c_str());
	}
	
	//rq->prnt();
	return rq;
}
