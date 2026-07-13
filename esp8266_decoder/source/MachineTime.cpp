#include "http_utils.h"
#include "platform.h"
#include "MyCrypter.h"
#include "request.h"
#include "my_time.h"
#include "sqlite_helper.h"
#include "sqlite3.h"
#include <sstream>
#include <fstream>
#include <iostream>
#include <cstring>
#include <cctype>
#include <vector>
#include <algorithm>
#include <climits>
#include <string>
#include <shared_mutex>
#include <map>
#include <mutex>
#include <memory>
#include <ctime>
#include <thread>
#include <chrono>
#include <csignal>

// Platform-specific includes
#ifndef _WIN32
	#include <unistd.h>
	#include <arpa/inet.h>
	#include <netinet/in.h>
	#include <sys/socket.h>
#endif

static std::unique_ptr<SqliteHelper> g_db_owner;
static SqliteHelper* g_db = nullptr;

void init_machine_time_db(const char* path) {
	if (!g_db) {
		g_db_owner = std::make_unique<SqliteHelper>(path);
		g_db = g_db_owner.get();
	}
}

class MachineTime {
	private:
		std::string id;
		MyCripter cripter;
		std::shared_mutex mutex;
		int tzOffset = 0;

		struct Day {
			int _time;
			int channel_time[19];
			bool working_now[19];
			std::vector<int> channel_start[19];
			std::vector<int> channel_stop[19];
			int pending_count[19]; // гістерезис: підряд пакетів до зміни стану

			Day(int tzOffset = 0) {
				_time = std::time(nullptr) + tzOffset;
				_time = _time / (24 * 3600);
				for(int i = 0; i < 19; i++) {
					channel_time[i] = 0;
					working_now[i] = false;
					pending_count[i] = 0;
				}
			}
			// Конструктор для відновлення з БД — без прив'язки до поточного часу
			Day(int day_epoch, bool /*from_db*/) : _time(day_epoch) {
				for(int i = 0; i < 19; i++) {
					channel_time[i] = 0;
					working_now[i] = false;
					pending_count[i] = 0;
				}
			}
		};

		std::vector<Day> month;
		std::vector<Day> old_month;
		Day this_session;
		std::vector<std::string> channel_names;

		void db_load_names() {
			if (!g_db || !g_db->db) return;
			const char* sql =
				"SELECT ch1,ch2,ch3,ch4,ch5,ch6,ch7,ch8,ch9,"
				"ch10,ch11,ch12,ch13,ch14,ch15,ch16,ch17,ch18 "
				"FROM channel_names WHERE device_id=?;";
			sqlite3_stmt* stmt;
			if (sqlite3_prepare_v2(g_db->db, sql, -1, &stmt, nullptr) != SQLITE_OK) return;
			sqlite3_bind_text(stmt, 1, id.c_str(), -1, SQLITE_TRANSIENT);
			if (sqlite3_step(stmt) == SQLITE_ROW) {
				for (int i = 0; i < 18; i++) {
					const char* val = (const char*)sqlite3_column_text(stmt, i);
					channel_names[i + 1] = val ? val : "";
				}
			}
			sqlite3_finalize(stmt);
		}

		void db_save_names() {
			if (!g_db || !g_db->db) return;
			const char* sql =
				"INSERT OR REPLACE INTO channel_names "
				"(device_id, ch1,ch2,ch3,ch4,ch5,ch6,ch7,ch8,ch9,"
				"ch10,ch11,ch12,ch13,ch14,ch15,ch16,ch17,ch18) "
				"VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);";
			sqlite3_stmt* stmt;
			if (sqlite3_prepare_v2(g_db->db, sql, -1, &stmt, nullptr) != SQLITE_OK) return;
			sqlite3_bind_text(stmt, 1, id.c_str(), -1, SQLITE_TRANSIENT);
			for (int i = 1; i <= 18; i++)
				sqlite3_bind_text(stmt, 1 + i, channel_names[i].c_str(), -1, SQLITE_TRANSIENT);
			sqlite3_step(stmt);
			sqlite3_finalize(stmt);
		}

		// Знаходить або створює Day з потрібним epoch у векторі (вставляє в порядку зростання)
		Day& find_or_create_day(std::vector<Day>& vec, int epoch) {
			for (auto& d : vec) {
				if (d._time == epoch) return d;
			}
			auto it = vec.begin();
			while (it != vec.end() && it->_time < epoch) ++it;
			return *vec.insert(it, Day(epoch, true));
		}

