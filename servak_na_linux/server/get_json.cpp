

char* replace_double_quotes(const char* input) {
    if (!input) return NULL; // Перевіряємо вхідний рядок

    size_t len = strlen(input);
    char* result = (char*)malloc(len + 1); // Виділяємо пам'ять для результату
    if (!result) return NULL; // Перевіряємо успішність виділення пам'яті

    for (size_t i = 0; i < len; i++) {
        if (input[i] == '"' || input[i] == '\'') {
            result[i] = ' '; // Заміняємо подвійні лапки і апострофи на пробіл
        } 
        // Додатковий код для інших символів:
        // else if (input[i] == '\\') {
        //     result[i] = ' '; // Наприклад, якщо треба замінити символ '\'
        // } 
        else {
            result[i] = input[i]; // Зберігаємо інші символи
        }
    }

    result[len] = '\0'; // Завершуємо рядок
    return result;
}


std::string get_json_npp(int z_id, int l_id)
{
	printf("GEN JSON-NPP\tz_id = %d\tl_id = %d\n", z_id, l_id);
	
	std::stringstream buff;
	
	connect_db();
	
	char sql_query[200];
	ISC_SHORT rr[5];
	bufer_int(npp_s);
	
	bufer_int(n_id);
	bufer_int(b_z_id);
	bufer_double(znachennja);
	bufer_str(umova, 50);
	bufer_str(coment, 100);
	
	XSQLDA* sqlda_output;
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(1));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 5;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = npp_s;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	
	sprintf(sql_query, "SELECT NPP_S FROM ZMINNY WHERE Z_ID = '%d'", z_id);
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	
	isc_stmt_handle stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				if(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
//					printf("COMPLITE\n");
					;
				}
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}

	isc_commit_transaction(status_vector, &tr_handle);
	
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		buff << "ERROR SELECT * FROM nZMINNY";
		free(sqlda_output);
		return buff.str(); // Помилка виконання SQL-запиту
	}
	
	buff << "{\"z_id\":" << z_id << ",\"l_id\":" << l_id << ",\"npp_s\":" << to_int(npp_s) << ",\"zm_npp\":[";
	
	free(sqlda_output);
	
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(5));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 5;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = n_id;
	sqlda_output->sqlvar[1].sqldata = b_z_id;
	sqlda_output->sqlvar[2].sqldata = znachennja;
	sqlda_output->sqlvar[3].sqldata = umova;
	sqlda_output->sqlvar[4].sqldata = coment;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	sqlda_output->sqlvar[1].sqlind = &rr[1];
	sqlda_output->sqlvar[2].sqlind = &rr[2];
	sqlda_output->sqlvar[3].sqlind = &rr[3];
	sqlda_output->sqlvar[4].sqlind = &rr[4];
	
	sprintf(sql_query, "SELECT * FROM ZMINNY_NPP WHERE Z_ID = '%d' ORDER BY N_ID ASC", z_id);
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	int fl_koma = 1;
	stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				while(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					if(fl_koma)
						fl_koma = 0;
					else
						buff << ',';
					buff << "{\"N_ID\":\"" << to_int(n_id) << "\",\"Z_ID\":\"" << to_int(b_z_id) << "\",\"ZNACHENNJA\":\"" << to_double(znachennja) << "\",\"UMOVA\":\"" << to_str(umova) << "\",\"COMENT\":\"" << to_str(coment) << "\"}";
				}
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}

	isc_commit_transaction(status_vector, &tr_handle);
	
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		buff << "ERROR SELECT * FROM nZMINNY_NPP";
		free(sqlda_output);
		return buff.str(); // Помилка виконання SQL-запиту
	}
	free(sqlda_output);
	
	buff << "]}";
	
	disconect_db();
	return buff.str();
}


