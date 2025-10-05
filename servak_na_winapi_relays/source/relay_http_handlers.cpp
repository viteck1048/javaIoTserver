#include "relay_http_handlers.h"
#include "relay.h"
#include "http_utils.h"
#include "platform.h"
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

int obrobka_get(socket_t fd_client, Request* rq, bool head) {
	std::string bb;
	int typ = 0;
	int fl_content = 0;
	std::string typstr;
	if (!strcmp(rq->path.c_str(), "/get_status") && rq->param("userID")) {
		bb = rr_get_status(atoi(rq->znach("userID")));
		typ = 3;
		fl_content = 1;
	} else if (!strcmp(rq->path.c_str(), "/get_knopky") && rq->param("userID")) {
		// Root path: serve relay HTML
		bb = get_knopky(atoi(rq->znach("userID")));
		typ = 1;
		fl_content = 1;
	} else if (!strcmp(rq->path.c_str(), "/get_gadgets") && rq->param("userID")) {
		// Root path: serve relay HTML
		bb = get_gadgets(atoi(rq->znach("userID")));
		typ = 1;
		fl_content = 1;
	} else if (!strcmp(rq->path.c_str(), "/get_relays") && rq->param("userID") && rq->param("sn")) {
		bb = get_relays(atoi(rq->znach("userID")), atoi(rq->znach("sn")));
		typ = 3;
		fl_content = 1;
	} else if (!strcmp(rq->path.c_str(), "/log_file") && rq->param("userID") && rq->param("sn")) {
		std::vector<uint8_t> bbb = send_for_user_log_file(atoi(rq->znach("sn")), atoi(rq->znach("userID")));
		typ = 1;
		fl_content = 1;
		int length = (int)bbb.size();
		send_kap(fd_client, 200, length, typ, typstr.c_str());
		if (!head) {
			return _send(fd_client, bbb.data(), length);
		}
		return 0;
	} else {
		// --- Static file serving from www ---
		std::string path = rq->path;
		if (path.find("/..") != std::string::npos) {
			send_err(fd_client, 403); // Forbidden
			return 1;
		}
		if (path.empty() || path == "/") path = "/knopky.html";
		std::string file_path = "www" + path;
		std::ifstream file(file_path, std::ios::binary);
		if (!file) {
			printf("File not found: %s response 404\n", file_path.c_str());
			rq->prnt();
			send_err(fd_client, 404);
			return 1;
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
		std::string content;
		content.resize(length);
		file.read(&content[0], length);
		send_kap(fd_client, 200, length, 0, content_type.c_str());
		if (!head) {
			_send(fd_client, content.c_str(), length);
		}
		return 0;
	}
	std::stringstream buff;
	if (fl_content == 1)
		buff << bb;
	buff.seekg(0, std::ios::end);
	int length = (int)buff.tellg();
	buff.seekg(0, std::ios::beg);
	send_kap(fd_client, 200, length, typ, typstr.c_str());
	if (!head) {
		return _send(fd_client, buff.str().c_str(), length);
	}
	return 0;
}

int obrobka_post(socket_t fd_client, Request* rq) {
	std::stringstream buff;
    /* Handle device status check
    if (!strcmp(rq->path.c_str(), "/device_status") && rq->param("sn")) {
        buff << handle_device_status(fd_client, rq);
  //      return 0;
    }
    // Handle device registration
    else if (!strcmp(rq->path.c_str(), "/register_device")) {
        handle_register_device(fd_client, rq);
   //     return 0;
    }*/
	if(!strcmp(rq->path.c_str(), "/update_state")) {
		// Get the pin parameter if it exists
		const char* pin_txt = rq->param("pin") ? rq->znach("pin") : nullptr;
		buff << obrobnyk_relay(rq->znach("sn"), rq->znach("acp"), rq->znach("relays"), rq->znach("init"), rq->znach("cs"), pin_txt);
	}

	else if(!strcmp(rq->path.c_str(), "/log_file")) {
		save_log_file(rq->body);
		buff << "OK";
	}
	else if(!strcmp(rq->path.c_str(), "/set_relay_name"))
		buff << set_relay_name(atoi(rq->znach("sn")), rq->znach("name"), atoi(rq->znach("userID")));
	
	else if(!strcmp(rq->path.c_str(), "/set_relay_names"))
		buff << set_relay_names(atoi(rq->znach("sn")), rq->znach("relay1"), rq->znach("relay2"), rq->znach("relay3"), rq->znach("relay4"), rq->znach("relay5"), rq->znach("relay6"), rq->znach("relay7"), rq->znach("relay8"), atoi(rq->znach("userID")));
	
	else {
		rq->prnt();
		send_err(fd_client, 400);
		return 1;
	}
	buff.seekg(0, std::ios::end);
	int length = (int)buff.tellg();
	buff.seekg(0, std::ios::beg);
	if(rq->query_len == 5 && !strcmp(rq->path.c_str(), "/update_state"))
		send_kap(fd_client, 200, length, 3, "", get_get_log_file_flag(atoi(rq->znach("sn"))));
	else
		send_kap(fd_client, 200, length, 3);
	return _send(fd_client, buff.str().c_str(), length);
}
/*
// Check if a device exists and is online
void handle_device_status(socket_t fd_client, const Request* rq) {
    if (!rq->param("sn")) {
        std::string error = "{\"error\":\"Missing serial number\"}";
        send_kap(fd_client, 400, error.length(), 3);
        _send(fd_client, error.c_str(), error.length());
        return;
    }

    const char* sn_str = rq->znach("sn");
    // Validate SN is a positive number
    if (!sn_str || *sn_str == '\0' || !std::all_of(sn_str, sn_str + strlen(sn_str), ::isdigit) || atoi(sn_str) <= 0) {
        std::string error = "{\"error\":\"Invalid serial number format\"}";
        send_kap(fd_client, 400, error.length(), 3);
        _send(fd_client, error.c_str(), error.length());
        return;
    }

    // Convert to unsigned long int to allow full range
    unsigned long sn = strtoul(sn_str, nullptr, 10);
    if (sn == 0 || sn == ULONG_MAX) {
        std::string error = "{\"error\":\"Invalid serial number\"}";
        send_kap(fd_client, 400, error.length(), 3);
        _send(fd_client, error.c_str(), error.length());
        return;
    }

    Rele* device = find_rele(static_cast<int>(sn));
    
    std::stringstream response;
    response << "{\"found\":" << (device ? "true" : "false") 
             << ",\"online\":" << ((device && (time(nullptr) - device->time_onl) <= 15) ? "true" : "false")
             << "}";
    
    std::string response_str = response.str();
    send_kap(fd_client, 200, response_str.length(), 3);
    _send(fd_client, response_str.c_str(), response_str.length());
}

// Helper function to validate PIN format
bool is_valid_pin(const char* pin_str) {
    if (!pin_str || strlen(pin_str) != 4) {
        return false;
    }
    
    // Check if all characters are digits
    for (int i = 0; i < 4; i++) {
        if (!isdigit(pin_str[i])) {
            return false;
        }
    }
    
    // Check if PIN is not 0000
    return strcmp(pin_str, "0000") != 0;
}

// Handle device registration with PIN
void handle_register_device(socket_t fd_client, const Request* rq) {
    // Check required parameters
    if (!rq->param("sn") || !rq->param("pin") || !rq->param("userID")) {
        std::string error = "{\"success\":false,\"message\":\"Missing required parameters\"}";
        send_kap(fd_client, 400, error.length(), 3);
        _send(fd_client, error.c_str(), error.length());
        return;
    }
    
    // Validate SN format
    const char* sn_str = rq->znach("sn");
    if (!sn_str || *sn_str == '\0' || !std::all_of(sn_str, sn_str + strlen(sn_str), ::isdigit)) {
        std::string error = "{\"success\":false,\"message\":\"Невірний формат серійного номера\"}";
        send_kap(fd_client, 400, error.length(), 3);
        _send(fd_client, error.c_str(), error.length());
        return;
    }
    
    // Convert to unsigned long int to allow full range
    unsigned long sn = strtoul(sn_str, nullptr, 10);
    if (sn == 0 || sn == ULONG_MAX) {
        std::string error = "{\"success\":false,\"message\":\"Невірний серійний номер\"}";
        send_kap(fd_client, 400, error.length(), 3);
        _send(fd_client, error.c_str(), error.length());
        return;
    }
    
    // Validate PIN format
    const char* pin_str = rq->znach("pin");
    if (!is_valid_pin(pin_str)) {
        std::string error = "{\"success\":false,\"message\":\"Невірний формат PIN-коду\"}";
        send_kap(fd_client, 400, error.length(), 3);
        _send(fd_client, error.c_str(), error.length());
        return;
    }

    int sn_int = static_cast<int>(sn);  // Use the already converted sn from above
    unsigned short pin = static_cast<unsigned short>(atoi(pin_str));
    int userID = atoi(rq->znach("userID"));
    Rele* device = find_rele(sn_int);
    
    std::stringstream response;
    
    if (!device || device->pin != pin) {
        // Device not found or PIN doesn't match
        response << "{\"success\":false,\"message\":\"Невірний серійний номер або PIN-код\"}";
    } else {
        // Device found and PIN matches, proceed with registration
        char sql[512];
        
        // Check if device exists in relays table
        sprintf(sql, "SELECT r_id FROM relays WHERE sn=%d;", sn_int);
        int r_id = g_db.get_int(sql);
        
        if (r_id == -1) {
            // Insert new device into relays table
            sprintf(sql, 
                "INSERT INTO relays (sn, r1, r2, r3, r4, r5, r6, r7, r8, gadget_name) "
                "VALUES (%d, '', '', '', '', '', '', '', '', '');", 
                sn_int
            );
            if (!g_db.insert(sql)) {
                response << "{\"success\":false,\"message\":\"Помилка створення запису пристрою\"}";
                
                std::string response_str = response.str();
                send_kap(fd_client, 200, response_str.length(), 3);
                _send(fd_client, response_str.c_str(), response_str.length());
                return;
            }
            
            // Get the newly inserted r_id
            sprintf(sql, "SELECT r_id FROM relays WHERE sn=%d;", sn_int);
            r_id = g_db.get_int(sql);
        }
        
        if (r_id != -1) {
            // Check if this user already has this device
            sprintf(sql, 
                "SELECT z_id FROM user_relays WHERE userID=%d AND r_id=%d;", 
                userID, r_id
            );
            int existing = g_db.get_int(sql);
            
            if (existing == -1) {
                // Insert into user_relays table
                sprintf(sql, 
                    "INSERT INTO user_relays (userID, r_id) VALUES (%d, %d);", 
                    userID, r_id
                );
                if (g_db.insert(sql)) {
                    response << "{\"success\":true,\"message\":\"Пристрій успішно зареєстровано\"}";
                } else {
                    response << "{\"success\":false,\"message\":\"Помилка реєстрації пристрою\"}";
                }
            } else {
                response << "{\"success\":false,\"message\":\"Цей пристрій вже зареєстрований у вашому обліковому записі\"}";
            }
        } else {
            response << "{\"success\":false,\"message\":\"Помилка отримання ID пристрою\"}";
        }
    }
    
    std::string response_str = response.str();
    send_kap(fd_client, 200, response_str.length(), 3);
    _send(fd_client, response_str.c_str(), response_str.length());
}
*/
int obrobka_put(socket_t fd_client, Request* rq) {
	std::stringstream buff;
	if (!strcmp(rq->path.c_str(), "/put_status") && rq->param("sn") && rq->param("button")) {
		buff << rr_put_status(atoi(rq->znach("sn")), atoi(rq->znach("button")), atoi(rq->znach("userID")));
	} else {
		rq->prnt();
		send_err(fd_client, 400);
		return 1;
	}
	buff.seekg(0, std::ios::end);
	int length = (int)buff.tellg();
	buff.seekg(0, std::ios::beg);
	send_kap(fd_client, 200, length, 3);
	return _send(fd_client, buff.str().c_str(), length);
}

int obrobka_delete(socket_t fd_client, Request* rq) {
	std::stringstream buff;
	if (false) {
		buff << "test_string";
	} else {
		rq->prnt();
		send_err(fd_client, 400);
		return 1;
	}
	buff.seekg(0, std::ios::end);
	int length = (int)buff.tellg();
	buff.seekg(0, std::ios::beg);
	send_kap(fd_client, 200, length, 3);
	return _send(fd_client, buff.str().c_str(), length);
}
