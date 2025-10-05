#pragma once
#include <string>
#include <vector>
#include <cstdint>

struct KnopkaInfo {
	int r_idx;
	int sn;
	std::string text;
};

extern bool hide_log;
std::string get_knopky(int userID);
std::string rr_get_status(int userID);
std::string rr_put_status(int sn, int button, int userID);
std::string obrobnyk_relay(const char* sn_txt, const char* acp_txt, const char* relays, const char* init_txt, const char* cs_txt, const char* pin_txt = nullptr);
std::string save_log_file(std::vector<uint8_t> log_file);
void set_get_log_file_flag(int sn, int userID, bool flag);
bool get_get_log_file_flag(int sn);
std::vector<uint8_t> send_for_user_log_file(int sn, int userID);
std::string get_gadgets(int userID);
std::string get_relays(int userID, int sn);
std::string set_relay_name(int sn, const char* name, int userID);
std::string set_relay_names(int sn, const char* relay1, const char* relay2, const char* relay3, const char* relay4, const char* relay5, const char* relay6, const char* relay7, const char* relay8, int userID);
// Додай тут інші функції/класи, якщо потрібно
