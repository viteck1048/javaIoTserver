#define ZAPYTIV_BEZ_ZATRYMKY 50

#pragma warning(disable : 4996)

bool hide_log;

#include <vector>
#include <algorithm>
#include <cstring>
#include <ctime>
#include <sstream>
#include <cstdio>
#include <iostream>
#include <cstdint>
#include <ctime>

// Cross-platform includes
#ifndef _WIN32
#include <unistd.h>
#include <termios.h>
#endif
//#include <thread>
//#include <chrono>
#include "sqlite_helper.h"
#include "def_rand.h"

#ifndef DEF_RAND
#define DEF_RAND 0x12345678
#endif

class MyRand {
	uint32_t a;
	public:
		MyRand() { a = DEF_RAND; }
		uint32_t rand() {
			uint32_t b = a;
			b ^= b << 13;
			b ^= b >> 17;
			b ^= b << 5;
			return a = b;
		}
		void srand(uint32_t i) { a = i ^ DEF_RAND; }
		void correct(uint32_t i) { a = i ^ a; }
};

static SqliteHelper g_db("relay_data.db");

struct KnopkaInfo {
    int sn;
    int r_idx; // 1..8
    std::string text;
};

struct Rele {
	char rr_status[9];
	char rr_command[9];
	int acp_status;
	int sn;
	int time_onl;
	bool put;
	int online;
	bool get_log_file_flag;
	int userID_set_GFF;
	time_t time_set_GFF;
	std::string log_file;
	unsigned short pin;
	MyRand my_rand;
	Rele() { 
		sn = 0; 
		online = 0; 
		put = 0; 
		rr_status[0]=0; 
		rr_command[0]=0; 
		acp_status=0; 
		time_onl=0; 
		userID_set_GFF = 0;
		get_log_file_flag = false;
		time_set_GFF = 0;
		log_file = "";
		pin = 0;
	}
};

static std::vector<Rele> rele_list;

static void init_db_tables() {
    static bool done = false;
    if (!done) {
        g_db.init_tables();
        done = true;
    }
}

bool validate_user(int userID, int sn) {
	// Перевірка на діапазон значень
	if (userID <= 0 || sn <= 0) {
		return false;
	}

	// Використання параметризованого запиту для безпеки
	const char* sql = "SELECT relays.sn FROM user_relays JOIN relays ON user_relays.r_id = relays.r_id WHERE user_relays.userID=? AND relays.sn=?";
	sqlite3_stmt* stmt;
	int rc = sqlite3_prepare_v2(g_db.db, sql, -1, &stmt, 0);
	if (rc != SQLITE_OK) {
		printf("SQL error: %s\n", sqlite3_errmsg(g_db.db));
		return false;
	}

	// Бінд параметрів
	sqlite3_bind_int(stmt, 1, userID);
	sqlite3_bind_int(stmt, 2, sn);

	// Виконання запиту
	if (sqlite3_step(stmt) == SQLITE_ROW) {
		sqlite3_finalize(stmt);
		return true;
	}

	sqlite3_finalize(stmt);
	return false;
}

static Rele* find_rele(int sn) {
	for (size_t i = 0; i < rele_list.size(); ++i) {
		if (rele_list[i].sn == sn)
			return &rele_list[i];
	}
	return NULL;
}