		void db_save_history_day(const Day& d) {
			if (!g_db || !g_db->db) return;
			const char* sql =
				"INSERT OR REPLACE INTO channel_history "
				"(device_id, day_epoch, ch0, ch1, ch2, ch3, ch4, ch5, ch6, ch7, ch8, "
				"ch9, ch10, ch11, ch12, ch13, ch14, ch15, ch16, ch17, ch18) "
				"VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);";
			sqlite3_stmt* stmt;
			if (sqlite3_prepare_v2(g_db->db, sql, -1, &stmt, nullptr) != SQLITE_OK) return;
			sqlite3_bind_text(stmt, 1, id.c_str(), -1, SQLITE_TRANSIENT);
			sqlite3_bind_int(stmt, 2, d._time);
			for (int i = 0; i < 19; i++)
				sqlite3_bind_int(stmt, 3 + i, d.channel_time[i]);
			sqlite3_step(stmt);
			sqlite3_finalize(stmt);
		}

		void db_save_events_day(const Day& d) {
			if (!g_db || !g_db->db) return;
			// Замінюємо повністю: видаляємо старі події цього дня, вставляємо актуальні
			{
				const char* sql = "DELETE FROM channel_events WHERE device_id=? AND day_epoch=?;";
				sqlite3_stmt* stmt;
				if (sqlite3_prepare_v2(g_db->db, sql, -1, &stmt, nullptr) == SQLITE_OK) {
					sqlite3_bind_text(stmt, 1, id.c_str(), -1, SQLITE_TRANSIENT);
					sqlite3_bind_int(stmt, 2, d._time);
					sqlite3_step(stmt);
					sqlite3_finalize(stmt);
				}
			}
			const char* sql =
				"INSERT INTO channel_events (device_id, day_epoch, channel, event_type, sec_of_day) "
				"VALUES (?,?,?,?,?);";
			sqlite3_stmt* stmt;
			if (sqlite3_prepare_v2(g_db->db, sql, -1, &stmt, nullptr) != SQLITE_OK) return;
			for (int ch = 0; ch < 19; ch++) {
				for (int s : d.channel_start[ch]) {
					sqlite3_reset(stmt);
					sqlite3_bind_text(stmt, 1, id.c_str(), -1, SQLITE_TRANSIENT);
					sqlite3_bind_int(stmt, 2, d._time);
					sqlite3_bind_int(stmt, 3, ch);
					sqlite3_bind_int(stmt, 4, 0); // start
					sqlite3_bind_int(stmt, 5, s);
					sqlite3_step(stmt);
				}
				for (int s : d.channel_stop[ch]) {
					sqlite3_reset(stmt);
					sqlite3_bind_text(stmt, 1, id.c_str(), -1, SQLITE_TRANSIENT);
					sqlite3_bind_int(stmt, 2, d._time);
					sqlite3_bind_int(stmt, 3, ch);
					sqlite3_bind_int(stmt, 4, 1); // stop
					sqlite3_bind_int(stmt, 5, s);
					sqlite3_step(stmt);
				}
			}
			sqlite3_finalize(stmt);
		}

		// Зберігає поточну незавершену сесію в окрему таблицю channel_session.
		// day_epoch = доба, до якої належить накопичення (month.back()), щоб при
		// старті в інший день можна було закрити сесію в її власний день.
		void db_save_session() {
			if (!g_db || !g_db->db || month.empty()) return;
			int working = 0;
			for (int i = 0; i < 19; i++)
				if (this_session.working_now[i]) working |= (1 << i);
			const char* sql =
				"INSERT OR REPLACE INTO channel_session "
				"(device_id, day_epoch, ch0, ch1, ch2, ch3, ch4, ch5, ch6, ch7, ch8, "
				"ch9, ch10, ch11, ch12, ch13, ch14, ch15, ch16, ch17, ch18, working) "
				"VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?);";
			sqlite3_stmt* stmt;
			if (sqlite3_prepare_v2(g_db->db, sql, -1, &stmt, nullptr) != SQLITE_OK) return;
			sqlite3_bind_text(stmt, 1, id.c_str(), -1, SQLITE_TRANSIENT);
			sqlite3_bind_int(stmt, 2, month.back()._time);
			for (int i = 0; i < 19; i++)
				sqlite3_bind_int(stmt, 3 + i, this_session.channel_time[i]);
			sqlite3_bind_int(stmt, 22, working);
			sqlite3_step(stmt);
			sqlite3_finalize(stmt);
		}