std::string get_json_liry(int m_id, const char* mash_name)
{
	printf("GEN JSON-LIRY\tm_id = %d\n", m_id);
	
	std::stringstream buff;
	
	connect_db();
	
	bufer_int(b_m_id);
	bufer_str(b_name, 60);
	bufer_str(m1, 1000);
	bufer_str(m2, 1000);
	
	ISC_SHORT rr[10];
	isc_tr_handle tr_handle = 0;
	XSQLDA* sqlda_output_m;
	sqlda_output_m = (XSQLDA*)malloc(XSQLDA_LENGTH(4));
	sqlda_output_m->version = SQLDA_VERSION1;
	sqlda_output_m->sqln = 4;
	sqlda_output_m->sqld = 1;
	sqlda_output_m->sqlvar[0].sqldata = b_m_id;
	sqlda_output_m->sqlvar[1].sqldata = b_name;
	sqlda_output_m->sqlvar[2].sqldata = m1;
	sqlda_output_m->sqlvar[3].sqldata = m2;
	sqlda_output_m->sqlvar[0].sqlind = &rr[0];
	sqlda_output_m->sqlvar[1].sqlind = &rr[1];
	sqlda_output_m->sqlvar[2].sqlind = &rr[2];
	sqlda_output_m->sqlvar[3].sqlind = &rr[3];
	
	char sql_query[999];
	sprintf(sql_query, "SELECT * FROM MASHYNES WHERE M_ID=%d", m_id);
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	
	isc_stmt_handle stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output_m) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				if(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output_m) == 0) {
					;
				}
				else {
					{
						isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
						isc_commit_transaction(status_vector, &tr_handle);
						isc_print_status(status_vector);
						isc_rollback_transaction(status_vector, &tr_handle);
						disconect_db();
						buff << "ERROR SELECT * FROM MASHYNES";
						free(sqlda_output_m);
						return buff.str(); // Помилка виконання SQL-запиту
					}
				}
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}
	isc_commit_transaction(status_vector, &tr_handle);
	
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		buff << "ERROR SELECT * FROM MASHYNES";
		free(sqlda_output_m);
		return buff.str(); // Помилка виконання SQL-запиту
	}
	
	buff << "{\"name1\":\"" << to_str(b_name) << "\",\"mash\":{\"M_ID\":\"" << to_int(b_m_id) << "\",\"NAME\":\"" << to_str(b_name) << "\",\"M1\":\"" << to_str(m1) << "\",\"M2\":\"" << to_str(m2) << "\"},";
	free(sqlda_output_m);
	
	bufer_int(l_id_1);
	bufer_str(l_name, 60);
	bufer_int(magaz);
	bufer_str(form, 100);
	bufer_str(form_zv, 100);
	bufer_int(br_kol_lir);
	bufer_int(l_id_2);
	bufer_int(l_id_3);
	
	tr_handle = 0;
	XSQLDA* sqlda_output_l;
	sqlda_output_l = (XSQLDA*)malloc(XSQLDA_LENGTH(7));
	sqlda_output_l->version = SQLDA_VERSION1;
	sqlda_output_l->sqln = 7;
	sqlda_output_l->sqld = 1;
	sqlda_output_l->sqlvar[0].sqldata = l_id_1;
	sqlda_output_l->sqlvar[1].sqldata = b_m_id;
	sqlda_output_l->sqlvar[2].sqldata = l_name;
	sqlda_output_l->sqlvar[3].sqldata = magaz;
	sqlda_output_l->sqlvar[4].sqldata = form;
	sqlda_output_l->sqlvar[5].sqldata = form_zv;
	sqlda_output_l->sqlvar[6].sqldata = br_kol_lir;
	sqlda_output_l->sqlvar[0].sqlind = &rr[0];
	sqlda_output_l->sqlvar[1].sqlind = &rr[1];
	sqlda_output_l->sqlvar[2].sqlind = &rr[2];
	sqlda_output_l->sqlvar[3].sqlind = &rr[3];
	sqlda_output_l->sqlvar[4].sqlind = &rr[4];
	sqlda_output_l->sqlvar[5].sqlind = &rr[5];
	sqlda_output_l->sqlvar[6].sqlind = &rr[6];
	
	sprintf(sql_query, "SELECT * FROM LIRY WHERE M_ID=%d ORDER BY L_ID ASC", m_id);
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	int br_liri = 0;
	stmt_handle = 0;
	buff << "\"liry\":[";
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output_l) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				while(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output_l) == 0) {
					if(br_liri == 0) {
						buff << "{\"L_ID\":\"" << to_int(l_id_1);
						sqlda_output_l->sqlvar[0].sqldata = l_id_2;
					}
					else if(br_liri == 1) {
						buff << ",{\"L_ID\":\"" << to_int(l_id_2);
						sqlda_output_l->sqlvar[0].sqldata = l_id_3;
					}
					else {
						buff << ",{\"L_ID\":\"" << to_int(l_id_3);						
					}
					buff << "\",\"M_ID\":\"" << to_int(b_m_id) << "\",\"NAME\":\"" << to_str(l_name) << "\",\"MAGAZ\":\"" << to_int(magaz) << "\",\"FORM\":\"" << to_str(form) << "\",\"FORM_ZV\":\"" << to_str(form_zv) << "\",\"BR_KOL_LIR\":\"" << to_int(br_kol_lir) << "\"}";
					br_liri++;
				}
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}
	buff << "],";
	isc_commit_transaction(status_vector, &tr_handle);
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		buff << "ERROR SELECT * FROM LIRY";
		free(sqlda_output_l);
		return buff.str(); // Помилка виконання SQL-запиту
	}
	free(sqlda_output_l);
	
	bufer_int(z_id);
	bufer_str(z_name, 60);
	bufer_int(npp_s);
	bufer_double(znachennja);
	bufer_char(bukva);
	buff << "\"liry_zm_arr\":[";
	for(int i = 0; i < br_liri; i++) {
		tr_handle = 0;
		XSQLDA* sqlda_output_z;
		sqlda_output_z = (XSQLDA*)malloc(XSQLDA_LENGTH(5));
		sqlda_output_z->version = SQLDA_VERSION1;
		sqlda_output_z->sqln = 5;
		sqlda_output_z->sqld = 1;
		sqlda_output_z->sqlvar[0].sqldata = z_id;
		sqlda_output_z->sqlvar[1].sqldata = z_name;
		sqlda_output_z->sqlvar[2].sqldata = npp_s;
		sqlda_output_z->sqlvar[3].sqldata = znachennja;
		sqlda_output_z->sqlvar[4].sqldata = bukva;
		sqlda_output_z->sqlvar[0].sqlind = &rr[0];
		sqlda_output_z->sqlvar[1].sqlind = &rr[1];
		sqlda_output_z->sqlvar[2].sqlind = &rr[2];
		sqlda_output_z->sqlvar[3].sqlind = &rr[3];
		sqlda_output_z->sqlvar[4].sqlind = &rr[4];
		if(i == 0)
			sprintf(sql_query, "SELECT Z.Z_ID, Z.NAME, Z.NPP_S, Z.ZNACHENNJA, Z.BUKVA FROM ZMINNY_LIRA ZL LEFT JOIN ZMINNY Z ON ZL.Z_ID=Z.Z_ID WHERE ZL.L_ID=%d ORDER BY ZL.ZL_ID ASC", to_int(l_id_1));
		else if(i == 1)
			sprintf(sql_query, "SELECT Z.Z_ID, Z.NAME, Z.NPP_S, Z.ZNACHENNJA, Z.BUKVA FROM ZMINNY_LIRA ZL LEFT JOIN ZMINNY Z ON ZL.Z_ID=Z.Z_ID WHERE ZL.L_ID=%d ORDER BY ZL.ZL_ID ASC", to_int(l_id_2));
		else
			sprintf(sql_query, "SELECT Z.Z_ID, Z.NAME, Z.NPP_S, Z.ZNACHENNJA, Z.BUKVA FROM ZMINNY_LIRA ZL LEFT JOIN ZMINNY Z ON ZL.Z_ID=Z.Z_ID WHERE ZL.L_ID=%d ORDER BY ZL.ZL_ID ASC", to_int(l_id_3));
		isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	
		stmt_handle = 0;
		if(i != 0)
			buff << ',';
		buff << '[';
		int fl_koma = 0;
		if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
			if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output_z) == 0) {
				while(isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
					while(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output_z) == 0) {
						if(fl_koma)
							buff << ',';
						else
							fl_koma = 1;
						buff << "{\"Z_ID\":\"" << to_int(z_id) << "\",\"NAME\":\"" << replace_double_quotes(to_str(z_name)) << "\",\"NPP_S\":\"" << to_int(npp_s) << "\",\"ZNACHENNJA\":\"" << to_double(znachennja) << "\",\"BUKVA\":\"" << to_char(bukva) << "\"}";
					}
				}
			}
			isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
		}
		buff << ']';
		isc_commit_transaction(status_vector, &tr_handle);
		
		free(sqlda_output_z);
		if(status_vector[1]) {
			isc_print_status(status_vector);
			isc_rollback_transaction(status_vector, &tr_handle);
			disconect_db();
			buff << "ERROR SELECT * FROM ZMINNI";
			return buff.str(); // Помилка виконання SQL-запиту
		}
	}
	buff << "]}";
	disconect_db();
	return buff.str();
}