std::vector<KnopkaInfo> get_knopky_list(int userID) {
    std::vector<KnopkaInfo> res;
    char sql[256];
    sprintf(sql, "SELECT r_id FROM user_relays WHERE userID=%d;", userID);
    sqlite3_stmt* stmt;
    if (sqlite3_prepare_v2(g_db.db, sql, -1, &stmt, 0) == SQLITE_OK) {
        while (sqlite3_step(stmt) == SQLITE_ROW) {
            int r_id = sqlite3_column_int(stmt, 0);
            char sql2[256];
            sprintf(sql2, "SELECT sn, r1, r2, r3, r4, r5, r6, r7, r8 FROM relays WHERE r_id=%d;", r_id);
            sqlite3_stmt* stmt2;
            if (sqlite3_prepare_v2(g_db.db, sql2, -1, &stmt2, 0) == SQLITE_OK) {
                if (sqlite3_step(stmt2) == SQLITE_ROW) {
                    int sn = sqlite3_column_int(stmt2, 0);
                    for (int i = 1; i <= 8; ++i) {
                        const unsigned char* txt = sqlite3_column_text(stmt2, i);
                        if (txt && txt[0]) {
                            KnopkaInfo ki;
                            ki.sn = sn;
                            ki.r_idx = i;
                            ki.text = reinterpret_cast<const char*>(txt);
                            res.push_back(ki);
                        }
                    }
                    // Додаємо sn у rele_list, якщо його там ще немає
                    if (!find_rele(sn)) {
                        Rele r;
                        r.sn = sn;
						r.acp_status = -4095;
						r.time_onl = (int)time(NULL);
						r.put = false;
						r.online = 0;
						r.get_log_file_flag = 0;
						strcpy(r.rr_status, "XXXXXXXX");
						strcpy(r.rr_command, "XXXXXXXX");
						r.log_file = "";
						r.userID_set_GFF = 0;
						r.time_set_GFF = 0;
						//r.my_rand.srand(sn);
                        rele_list.push_back(r);
                    }
                }
                sqlite3_finalize(stmt2);
            }
        }
        sqlite3_finalize(stmt);
    }
    return res;
}

std::string get_relays(int userID, int sn) {
	std::stringstream html;
	if (!validate_user(userID, sn)) {
		printf("get_relays userID = %d, sn = %d\tvalidate_user = false\n", userID, sn);
		return "";
	}
	printf("get_relays userID = %d, sn = %d\n", userID, sn);
	
	// Отримуємо інформацію про реле
	std::string sql = "SELECT relays.sn, relays.gadget_name, relays.r1, relays.r2, relays.r3, relays.r4, relays.r5, relays.r6, relays.r7, relays.r8 FROM relays JOIN user_relays ON relays.r_id = user_relays.r_id WHERE user_relays.userID = ? AND relays.sn = ?";
	
	sqlite3_stmt* stmt;
	if (sqlite3_prepare_v2(g_db.db, sql.c_str(), -1, &stmt, 0) == SQLITE_OK)
	{
		sqlite3_bind_int(stmt, 1, userID);
		sqlite3_bind_int(stmt, 2, sn);
		
		int rc = sqlite3_step(stmt);
		
		if (rc == SQLITE_ROW) {
			// Формуємо JSON відповідь
			int sn_val = sqlite3_column_int(stmt, 0);
			const char* gadget_name = (const char*)sqlite3_column_text(stmt, 1);
			
			
			html << "{\"sn\":\"" << sn_val << "\",\"gadget_name\":\"";
			html << (gadget_name ? gadget_name : "") << "\",\"relays\":[";
			
			// Додаємо імена релейних виходів
			for (int i = 2; i < 10; i++) {
				const char* relay_name = (const char*)sqlite3_column_text(stmt, i);
				if (i > 2) html << ",";
				html << "{\"name\":\"" << (relay_name ? relay_name : "") << "\"}";
			}
			html << "]}";
			
		}
		else {
			printf("No row found or error: %s\n", sqlite3_errmsg(g_db.db));
		}
		
		sqlite3_finalize(stmt);
	}
	
	return html.str();
}

std::string get_gadgets(int userID) {
	std::stringstream html;
	html << "<div class=\"container\">";
	
	// Отримуємо всі реле користувача
	std::string sql = "SELECT relays.sn, relays.gadget_name FROM relays JOIN user_relays ON relays.r_id = user_relays.r_id WHERE user_relays.userID = ?";
	
	sqlite3_stmt* stmt;
	if (sqlite3_prepare_v2(g_db.db, sql.c_str(), -1, &stmt, 0) == SQLITE_OK)
	{
		sqlite3_bind_int(stmt, 1, userID);
		
		while (sqlite3_step(stmt) == SQLITE_ROW)
		{
			int sn = sqlite3_column_int(stmt, 0);
			const char* gadget_name = (const char*)sqlite3_column_text(stmt, 1);
			
			// Якщо gadget_name порожній, використовуємо sn
			std::string display_text = gadget_name && strlen(gadget_name) > 0 ? gadget_name : std::to_string(sn);
			
			html << "<div class=\"knopka\">";
			html << "<a href=\"edit_relay_names.html?sn=" << sn << "\" class=\"knopka\">";
			html << display_text;
			html << "</a>";
			html << "</div>";
		}
		
		sqlite3_finalize(stmt);
	}
	
	html << "</div>";
	return html.str();
}