		// Додає (а не заміщує) час каналів до дня в channel_history через UPSERT.
		// Потрібно лише для закриття сесії в день поза 2-місячним вікном пам'яті.
		void db_add_history_times(int day_epoch, const int* add) {
			if (!g_db || !g_db->db) return;
			const char* sql =
				"INSERT INTO channel_history "
				"(device_id, day_epoch, ch0, ch1, ch2, ch3, ch4, ch5, ch6, ch7, ch8, "
				"ch9, ch10, ch11, ch12, ch13, ch14, ch15, ch16, ch17, ch18) "
				"VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
				"ON CONFLICT(device_id, day_epoch) DO UPDATE SET "
				"ch0=ch0+excluded.ch0, ch1=ch1+excluded.ch1, ch2=ch2+excluded.ch2, "
				"ch3=ch3+excluded.ch3, ch4=ch4+excluded.ch4, ch5=ch5+excluded.ch5, "
				"ch6=ch6+excluded.ch6, ch7=ch7+excluded.ch7, ch8=ch8+excluded.ch8, "
				"ch9=ch9+excluded.ch9, ch10=ch10+excluded.ch10, ch11=ch11+excluded.ch11, "
				"ch12=ch12+excluded.ch12, ch13=ch13+excluded.ch13, ch14=ch14+excluded.ch14, "
				"ch15=ch15+excluded.ch15, ch16=ch16+excluded.ch16, ch17=ch17+excluded.ch17, "
				"ch18=ch18+excluded.ch18;";
			sqlite3_stmt* stmt;
			if (sqlite3_prepare_v2(g_db->db, sql, -1, &stmt, nullptr) != SQLITE_OK) return;
			sqlite3_bind_text(stmt, 1, id.c_str(), -1, SQLITE_TRANSIENT);
			sqlite3_bind_int(stmt, 2, day_epoch);
			for (int i = 0; i < 19; i++)
				sqlite3_bind_int(stmt, 3 + i, add[i]);
			sqlite3_step(stmt);
			sqlite3_finalize(stmt);
		}

		void new_day() {
			if (!month.empty()) {
				if (g_db && g_db->db) g_db->exec("BEGIN;");
				db_save_history_day(month.back());
				db_save_events_day(month.back());
				if (g_db && g_db->db) g_db->exec("COMMIT;");
			}
			month.push_back(Day(tzOffset));
		}

		void new_month() {
			// Зберігаємо останній день місяця, що архівується, ДО std::move(month).
			// Інакше new_day() нижче побачить порожній month (guard !month.empty())
			// і пропустить збереження — так губився останній день місяця
			// (channel_history + channel_events).
			if (!month.empty() && g_db && g_db->db) {
				g_db->exec("BEGIN;");
				db_save_history_day(month.back());
				db_save_events_day(month.back());
				g_db->exec("COMMIT;");
			}

			// Видаляємо з channel_events всі записи позаминулого місяця (який виходить з вікна)
			if (!old_month.empty() && g_db && g_db->db) {
				int threshold = old_month.front()._time;
				const char* sql = "DELETE FROM channel_events WHERE device_id=? AND day_epoch<?;";
				sqlite3_stmt* stmt;
				if (sqlite3_prepare_v2(g_db->db, sql, -1, &stmt, nullptr) == SQLITE_OK) {
					sqlite3_bind_text(stmt, 1, id.c_str(), -1, SQLITE_TRANSIENT);
					sqlite3_bind_int(stmt, 2, threshold);
					sqlite3_step(stmt);
					sqlite3_finalize(stmt);
				}
			}
			old_month.clear();
		    old_month.shrink_to_fit();
			old_month = std::move(month);
		}

		void new_session() {
			for (int i = 0; i < 19; i++) {
				month.back().channel_time[i] += this_session.channel_time[i];
				this_session.channel_time[i] = 0;
			}
			db_save_session(); // обнуляємо channel_session після злиття
			std::time_t t = std::time(nullptr) + tzOffset;
			if (month.back()._time != t / (24 * 3600)) {
				std::tm tm;
			#ifdef _WIN32
				gmtime_s(&tm, &t);
			#else
				gmtime_r(&t, &tm);
			#endif
				if(tm.tm_mon + (tm.tm_year * 12) != my_time_get_month_epoch(month.back()._time)) {
					new_month();
				}
				new_day();
			}
		}

