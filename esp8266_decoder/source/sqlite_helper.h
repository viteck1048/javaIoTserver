#pragma once

/*

#include <string>
#include "sqlite3.h"

class SqliteHelper {
public:
	SqliteHelper(const char* db_path) : db(NULL) {
		if (sqlite3_open(db_path, &db)) {
			db = NULL;
		}
	}
	~SqliteHelper() {
		if (db) sqlite3_close(db);
	}
	bool exec(const char* sql) {
		char* errMsg = 0;
		int rc = sqlite3_exec(db, sql, 0, 0, &errMsg);
		if (rc != SQLITE_OK) {
			if (errMsg) sqlite3_free(errMsg);
			return false;
		}
		return true;
	}
	bool exists(const char* sql) {
		sqlite3_stmt* stmt;
		bool found = false;
		if (sqlite3_prepare_v2(db, sql, -1, &stmt, 0) == SQLITE_OK) {
			if (sqlite3_step(stmt) == SQLITE_ROW) found = true;
			sqlite3_finalize(stmt);
		}
		return found;
	}
	int get_int(const char* sql) {
		sqlite3_stmt* stmt;
		int res = -1;
		if (sqlite3_prepare_v2(db, sql, -1, &stmt, 0) == SQLITE_OK) {
			if (sqlite3_step(stmt) == SQLITE_ROW) res = sqlite3_column_int(stmt, 0);
			sqlite3_finalize(stmt);
		}
		return res;
	}
	bool insert(const char* sql) {
		return exec(sql);
	}
	void init_tables() {
		// 1. Довготривала таблиця для підсумкового часу каналів (канали 1-18, без 0)
		// Унікальний індекс (device_id, day_epoch) дозволить робити "запис або оновлення" (UPSERT)
		exec("CREATE TABLE IF NOT EXISTS channel_history ("
			"device_id TEXT, "
			"day_epoch INTEGER, "
			"ch0 INTEGER DEFAULT 0, ch1 INTEGER DEFAULT 0, ch2 INTEGER DEFAULT 0, ch3 INTEGER DEFAULT 0, ch4 INTEGER DEFAULT 0, "
			"ch5 INTEGER DEFAULT 0, ch6 INTEGER DEFAULT 0, ch7 INTEGER DEFAULT 0, ch8 INTEGER DEFAULT 0, "
			"ch9 INTEGER DEFAULT 0, ch10 INTEGER DEFAULT 0, ch11 INTEGER DEFAULT 0, ch12 INTEGER DEFAULT 0, "
			"ch13 INTEGER DEFAULT 0, ch14 INTEGER DEFAULT 0, ch15 INTEGER DEFAULT 0, ch16 INTEGER DEFAULT 0, "
			"ch17 INTEGER DEFAULT 0, ch18 INTEGER DEFAULT 0, "
			"PRIMARY KEY (device_id, day_epoch));");
		
		exec("CREATE TABLE IF NOT EXISTS channel_names ("
			"device_id TEXT, "
			"ch1 TEXT, ch2 TEXT, ch3 TEXT, ch4 TEXT, "
			"ch5 TEXT, ch6 TEXT, ch7 TEXT, ch8 TEXT, "
			"ch9 TEXT, ch10 TEXT, ch11 TEXT, ch12 TEXT, "
			"ch13 TEXT, ch14 TEXT, ch15 TEXT, ch16 TEXT, "
			"ch17 TEXT, ch18 TEXT, "
			"PRIMARY KEY (device_id));");

		// 2. Тимчасова таблиця для логів старт/стоп (насипом за 2 місяці)
		// Індекс (device_id, day_epoch) критичний для швидкої вибірки при перезапуску
		exec("CREATE TABLE IF NOT EXISTS channel_events ("
			"device_id TEXT, "
			"day_epoch INTEGER, "
			"channel INTEGER, "
			"event_type INTEGER DEFAULT 0, "
			"sec_of_day INTEGER);");

		exec("CREATE INDEX IF NOT EXISTS idx_events_dev_day ON channel_events(device_id, day_epoch);");

	}
//private:
	sqlite3* db;
};

/*/

#include "sqlite3.h"
#include <string>
#include <vector>

class SqliteHelper {
public:
	SqliteHelper(const char* db_path) : db(NULL) {
		// Відкриваємо в режимі повного багатопотоку (Serialized)
		if (sqlite3_open_v2(db_path, &db, SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, NULL) == SQLITE_OK) {
			// Вмикаємо WAL-режим: дозволяє читати під час запису
			exec("PRAGMA journal_mode=WAL;");
			exec("PRAGMA synchronous=NORMAL;");
			init_tables();
		} else {
			db = NULL;
		}
	}
	
	~SqliteHelper() {
		if (db) sqlite3_close(db);
	}