std::string get_knopky(int userID) {
	printf("get_knopky userID = %d\n", userID);
	std::stringstream html;
    std::vector<KnopkaInfo> lst = get_knopky_list(userID);
    for (const auto& k : lst) {
        html << "<button class=\"button\" b_id=\""<< k.r_idx << "\" sn=\"" << k.sn << "\">" << k.text << "</button>\n";
	}
	return html.str();
}

void clear_flags_and_log_file(Rele* rr) {
	if (rr) {
		printf("clear_flags_and_log_file sn = %d\n", rr->sn);
		rr->get_log_file_flag = false;
		rr->userID_set_GFF = 0;
		rr->log_file.clear();
	}
}

std::string save_log_file(std::vector<uint8_t> log_file) {
	//Gadget serial number: " + ssn + "\n"
	if(log_file.size() > 40) {
		std::string ssn;
		for(int i = 21; i < 40; i++)
			ssn += (char)log_file[i];
		int sn = atoi(ssn.c_str());
		
		Rele* rr = find_rele(sn);
		if (rr && rr->userID_set_GFF != 0) {
			for(int i = 0; i < (int)log_file.size(); i++) {
				rr->log_file.push_back(log_file[i]);
			}
			printf("Received & saved log_file, size: %d bytes, sn = %d\n", (int)log_file.size(), sn);
		}
		else {
			printf("Received log_file escaped, sn = %d, userID_set_GFF = 0\n", sn);
		}
	}
	return "";
}

std::vector<uint8_t> send_for_user_log_file(int sn, int userID) {
	if(!validate_user(userID, sn))
		return std::vector<uint8_t>();
	Rele* rr = find_rele(sn);
	if(rr) {
		if(rr->log_file.size() > 0) {
			if(userID != rr->userID_set_GFF)
				return std::vector<uint8_t>();
			std::vector<uint8_t> res;
			// Додаємо HTML заголовок
			const char* html_header = "<!DOCTYPE html>\n"
				"<html lang=\"en\">\n"
				"<head>\n"
				"    <meta charset=\"UTF-8\">\n"
				"    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
				"    <title>Log File</title>\n"
				"    <style>\n"
				"        body {\n"
				"            background-color: #E6F0F3;\n"
				"            font-family: monospace;\n"
				"            margin: 20px;\n"
				"            max-width: 1000px;\n"
				"            margin: 0 auto;\n"
				"        }\n"
				"        pre {\n"
				"            background-color: #E6F0F3;\n"
				"            padding: 20px;\n"
				"            border-radius: 5px;\n"
				"            white-space: pre-wrap;\n"
				"            word-wrap: break-word;\n"
				"        }\n"
				"    </style>\n"
				"</head>\n"
				"<body>\n"
				"    <pre>";
			
			// Додаємо HTML заголовок
			for (const char* p = html_header; *p; p++) {
				res.push_back(*p);
			}
			
			// Додаємо лог файл
			for(uint8_t byte : rr->log_file) {
				res.push_back(byte);
			}
			
			// Додаємо HTML закінчення
			const char* html_footer = "</pre>\n</body>\n</html>";
			for (const char* p = html_footer; *p; p++) {
				res.push_back(*p);
			}
			
			printf("send_for_user_log_file size: %d bytes\n", (int)res.size());
			rr->log_file.clear();
			rr->userID_set_GFF = 0;
			rr->time_set_GFF = 0;
			return res;
		}
	}
	return std::vector<uint8_t>();
}