std::string get_json_zminny(int z_id, int zm_poz)
{
	printf("GEN JSON-ZMINNA\tz_id = %d\n", z_id);
	
	std::stringstream buff;
	Status status;
	
	connect_db();
	
	bufer_int(b_z_id);
	bufer_int(m_id);
	bufer_str(name, 60);
	bufer_char(bukva);
	bufer_double(znachennja);
	bufer_int(npp_s);
	ISC_SHORT rr[6];
	
	XSQLDA* sqlda_output;
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(6));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 6;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = b_z_id;
	sqlda_output->sqlvar[1].sqldata = m_id;
	sqlda_output->sqlvar[2].sqldata = name;
	sqlda_output->sqlvar[3].sqldata = bukva;
	sqlda_output->sqlvar[4].sqldata = znachennja;
	sqlda_output->sqlvar[5].sqldata = npp_s;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	sqlda_output->sqlvar[1].sqlind = &rr[1];
	sqlda_output->sqlvar[2].sqlind = &rr[2];
	sqlda_output->sqlvar[3].sqlind = &rr[3];
	sqlda_output->sqlvar[4].sqlind = &rr[4];
	sqlda_output->sqlvar[5].sqlind = &rr[5];
	
	char sql_query[999];
	sprintf(sql_query, "SELECT * FROM ZMINNY WHERE Z_ID = %d", z_id);
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	
	isc_stmt_handle stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				if(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					buff << "{\"zm\":{\"Z_ID\":\"" << to_int(b_z_id) << "\",\"M_ID\":\"" << to_int(m_id) << "\",\"NAME\":\"" << to_str(name) << "\",\"BUKVA\":\"" << to_char(bukva) << "\",\"ZNACHENNJA\":\"" << to_double(znachennja) << "\",\"NPP_S\":\"" << to_int(npp_s) << "\"},\"zm_poz\":" << zm_poz << "}";
				}
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}

	isc_commit_transaction(status_vector, &tr_handle);
	free(sqlda_output);
	
	
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		buff << "ERROR SELECT * FROM ZMINNI";
		return buff.str(); // Помилка виконання SQL-запиту
	}
	disconect_db();
	return buff.str();
}