		std::string get_day_info(const Day& day) {
			bool is_current_day = (month.back()._time == day._time);
			std::string resp = "{\"server_time\":\"" + my_time_str() + "\",\"gadget_time\":\"" + my_time_str_offset(tzOffset) + "\",\"channels\":[";
			for (int i = 0; i < 19; i++) {
				int ch_time = is_current_day ? day.channel_time[i] + this_session.channel_time[i] : day.channel_time[i];
				bool wn = is_current_day && this_session.working_now[i];
				char buf[64];
				std::snprintf(buf, sizeof(buf), "%s{\"id\":%d,\"time_seconds\":%d,\"working_now\":%s}",
					i > 0 ? "," : "", i, ch_time, wn ? "true" : "false");
				resp += buf;
			}
			resp += "]}";
			return resp;
		}

		std::string get_month_info() {
			std::string resp = "{\"server_time\":\"" + my_time_str() + "\",\"gadget_time\":\"" + my_time_str_offset(tzOffset) + "\",\"days\":[";
			bool first = true;
			for (const auto& d : old_month) {
				if (!first) resp += ",";
				first = false;
				resp += "{\"epoch\":" + std::to_string(d._time) + ",\"date\":\"" + my_time_str_calendar_day(d._time) + "\"}";
			}
			for (const auto& d : month) {
				if (!first) resp += ",";
				first = false;
				resp += "{\"epoch\":" + std::to_string(d._time) + ",\"date\":\"" + my_time_str_calendar_day(d._time) + "\"}";
			}
			resp += "]}";
			return resp;
		}

		//заїбав, мудило
		int fourBytesToInt(const std::vector<uint8_t>& data, int offset) {
			return (data[offset * sizeof(int)] << 0) |
				   (data[offset * sizeof(int) + 1] << 8) |
				   (data[offset * sizeof(int) + 2] << 16) |
				   (data[offset * sizeof(int) + 3] << 24);
		}

		// викликається з get() що вже тримає shared_lock
		std::string get_from_db(int day_epoch) {
			if (!g_db || !g_db->db) return "";
			const char* sql =
				"SELECT ch0,ch1,ch2,ch3,ch4,ch5,ch6,ch7,ch8,"
				"ch9,ch10,ch11,ch12,ch13,ch14,ch15,ch16,ch17,ch18 "
				"FROM channel_history WHERE device_id=? AND day_epoch=?;";
			sqlite3_stmt* stmt;
			if (sqlite3_prepare_v2(g_db->db, sql, -1, &stmt, nullptr) != SQLITE_OK) return "";
			sqlite3_bind_text(stmt, 1, id.c_str(), -1, SQLITE_TRANSIENT);
			sqlite3_bind_int(stmt, 2, day_epoch);
			std::string resp;
			if (sqlite3_step(stmt) == SQLITE_ROW) {
				resp = "{\"server_time\":\"\",\"gadget_time\":\"\",\"channels\":[";
				for (int i = 0; i < 19; i++) {
					int ch_time = sqlite3_column_int(stmt, i);
					char buf[64];
					std::snprintf(buf, sizeof(buf), "%s{\"id\":%d,\"time_seconds\":%d,\"working_now\":false}",
						i > 0 ? "," : "", i, ch_time);
					resp += buf;
				}
				resp += "]}";
			}
			sqlite3_finalize(stmt);
			return resp;
		}