void set_get_log_file_flag(int sn, int userID, bool flag) {
	if (!validate_user(userID, sn)) {
		return;
	}
	Rele* rr = find_rele(sn);
	if (rr) {
		if(flag) {
			if(rr->userID_set_GFF == 0) {
				printf("set_get_log_file_flag sn = %d, userID = %d, flag = %d\n", sn, userID, flag);
				rr->get_log_file_flag = flag;
				rr->userID_set_GFF = userID;
				rr->time_set_GFF = (int)time(NULL);
			}
		}
		else {
			if(rr->userID_set_GFF == userID) {
				printf("set_get_log_file_flag sn = %d, userID = %d, flag = %d\n", sn, userID, flag);
				rr->get_log_file_flag = flag;
				rr->userID_set_GFF = 0;
				rr->time_set_GFF = 0;
			}
		}
	}
}

bool get_get_log_file_flag(int sn) {
	Rele* rr = find_rele(sn);
	bool res = false;
	if (rr) {
		res = rr->get_log_file_flag;
		rr->get_log_file_flag = false;
	}
	return res;
}

std::string obrobnyk_relay(const char* sn_txt, const char* acp_txt, const char* relays, const char* init_txt, const char* cs_txt, const char* pin_txt = nullptr)
{
	// Перевірка довжини параметрів
	if (!sn_txt || !acp_txt || !relays || !init_txt || !cs_txt) {
		printf("obrobnyk_relay, invalid parameters nullptr\n");
		return "{\"relays\":\"XXXXXXXX\",\"online\":\"0\",\"cs\":\"0\"}";
	}
	if (strlen(sn_txt) != 10 || strlen(relays) != 8 || strlen(acp_txt) != 10 || strlen(init_txt) != 4 || strlen(cs_txt) < 2) {
		printf("obrobnyk_relay, invalid parameters strlen\n");
		return "{\"relays\":\"XXXXXXXX\",\"online\":\"0\",\"cs\":\"0\"}";
	}
	int cs = atoi(cs_txt);
	int sn = atoi(sn_txt);
	unsigned short pin = pin_txt ? (unsigned short)atoi(pin_txt) : 0;
	bool init = strcmp(init_txt, "true") == 0 ? true : false;
	std::stringstream buff;
	bool f_create_new_struct = 0;
	int contr_sum = 0, contr_sum2 = 0;
	char bbb[200];
	int bbbi = 0;
	bbbi = sprintf(bbb, "sn = %s Firmware v.%4.4s resv = %s send = ", sn_txt, acp_txt, relays);
	Rele* rr = find_rele(sn);
	int t_time = (int)time(NULL);
	int br_rr_all = (int)rele_list.size(), br_rr_onl = 0;
	for (size_t i = 0; i < rele_list.size(); ++i) {
		if ((t_time - rele_list[i].time_onl) <= 15) 
			br_rr_onl++;
		else {
			rele_list[i].acp_status = -4095;
		}
	}
    if (!rr) {
        init_db_tables();
        // Чи є relay з цим sn у БД?
        char sql[256];
        sprintf(sql, "SELECT r_id FROM relays WHERE sn=%d;", sn);
        int r_id = g_db.get_int(sql);
        if (r_id == -1) {
            // Додаємо relay
            sprintf(sql, "INSERT INTO relays (sn, r1, r2, r3, r4, r5, r6, r7, r8, gadget_name) VALUES (%d, '', '', '', '', '', '', '', '', '');", sn);
            g_db.insert(sql);
        }
        // Додаємо в оперативну памʼять
        Rele r;
        r.sn = sn;
        r.acp_status = 0;
        strcpy(r.rr_status, relays);
        strcpy(r.rr_command, "XXXXXXXX");
        r.online = 0;
        r.get_log_file_flag = 0;
        f_create_new_struct = 1;
		r.time_set_GFF = 0;
        r.time_onl = t_time;
        r.my_rand.srand(sn);
        r.put = 0;
        r.pin = pin;  // Save the PIN from the request
        rele_list.push_back(r);
		r.log_file = "";
		r.userID_set_GFF = 0;
        rr = &rele_list.back();
    }
    else {
        if(rr->put == 0 && strcmp(relays, "XXXXXXXX"))
            strcpy(rr->rr_status, relays);
        rr->acp_status = 0;
        if(!init)
            rr->time_onl = t_time;
        if(init) {
            rr->my_rand.srand(sn);
        }
        
        // Update PIN if provided in the request
        if (pin_txt) {
            rr->pin = pin;
        }
        else if(!strcmp(relays, "XXXXXXXX")) {
            strcpy(rr->rr_command, rr->rr_status);
            rr->put = 1;
        }
        if(rr->time_set_GFF > 0) {
            if((t_time - rr->time_set_GFF) > 120) {
                rr->time_set_GFF = 0;
                clear_flags_and_log_file(rr);
            }
        }
	}
	bbbi += sprintf(bbb + bbbi, "%s", rr->rr_command);
	bbbi += sprintf(bbb + bbbi, " init = %s cs = %d", init_txt, cs);
	int ii = strlen(sn_txt);
	for(int iii = 0; iii < ii; iii++)
		contr_sum += (int)((unsigned char)sn_txt[iii] ^ (unsigned char)(rr->my_rand.rand() & (int)0xff));
	ii = strlen(relays);
	for(int iii = 0; iii < ii; iii++)
		contr_sum += (int)((unsigned char)relays[iii] ^ (unsigned char)(rr->my_rand.rand() & (int)0xff));
	ii = strlen(acp_txt);
	for(int iii = 0; iii < ii; iii++)
		contr_sum += (int)((unsigned char)acp_txt[iii] ^ (unsigned char)(rr->my_rand.rand() & (int)0xff));
	ii = strlen(init_txt);
	for(int iii = 0; iii < ii; iii++)
		contr_sum += (int)((unsigned char)init_txt[iii] ^ (unsigned char)(rr->my_rand.rand() & (int)0xff));
	if(cs != contr_sum) {
		init = 1;
		bbbi += sprintf(bbb + bbbi, " cs = %d contr = %d", cs, contr_sum);
		buff << "{\"relays\":\"XXXXXXXX\",\"online\":\"0\",\"cs\":\"0\"}";
		puts(bbb);
		return buff.str();
	}
	rr->my_rand.correct(contr_sum);
	char buff_cs[50];
	buff << "{\"relays\":\"";
	if(init || !(rr->put)) {
		strcpy(rr->rr_command, "XXXXXXXX");
		buff << "XXXXXXXX";
	}
	else {
		buff << rr->rr_command;
	}
	sprintf(buff_cs, "%s%d", ((init || !(rr->put)) ? "XXXXXXXX" : rr->rr_command), rr->online);
	 ii = strlen(buff_cs);
	bbbi += sprintf(bbb + bbbi, " %s", buff_cs);
	for(int iii = 0; iii < ii; iii++) {
		contr_sum2 += (int)((unsigned char)buff_cs[iii] ^ (unsigned char)(rr->my_rand.rand() & (int)0xff));
	}
	buff << "\",\"online\":\"" << rr->online << "\",\"cs\":\"" << contr_sum2 << "\"}";
	bbbi += sprintf(bbb + bbbi, " cs2 = %d", contr_sum2);
	rr->my_rand.correct(contr_sum2);
	if(!init) {
		rr->put = 0;
		strcpy(rr->rr_command, "XXXXXXXX");
	}
	rr->online = 0;
	if(f_create_new_struct)
		bbbi += sprintf(bbb + bbbi, " create new struct");
	if(init || !hide_log)
		puts(bbb);
	else
		printf("%d relays all %d relays online", br_rr_all, br_rr_onl);
	fflush(stdout);
	return buff.str();
}