std::string get_json_umovy(int l_id)
{
	printf("GEN JSON-UMOVY\tl_id = %d\n", l_id);
	
	std::stringstream buff;
	
	connect_db();
	
	ISC_SHORT rr[3];
	bufer_int(u_id);
	bufer_int(b_l_id);
	bufer_str(umova, 100);
	
	XSQLDA* sqlda_output;
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(3));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 3;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = u_id;
	sqlda_output->sqlvar[1].sqldata = b_l_id;
	sqlda_output->sqlvar[2].sqldata = umova;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	sqlda_output->sqlvar[1].sqlind = &rr[1];
	sqlda_output->sqlvar[2].sqlind = &rr[2];
	
	buff << "{\"l_id\":" << l_id << ",\"umovy\":[";
	int fl_koma = 0;
	
	char sql_query[100];
	sprintf(sql_query, "SELECT * FROM UMOVY WHERE L_ID = '%d' ORDER BY U_ID ASC", l_id);
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	
	isc_stmt_handle stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				while(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					if(!fl_koma)
						fl_koma = 1;
					else
						buff << ',';
					buff << "{\"U_ID\":\"" << to_int(u_id) << "\",\"L_ID\":\"" << to_int(b_l_id) << "\",\"UMOVA\":\"" << to_str(umova) << "\"}";
				}
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}
	
	isc_commit_transaction(status_vector, &tr_handle);
	
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		buff << "ERROR SELECT * FROM UMOVY";
		free(sqlda_output);
		return buff.str(); // Помилка виконання SQL-запиту
	}
	free(sqlda_output);
	buff << "]}";
	disconect_db();
	return buff.str();
}


