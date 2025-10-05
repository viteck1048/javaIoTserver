#pragma once
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
        exec("CREATE TABLE IF NOT EXISTS relays (r_id INTEGER PRIMARY KEY AUTOINCREMENT, sn INTEGER, r1 TEXT, r2 TEXT, r3 TEXT, r4 TEXT, r5 TEXT, r6 TEXT, r7 TEXT, r8 TEXT, gadget_name TEXT);");
        // user_relays: N:M зв'язок, один userID може мати багато реле, одне реле — багато userID
        exec("CREATE TABLE IF NOT EXISTS user_relays (z_id INTEGER PRIMARY KEY AUTOINCREMENT, userID INTEGER, r_id INTEGER);");
    }
//private:
    sqlite3* db;
};