std::string rr_get_status(int userID)
{
	printf("rr_get_status json userID = %d\n", userID);
	std::stringstream buff;
	buff << "{\"buttons\":\"";
	std::vector<KnopkaInfo> lst = get_knopky_list(userID);
	for (const auto& k : lst) {
		Rele* rr = find_rele(k.sn);
		char st = 'X';
		if (rr && k.r_idx >= 1 && k.r_idx <= 8) {
			if(rr->acp_status != -4095)
				st = rr->rr_status[k.r_idx - 1];
			else
				st = 'X';
		}
		else
			st = 'X';
		buff << st;
		rr->online = ZAPYTIV_BEZ_ZATRYMKY;
	}
	buff << "\"}";
	return buff.str();
}

std::string rr_put_status(int sn, int button, int userID) {
	if (!validate_user(userID, sn)) {
		printf("rr_put_status json button = %d\tsn = %d\tuserID = %d\tvalidate_user = false\n", button, sn, userID);
		return "";
	}
    printf("rr_put_status json button = %d\tsn = %d\tuserID = %d\n", button, sn, userID); // Змінити стан реле, якщо знайдено
    button--; // 1-based to 0-based
    Rele* rr = find_rele(sn);
    if (rr && button >= 0 && button < 8 && rr->acp_status != -4095) {
        if (rr->rr_status[button] == '0') {
            rr->rr_status[button] = 'X';
            rr->rr_command[button] = '1';
        } else if (rr->rr_status[button] == '1') {
            rr->rr_status[button] = 'X';
            rr->rr_command[button] = '0';
        } else {
            rr->rr_status[button] = 'X';
            rr->rr_command[button] = 'X';
        }
        rr->put = 1;
    }
	
	return "";
}