	public:
		MachineTime(std::string id, std::string tz_str) : id(id) {
			tzOffset = offsetTimeZone(tz_str);
			this_session = Day(tzOffset);
			channel_names.assign(19, "");

			if (g_db && g_db->db) {
				db_load_names();
				int today_epoch = (int)((std::time(nullptr) + tzOffset) / (24 * 3600));
				int today_m = my_time_get_month_epoch(today_epoch);
				int prev_m  = today_m - 1;

				// Завантажуємо channel_history для поточного і минулого місяців
				{
					const char* sql =
						"SELECT day_epoch, ch0,ch1,ch2,ch3,ch4,ch5,ch6,ch7,ch8,ch9,"
						"ch10,ch11,ch12,ch13,ch14,ch15,ch16,ch17,ch18 "
						"FROM channel_history WHERE device_id=? ORDER BY day_epoch ASC;";
					sqlite3_stmt* stmt;
					if (sqlite3_prepare_v2(g_db->db, sql, -1, &stmt, nullptr) == SQLITE_OK) {
						sqlite3_bind_text(stmt, 1, id.c_str(), -1, SQLITE_TRANSIENT);
						while (sqlite3_step(stmt) == SQLITE_ROW) {
							int ep = sqlite3_column_int(stmt, 0);
							int m  = my_time_get_month_epoch(ep);
							std::vector<Day>* vec = nullptr;
							if      (m == today_m) vec = &month;
							else if (m == prev_m)  vec = &old_month;
							if (vec) {
								Day& d = find_or_create_day(*vec, ep);
								for (int i = 0; i < 19; i++)
									d.channel_time[i] = sqlite3_column_int(stmt, 1 + i);
							}
						}
						sqlite3_finalize(stmt);
					}
				}

				// Завантажуємо channel_events; застарілі видаляємо з БД
				{
					const char* sql =
						"SELECT day_epoch, channel, event_type, sec_of_day "
						"FROM channel_events WHERE device_id=? ORDER BY rowid ASC;";
					sqlite3_stmt* stmt;
					std::vector<int> stale;
					if (sqlite3_prepare_v2(g_db->db, sql, -1, &stmt, nullptr) == SQLITE_OK) {
						sqlite3_bind_text(stmt, 1, id.c_str(), -1, SQLITE_TRANSIENT);
						while (sqlite3_step(stmt) == SQLITE_ROW) {
							int ep   = sqlite3_column_int(stmt, 0);
							int ch   = sqlite3_column_int(stmt, 1);
							int type = sqlite3_column_int(stmt, 2);
							int sec  = sqlite3_column_int(stmt, 3);
							int m    = my_time_get_month_epoch(ep);
							if (ch < 0 || ch >= 19) continue;
							std::vector<Day>* vec = nullptr;
							if      (m == today_m) vec = &month;
							else if (m == prev_m)  vec = &old_month;
							if (vec) {
								Day& d = find_or_create_day(*vec, ep);
								(type == 0 ? d.channel_start[ch] : d.channel_stop[ch]).push_back(sec);
							} else {
								stale.push_back(ep);
							}
						}
						sqlite3_finalize(stmt);
					}
					for (int ep : stale) {
						const char* del = "DELETE FROM channel_events WHERE device_id=? AND day_epoch=?;";
						sqlite3_stmt* del_stmt;
						if (sqlite3_prepare_v2(g_db->db, del, -1, &del_stmt, nullptr) == SQLITE_OK) {
							sqlite3_bind_text(del_stmt, 1, id.c_str(), -1, SQLITE_TRANSIENT);
							sqlite3_bind_int(del_stmt, 2, ep);
							sqlite3_step(del_stmt);
							sqlite3_finalize(del_stmt);
						}
					}
				}

				// Відновлюємо this_session з channel_session разом із днем-якорем
				int sess_day = -1;
				int sess_time[19] = {0};
				int sess_working = 0;
				{
					const char* sql =
						"SELECT day_epoch, ch0,ch1,ch2,ch3,ch4,ch5,ch6,ch7,ch8,"
						"ch9,ch10,ch11,ch12,ch13,ch14,ch15,ch16,ch17,ch18, working "
						"FROM channel_session WHERE device_id=?;";
					sqlite3_stmt* stmt;
					if (sqlite3_prepare_v2(g_db->db, sql, -1, &stmt, nullptr) == SQLITE_OK) {
						sqlite3_bind_text(stmt, 1, id.c_str(), -1, SQLITE_TRANSIENT);
						if (sqlite3_step(stmt) == SQLITE_ROW) {
							sess_day = sqlite3_column_int(stmt, 0);
							for (int i = 0; i < 19; i++)
								sess_time[i] = sqlite3_column_int(stmt, 1 + i);
							sess_working = sqlite3_column_int(stmt, 20);
						}
						sqlite3_finalize(stmt);
					}
				}

				if (sess_day == today_epoch) {
					// Сесія належить сьогоднішній добі — лишаємо її живою
					for (int i = 0; i < 19; i++) {
						this_session.channel_time[i] = sess_time[i];
						this_session.working_now[i] = (sess_working >> i) & 1;
					}
				} else if (sess_day > 0) {
					// Старт у іншу добу: закриваємо стару сесію в її ВЛАСНИЙ день,
					// а не в сьогоднішній. this_session лишається нульовою.
					int m = my_time_get_month_epoch(sess_day);
					if (m == today_m || m == prev_m) {
						Day& d = find_or_create_day(m == today_m ? month : old_month, sess_day);
						for (int i = 0; i < 19; i++)
							d.channel_time[i] += sess_time[i];
						db_save_history_day(d);
					} else {
						// День поза 2-місячним вікном пам'яті — дописуємо прямо в БД
						db_add_history_times(sess_day, sess_time);
					}
				}

				// Гарантуємо наявність сьогоднішнього дня на вершині month
				if (month.empty() || month.back()._time != today_epoch)
					month.push_back(Day(today_epoch, true));

				// Перезаписуємо рядок сесії під сьогоднішній день: для gap-кейсу це
				// нулі з якорем=today, щоб закрита сесія не злилась повторно при
				// наступному рестарті.
				db_save_session();
			} else {
				new_day();
			}
		}