std::string get_json_mash(const char* name)
{
	printf("GEN JSON-MASH\tname = %s\n", name == NULL ? "all" : name);
	
	std::stringstream buff;
	
	connect_db();
	
	int praporec_nicjoho_ne_znajdeno = 0;
	char buf_m_id[8];
	char buf_name[64];
	ISC_SHORT rr[2];
	
	XSQLDA* sqlda_output;
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(2));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 2;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = buf_name;
	sqlda_output->sqlvar[1].sqldata = buf_m_id;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	sqlda_output->sqlvar[1].sqlind = &rr[1];
	
	std::stringstream sql_query;
	sql_query << "SELECT NAME, M_ID FROM MASHYNES ";
	if(name[0] != 0) {
		sql_query << "WHERE LOWER(NAME) LIKE LOWER('%" << name << "%') ";
		praporec_nicjoho_ne_znajdeno = 1;
	}
	sql_query << " ORDER BY M_ID";
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	
	int fl_koma = 0;
	buff << "{\"find_fl\":\"";
	
	isc_stmt_handle stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query.str().c_str(), 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				while(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					if(praporec_nicjoho_ne_znajdeno == 1)
						praporec_nicjoho_ne_znajdeno = 2;
					if(!fl_koma) {
						fl_koma = 1;
						buff << praporec_nicjoho_ne_znajdeno << "\",\"mash\":[";
					}
					else
						buff << ',';
					buff << "{\"m_id\":\"" << *(int*)buf_m_id << "\",\"name\":\"" << ((VARY*)buf_name)->vary_string << "\"}";
				}
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}

	isc_commit_transaction(status_vector, &tr_handle);
	if(praporec_nicjoho_ne_znajdeno == 1) {
		isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
		stmt_handle = 0;
		if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
			if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, "SELECT NAME, M_ID FROM MASHYNES ORDER BY M_ID", 1, sqlda_output) == 0) {
				if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
					while(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
						if(!fl_koma) {
							fl_koma = 1;
							buff << praporec_nicjoho_ne_znajdeno << "\",\"mash\":[";
						}
						else
							buff << ',';
						buff << "{\"m_id\":\"" << *(int*)buf_m_id << "\",\"name\":\"" << ((VARY*)buf_name)->vary_string << "\"}";
					}
				}
			}
			isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
		}

		isc_commit_transaction(status_vector, &tr_handle);
	}
	
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		buff << "ERROR SELECT * FROM UMOVY";
		free(sqlda_output);
		return buff.str(); // Помилка виконання SQL-запиту
	}
	free(sqlda_output);
	buff << "]}";
	disconect_db();
	
	return buff.str();
}