std::string set_relay_name(int sn, const char* name, int userID) {
	if (!validate_user(userID, sn)) {
		printf("set_relay_name, validate_user = false\n");
		return "false";
	}
	
	printf("set_relay_name sn = %d, userID = %d\n", sn, userID);
	
	// Оновлюємо назву пристрою в БД
	std::string sql = "UPDATE relays SET gadget_name = ? WHERE sn = ?";
	sqlite3_stmt* stmt;
	
	if (sqlite3_prepare_v2(g_db.db, sql.c_str(), -1, &stmt, 0) == SQLITE_OK)
	{
		sqlite3_bind_text(stmt, 1, name, -1, SQLITE_STATIC);
		sqlite3_bind_int(stmt, 2, sn);

		if (sqlite3_step(stmt) == SQLITE_DONE)
		{
			sqlite3_finalize(stmt);
			return "OK";
		}
		else
		{
			printf("Error updating device name: %s\n", sqlite3_errmsg(g_db.db));
			sqlite3_finalize(stmt);
			return "Error: Failed to update device name";
		}
	}
	else
	{
		printf("Error preparing statement: %s\n", sqlite3_errmsg(g_db.db));
		return "Error: Database error";
	}
}

std::string set_relay_names(int sn, const char* relay1, const char* relay2, const char* relay3, const char* relay4, const char* relay5, const char* relay6, const char* relay7, const char* relay8, int userID) {
	if (!validate_user(userID, sn)) {
		printf("set_relay_names, validate_user = false\n");
		return "false";
	}
	
	printf("set_relay_names sn = %d, userID = %d\n", sn, userID);
	
	// Оновлюємо всі імена релейних виходів
	std::string sql = "UPDATE relays SET r1 = ?, r2 = ?, r3 = ?, r4 = ?, r5 = ?, r6 = ?, r7 = ?, r8 = ? WHERE sn = ?";
	sqlite3_stmt* stmt;
	
	if (sqlite3_prepare_v2(g_db.db, sql.c_str(), -1, &stmt, 0) == SQLITE_OK)
	{
		sqlite3_bind_text(stmt, 1, relay1, -1, SQLITE_STATIC);
		sqlite3_bind_text(stmt, 2, relay2, -1, SQLITE_STATIC);
		sqlite3_bind_text(stmt, 3, relay3, -1, SQLITE_STATIC);
		sqlite3_bind_text(stmt, 4, relay4, -1, SQLITE_STATIC);
		sqlite3_bind_text(stmt, 5, relay5, -1, SQLITE_STATIC);
		sqlite3_bind_text(stmt, 6, relay6, -1, SQLITE_STATIC);
		sqlite3_bind_text(stmt, 7, relay7, -1, SQLITE_STATIC);
		sqlite3_bind_text(stmt, 8, relay8, -1, SQLITE_STATIC);
		sqlite3_bind_int(stmt, 9, sn);

		if (sqlite3_step(stmt) == SQLITE_DONE)
		{
			sqlite3_finalize(stmt);
			return "OK";
		}
		else
		{
			printf("Error updating relay names: %s\n", sqlite3_errmsg(g_db.db));
			sqlite3_finalize(stmt);
			return "Error: Failed to update relay names";
		}
	}
	else
	{
		printf("Error preparing statement: %s\n", sqlite3_errmsg(g_db.db));
		return "Error: Database error";
	}
}