		~MachineTime() {
			if (month.empty() || !g_db || !g_db->db) return;
			printf("Saving data for device %s...\n", id.c_str());
			g_db->exec("BEGIN;");
			db_save_history_day(month.back()); // зберігаємо день як є, без злиття
			db_save_session();                 // this_session окремо в channel_session
			db_save_events_day(month.back());
			g_db->exec("COMMIT;");
		}

		std::string set_post(const std::string value) {
			std::unique_lock lock(mutex);
			std::vector<uint8_t> data = cripter.decrypt(value);
			if (!data.size()) {
				return cripter.encrypt("Invalid data size");
			}
			return cripter.encrypt(g_setup.get("myStaticKeyResponce"));
		}

		std::string set(const std::string value) {
			//блокувати все, поки пишеш
			std::unique_lock lock(mutex);
			std::vector<uint8_t> data = cripter.decrypt(value);

			if (data.size() != 20 * sizeof(int)) {
				return cripter.encrypt("Invalid data size");
			}

			int flag_new_session = fourBytesToInt(data, 11);
			if (flag_new_session) {
				new_session();
			}
			this_session._time = (std::time(nullptr) + tzOffset) % (24 * 3600);
			for (int i = 0; i < 19; i++) {
				int j = i < 11 ? i : i + 1;

				int time_tmp = fourBytesToInt(data, j) >> 8;
				int time_ch = this_session.channel_time[i];
				bool increased = (time_tmp > time_ch);
				bool unchanged = (time_tmp == time_ch);

				if (increased && !this_session.working_now[i]) {
					if (++this_session.pending_count[i] >= 3) {
						this_session.working_now[i] = true;
						this_session.pending_count[i] = 0;
						month.back().channel_start[i].push_back(this_session._time);
					}
				} else if (unchanged && this_session.working_now[i]) {
					if (++this_session.pending_count[i] >= 3) {
						this_session.working_now[i] = false;
						this_session.pending_count[i] = 0;
						month.back().channel_stop[i].push_back(this_session._time);
					}
				} else if ((increased && this_session.working_now[i]) || (unchanged && !this_session.working_now[i])) {
					this_session.pending_count[i] = 0;
				} else {
					printf("Error: time cannot decrease or stay the same while channel is working\n");
				}

				this_session.channel_time[i] = time_tmp;
			}

			return cripter.encrypt(g_setup.get("myStaticKeyResponce"));
		}

		void set_timeOffset(std::string tz_str) {
			tzOffset = offsetTimeZone(tz_str);
		}

		std::string get(int day = 0) {
			//загальмувати, допоки щось пишеться в set. читати пускати всіх підряд паралельно
			std::shared_lock lock(mutex);
			std::string resp;

			if (day == 0) {
				resp = get_month_info();
			}
			else {
				int earliest = !old_month.empty() ? old_month.front()._time
				             : (!month.empty()    ? month.front()._time : INT_MAX);
				if (day < earliest) {
					resp = get_from_db(day);
				} else {
					for (const auto& d : old_month) {
						if (d._time == day) { resp = get_day_info(d); break; }
					}
					if (resp.empty()) {
						for (const auto& d : month) {
							if (d._time == day) { resp = get_day_info(d); break; }
						}
					}
				}
			}
			return resp;
		}