	bool exec(const char* sql) {
		// Передаємо NULL замість errMsg, щоб уникнути витоків пам'яті
		return sqlite3_exec(db, sql, 0, 0, NULL) == SQLITE_OK;
	}

	// Безпечний метод перевірки існування через параметризацію (захист від ін'єкцій)
	bool device_exists(const std::string& device_id, int day_epoch) {
		const char* sql = "SELECT 1 FROM channel_history WHERE device_id = ? AND day_epoch = ?;";
		sqlite3_stmt* stmt;
		bool found = false;
		
		if (sqlite3_prepare_v2(db, sql, -1, &stmt, 0) == SQLITE_OK) {
			sqlite3_bind_text(stmt, 1, device_id.c_str(), -1, SQLITE_TRANSIENT);
			sqlite3_bind_int(stmt, 2, day_epoch);
			
			if (sqlite3_step(stmt) == SQLITE_ROW) {
				found = true;
			}
			sqlite3_finalize(stmt);
		}
		return found;
	}

	void init_tables() {
		// Ваша структура таблиць (залишаємо без змін, вона чудова)
		exec("CREATE TABLE IF NOT EXISTS channel_history ("
			"device_id TEXT, "
			"day_epoch INTEGER, "
			"ch0 INTEGER DEFAULT 0, ch1 INTEGER DEFAULT 0, ch2 INTEGER DEFAULT 0, ch3 INTEGER DEFAULT 0, ch4 INTEGER DEFAULT 0, "
			"ch5 INTEGER DEFAULT 0, ch6 INTEGER DEFAULT 0, ch7 INTEGER DEFAULT 0, ch8 INTEGER DEFAULT 0, "
			"ch9 INTEGER DEFAULT 0, ch10 INTEGER DEFAULT 0, ch11 INTEGER DEFAULT 0, ch12 INTEGER DEFAULT 0, "
			"ch13 INTEGER DEFAULT 0, ch14 INTEGER DEFAULT 0, ch15 INTEGER DEFAULT 0, ch16 INTEGER DEFAULT 0, "
			"ch17 INTEGER DEFAULT 0, ch18 INTEGER DEFAULT 0, "
			"PRIMARY KEY (device_id, day_epoch));");
		
		exec("CREATE TABLE IF NOT EXISTS channel_names ("
			"device_id TEXT, "
			"ch1 TEXT, ch2 TEXT, ch3 TEXT, ch4 TEXT, "
			"ch5 TEXT, ch6 TEXT, ch7 TEXT, ch8 TEXT, "
			"ch9 TEXT, ch10 TEXT, ch11 TEXT, ch12 TEXT, "
			"ch13 TEXT, ch14 TEXT, ch15 TEXT, ch16 TEXT, "
			"ch17 TEXT, ch18 TEXT, "
			"PRIMARY KEY (device_id));");

		exec("CREATE TABLE IF NOT EXISTS channel_events ("
			"device_id TEXT, "
			"day_epoch INTEGER, "
			"channel INTEGER, "
			"event_type INTEGER DEFAULT 0, "
			"sec_of_day INTEGER);");

		exec("CREATE INDEX IF NOT EXISTS idx_events_dev_day ON channel_events(device_id, day_epoch);");
		// migration: add event_type if DB existed without it
		exec("ALTER TABLE channel_events ADD COLUMN event_type INTEGER DEFAULT 0;");

		// Незавершена поточна сесія (this_session) — один рядок на пристрій.
		// day_epoch = доба, до якої належить накопичення (потрібен для коректного
		// закриття при старті сервера в інший день). working = бітова маска
		// working_now по каналах 0..18.
		exec("CREATE TABLE IF NOT EXISTS channel_session ("
			"device_id TEXT, "
			"day_epoch INTEGER, "
			"ch0 INTEGER DEFAULT 0, ch1 INTEGER DEFAULT 0, ch2 INTEGER DEFAULT 0, ch3 INTEGER DEFAULT 0, ch4 INTEGER DEFAULT 0, "
			"ch5 INTEGER DEFAULT 0, ch6 INTEGER DEFAULT 0, ch7 INTEGER DEFAULT 0, ch8 INTEGER DEFAULT 0, "
			"ch9 INTEGER DEFAULT 0, ch10 INTEGER DEFAULT 0, ch11 INTEGER DEFAULT 0, ch12 INTEGER DEFAULT 0, "
			"ch13 INTEGER DEFAULT 0, ch14 INTEGER DEFAULT 0, ch15 INTEGER DEFAULT 0, ch16 INTEGER DEFAULT 0, "
			"ch17 INTEGER DEFAULT 0, ch18 INTEGER DEFAULT 0, "
			"working INTEGER DEFAULT 0, "
			"PRIMARY KEY (device_id));");
	}

//private:
	sqlite3* db;
};

//*/