		std::string get_events(int day, int channel) {
			std::shared_lock lock(mutex);
			if (channel < 0 || channel >= 19)
				return "{\"error\":\"channel out of range\"}";

			const Day* target = nullptr;
			for (const auto& d : old_month) {
				if (d._time == day) { target = &d; break; }
			}
			if (!target) {
				for (const auto& d : month) {
					if (d._time == day) { target = &d; break; }
				}
			}
			if (!target)
				return "{\"error\":\"day not found\"}";

			std::string resp = "{\"day\":" + std::to_string(day) + ",\"channel\":" + std::to_string(channel) + ",\"starts\":[";
			for (size_t i = 0; i < target->channel_start[channel].size(); i++) {
				if (i > 0) resp += ",";
				resp += std::to_string(target->channel_start[channel][i]);
			}
			resp += "],\"stops\":[";
			for (size_t i = 0; i < target->channel_stop[channel].size(); i++) {
				if (i > 0) resp += ",";
				resp += std::to_string(target->channel_stop[channel][i]);
			}
			resp += "]}";
			return resp;
		}

		std::string get_names() {
			std::shared_lock lock(mutex);
			std::string resp = "{\"names\":[";
			for (int i = 1; i <= 18; i++) {
				if (i > 1) resp += ",";
				resp += "\"";
				for (char c : channel_names[i]) {
					if      (c == '"')  resp += "\\\"";
					else if (c == '\\') resp += "\\\\";
					else                resp += c;
				}
				resp += "\"";
			}
			resp += "]}";
			return resp;
		}

		bool set_name(int ch, const std::string& name) {
			if (ch < 1 || ch > 18) return false;
			std::unique_lock lock(mutex);
			channel_names[ch] = name;
			db_save_names();
			return true;
		}

		bool delete_name(int ch) {
			if (ch < 1 || ch > 18) return false;
			std::unique_lock lock(mutex);
			channel_names[ch] = "";
			db_save_names();
			return true;
		}

		void update_today() {
			std::shared_lock lock(mutex);
			if (month.empty() || !g_db || !g_db->db) return;
			/*printf("[update_today] id=%s  day=%d  ch0=%d  session_ch0=%d\n",
				id.c_str(), month.back()._time,
				month.back().channel_time[0], this_session.channel_time[0]);*/
			db_save_history_day(month.back()); // день як є, без злиття
			db_save_session();                 // this_session окремо в channel_session
		}

};

//static std::vector<MachineTime> machineTime;
static std::map<std::string, std::shared_ptr<MachineTime>> machineTime;
static std::shared_mutex machineTimeMutex;


Response* getMachineTime18Channels(Request* rq) {
	// перелік зареєстрованих id — без прив'язки до конкретного пристрою
	if (rq->param("ids")) {
		std::shared_lock lock(machineTimeMutex);
		std::string resp = "{\"ids\":[";
		bool first = true;
		for (const auto& [key, _] : machineTime) {
			if (!first) resp += ",";
			first = false;
			resp += "\"";
			for (char c : key) {
				if      (c == '"')  resp += "\\\"";
				else if (c == '\\') resp += "\\\\";
				else                resp += c;
			}
			resp += "\"";
		}
		resp += "]}";
		return new Response(200, resp, "application/json");
	}

	std::string id = rq->znach("id");
	std::shared_ptr<MachineTime> obj;
	{
		std::shared_lock lock(machineTimeMutex);
		auto it = machineTime.find(id);
		if (it == machineTime.end()) {
			return new Response(404);
		} else {
			obj = it->second;
		}
	}

	if (rq->param("name"))
		return new Response(200, obj->get_names(), "application/json");

	if (rq->param("channel")) {
		if (!rq->param("day"))
			return new Response(400);
		int day = atoi(rq->znach("day"));
		int channel = atoi(rq->znach("channel"));
		return new Response(200, obj->get_events(day, channel), "application/json");
	}

	std::string resp;
	if (rq->param("day")) {
		int day = atoi(rq->znach("day"));
		resp = obj->get(day);
		if (resp.empty())
			return new Response(404);
	} else {
		resp = obj->get();
	}

	return new Response(200, resp, "application/json");
}

Response* postMachineTime18Channels(Request* rq) {
	std::string id = rq->znach("id");
	std::shared_ptr<MachineTime> obj;

	// Спочатку безпечно шукаємо через .find() під shared_lock
	{
		std::shared_lock lock(machineTimeMutex);
		auto it = machineTime.find(id);
		if (it != machineTime.end()) {
			obj = it->second;
			if(rq->param("x-timezone", Request::HEADERS)) {
				std::string tz_str = rq->znach("x-timezone", Request::HEADERS);
				obj->set_timeOffset(tz_str);
			}
		}
	}

	// Якщо об'єкта немає — блокуємо мапу на запис та створюємо його
	if (!obj) {
		if(rq->param("x-timezone", Request::HEADERS)) {
			std::string tz_str = rq->znach("x-timezone", Request::HEADERS);
			printf("Creating new MachineTime with id=%s and timezone=%s\n", id.c_str(), tz_str.c_str());
			obj = std::make_shared<MachineTime>(id, tz_str);
		} else {
			obj = std::make_shared<MachineTime>(id, "UTC");
			printf("Creating new MachineTime with id=%s and default timezone UTC\n", id.c_str());
		}
		{
			std::unique_lock lock(machineTimeMutex);
			// Double-check: чи не створив його інший потік, поки ми чекали лок
			auto it = machineTime.find(id);
			if (it != machineTime.end()) {
				obj = it->second;
			} else {
				machineTime[id] = obj; // Тут запис безпечний
			}
		}
	}

	return new Response(200, obj->set_post(rq->body_str), "application/octet-stream");


}

Response* putMachineTime18Channels(Request* rq) {
	std::string id = rq->znach("id");
	std::shared_ptr<MachineTime> obj;
	{
		std::shared_lock lock(machineTimeMutex);
		auto it = machineTime.find(id);

		if (it == machineTime.end()) {
			return new Response(404);
		} else {
			obj = it->second;
		}
	}
	if (rq->param("name")) {
		if (!rq->param("channel"))
			return new Response(400);
		int ch = atoi(rq->znach("channel"));
		std::string name = rq->znach("name");
		return obj->set_name(ch, name) ? new Response(200) : new Response(400);
	}

	if(rq->param("x-timezone", Request::HEADERS)) {
		std::string tz_str = rq->znach("x-timezone", Request::HEADERS);
		obj->set_timeOffset(tz_str);
	}

	return new Response(200, obj->set(rq->body_str), "application/octet-stream");

}

Response* deleteMachineTime18Channels(Request* rq) {
	std::string id = rq->znach("id");
	std::shared_ptr<MachineTime> obj;
	{
		std::shared_lock lock(machineTimeMutex);
		auto it = machineTime.find(id);
		if (it == machineTime.end())
			return new Response(404);
		obj = it->second;
	}
	if (!rq->param("channel"))
		return new Response(400);
	int ch = atoi(rq->znach("channel"));
	return obj->delete_name(ch) ? new Response(200) : new Response(400);
}

static void machine_time_signal_handler(int sig) {
	printf("[signal] caught signal %d — flushing data and exiting\n", sig);
	std::exit(0); // triggers static destructors → ~MachineTime() saves to DB
}

void start_machine_time_agent_and_signals() {
	std::signal(SIGINT,  machine_time_signal_handler);
	std::signal(SIGTERM, machine_time_signal_handler);
	printf("[init] signal handlers registered (SIGINT, SIGTERM)\n");

	std::string enabled = g_setup.get("save_db_agent");
	if (enabled.empty() || (enabled != "true" && enabled != "1")) {
		printf("[init] save_db_agent not enabled, skipping agent thread\n");
		return;
	}

	printf("Starting machine time agent thread...\n");
	int interval_min = 30;
	std::string interval_str = g_setup.get("save_db_agent_period");
	if (!interval_str.empty()) {
		try { interval_min = std::stoi(interval_str); } catch (...) {}
	}
	if (interval_min < 1) interval_min = 1;
	printf("Machine time agent will save data to DB every %d minutes\n", interval_min);
	std::thread([interval_min]() {
		while (true) {
			std::this_thread::sleep_for(std::chrono::minutes(interval_min));
			std::shared_lock lock(machineTimeMutex);
			for (auto& [id, obj] : machineTime)
				obj->update_today();
			printf("Machine time agent: saved current day data to DB for all devices\n");
		}
	}).detach();
}
