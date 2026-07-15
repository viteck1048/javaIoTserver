
// Порядковий номер положення в межах перемикача -> римська цифра.
static std::string to_roman(int n) {
	if(n <= 0)
		return std::to_string(n);
	static const int val[] = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
	static const char* sym[] = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
	std::string r;
	for(int i = 0; i < 13; i++)
		while(n >= val[i]) {
			r += sym[i];
			n -= val[i];
		}
	return r;
}


std::string add_zminna(int l_id, char bukva, int zm_poz)
{
	printf("POST ADD-ZMINNA\t\tl_id = %d\tbukva = %c\tzm_poz = %d\n", l_id, bukva, zm_poz);
	
	std::stringstream buff;
	Status status;
	
	connect_db();
	int m_id = 0;
	int z_id = 0;
	char sql_query[999];
	sprintf(sql_query, "SELECT M_ID FROM LIRY WHERE L_ID=%d", l_id);
	
	bufer_int(b_m_id);
	ISC_SHORT rr[10];
	
	XSQLDA* sqlda_output;
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(1));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 1;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = b_m_id;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	
	isc_stmt_handle stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				if(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					m_id = to_int(b_m_id);
				}
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}

	isc_commit_transaction(status_vector, &tr_handle);
	
	if(status_vector[1] || m_id == 0) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		buff << "ERROR SELECT * FROM LIRY 46";
		free(sqlda_output);
		return buff.str();
	}
	free(sqlda_output);
	
	sprintf(sql_query, "SELECT Z_ID FROM ZMINNY WHERE M_ID=%d AND BUKVA='%c'", m_id, bukva);
	
	bufer_int(b_z_id);
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(1));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 1;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = b_z_id;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	
	stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				if(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					z_id = to_int(b_z_id);
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
		buff << "ERROR SELECT * FROM ZMINNY 83";
		free(sqlda_output);
		return buff.str();
	}
	free(sqlda_output);
	
	if(z_id) {
		int zl_id = 0;
		sprintf(sql_query, "SELECT ZL_ID FROM ZMINNY_LIRA WHERE M_ID=%d AND L_ID=%d AND Z_ID=%d ", m_id, l_id, z_id);
		bufer_int(b_zl_id);
		sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(1));
		sqlda_output->version = SQLDA_VERSION1;
		sqlda_output->sqln = 1;
		sqlda_output->sqld = 1;
		sqlda_output->sqlvar[0].sqldata = b_zl_id;
		sqlda_output->sqlvar[0].sqlind = &rr[0];
		
		isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
		stmt_handle = 0;
		if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
			if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
				if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
					if(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
						zl_id = to_int(b_zl_id);
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
			buff << "ERROR SELECT * FROM ZMINNY_LIRA 118";
			free(sqlda_output);
			return buff.str();
		}
		free(sqlda_output);
		if(!zl_id) {
			sprintf(sql_query, "INSERT INTO ZMINNY_LIRA(Z_ID, L_ID, M_ID) VALUES (%d, %d, %d);", z_id, l_id, m_id);
			isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
			isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
			isc_commit_transaction(status_vector, &tr_handle);
			if (status_vector[1]) {
				isc_print_status(status_vector);
				isc_rollback_transaction(status_vector, &tr_handle);
				disconect_db();
				buff << "ERROR INSERT INTO ZMINNY_LIRA 132";
				return buff.str();
			}
		}
		disconect_db();
		sprintf(sql_query, "{\"z_id\":%d,\"zm_poz\":%d}", z_id, zm_poz);
		buff << sql_query;
		return buff.str();
	}
	sprintf(sql_query, "INSERT INTO ZMINNY(M_ID, BUKVA, NPP_S) VALUES (%d, '%c', 0);", m_id, bukva);
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
	isc_commit_transaction(status_vector, &tr_handle);
	if (status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		buff << "ERROR INSERT INTO ZMINNY 149";
		return buff.str();
	}
	
	sprintf(sql_query, "SELECT Z_ID FROM ZMINNY WHERE M_ID=%d AND BUKVA='%c'", m_id, bukva);
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(1));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 1;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = b_z_id;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				if(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					z_id = to_int(b_z_id);
				}
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}
	isc_commit_transaction(status_vector, &tr_handle);
	if(status_vector[1] || !z_id) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		buff << "ERROR SELECT * FROM ZMINNY 178";
		free(sqlda_output);
		return buff.str();
	}
	free(sqlda_output);
	
	sprintf(sql_query, "INSERT INTO ZMINNY_LIRA(Z_ID, L_ID, M_ID) VALUES (%d, %d, %d);", z_id, l_id, m_id);
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
	isc_commit_transaction(status_vector, &tr_handle);
	if (status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		buff << "ERROR INSERT INTO ZMINNY_LIRA 192";
		return buff.str();
	}
	
	disconect_db();
	sprintf(sql_query, "{\"z_id\":%d,\"zm_poz\":%d}", z_id, zm_poz);
	buff << sql_query;
	return buff.str();
}


std::string add_zm_npp(int z_id)
{
	printf("POST ADD-ZM-NPP\t\tz_id = %d\n", z_id);
	std::stringstream buff;
	Status status;
	connect_db();
	char sql_query[999];
	ISC_SHORT rr[6];
	isc_stmt_handle stmt_handle;
	XSQLDA* sqlda_output;
	
	sprintf(sql_query, "INSERT INTO ZMINNY_NPP(Z_ID, ZNACHENNJA, UMOVA, COMENT) VALUES (%d, 1, '1=1', 'srakadupakurvamaty');", z_id);
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
	isc_commit_transaction(status_vector, &tr_handle);
	if (status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		buff << "ERROR INSERT INTO ZMINNY_NPP 24";
		return buff.str();
	}
	
	sprintf(sql_query, "SELECT N_ID FROM ZMINNY_NPP WHERE COMENT='srakadupakurvamaty'");
	
	int n_id = 0;
	bufer_int(b_n_id);
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(1));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 1;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = b_n_id;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				if(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					n_id = to_int(b_n_id);
				}
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}
	isc_commit_transaction(status_vector, &tr_handle);
	
	free(sqlda_output);
	if(status_vector[1] || n_id == 0) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		buff << "ERROR SELECT L_ID FROM ZMINNY_NPP 57";
		return buff.str();
	}

	// Дефолтна назва положення -- римська цифра його порядку в ЦЬОМУ перемикачі (z_id),
	// а не глобальний N_ID. Свіжовставлений рядок уже враховано в COUNT, тож це і є номер.
	int npp_poz = 0;
	sprintf(sql_query, "SELECT COUNT(*) FROM ZMINNY_NPP WHERE Z_ID=%d", z_id);
	bufer_int(b_npp_poz);
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(1));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 1;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = b_npp_poz;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				if(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					npp_poz = to_int(b_npp_poz);
				}
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}
	isc_commit_transaction(status_vector, &tr_handle);
	free(sqlda_output);

	std::string roman = to_roman(npp_poz);
	sprintf(sql_query, "UPDATE ZMINNY_NPP SET COMENT='%s' WHERE N_ID=%d", roman.c_str(), n_id);
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
	isc_commit_transaction(status_vector, &tr_handle);
	if (status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		buff << "ERROR UPDATE ZMINNY_NPP 69";
		return buff.str();
	}
	
	buff << n_id;
	disconect_db();
	return buff.str();
}


std::string add_umova(int l_id)
{
	printf("POST ADD-UMOVA\t\tl_id = %d\n", l_id);
	std::stringstream buff;
	Status status;
	connect_db();
	char sql_query[999];
	ISC_SHORT rr[6];
	isc_stmt_handle stmt_handle;
	XSQLDA* sqlda_output;
	
	sprintf(sql_query, "INSERT INTO UMOVY(L_ID, UMOVA) VALUES (%d, 'qqqqqqqqqqqqqqqqq');", l_id);
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
	isc_commit_transaction(status_vector, &tr_handle);
	if (status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		buff << "ERROR INSERT INTO UMOVY 22";
		return buff.str();
	}
	
	sprintf(sql_query, "SELECT U_ID FROM UMOVY WHERE UMOVA='qqqqqqqqqqqqqqqqq'");
	
	int u_id = 0;
	bufer_int(b_u_id);
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(1));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 1;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = b_u_id;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				if(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					u_id = to_int(b_u_id);
				}
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}
	isc_commit_transaction(status_vector, &tr_handle);
	
	free(sqlda_output);
	if(status_vector[1] || u_id == 0) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		buff << "ERROR SELECT U_ID FROM UMOVY 55";
		return buff.str();
	}
	
	sprintf(sql_query, "UPDATE UMOVY SET UMOVA='1=1' WHERE U_ID=%d", u_id);
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
	isc_commit_transaction(status_vector, &tr_handle);
	if (status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		buff << "ERROR UPDATE ZMINNY_NPP 69";
		return buff.str();
	}
	
	buff << u_id;
	disconect_db();
	return buff.str();
}


std::string add_lira(int m_id)
{
	printf("POST ADD-LIRA\t\tm_id = %d\n", m_id);
	
	int count_m_id = 0;
	std::stringstream buff;
	Status status;
	connect_db();
	ISC_SHORT rr[6];
	isc_stmt_handle stmt_handle;
	char sql_query[999];
	sprintf(sql_query, "SELECT COUNT(M_ID) AS LIRS FROM (SELECT M_ID FROM LIRY WHERE M_ID=%d) LL GROUP BY M_ID", m_id);
	
	bufer_int(b_count_m_id);
	
	XSQLDA* sqlda_output;
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(1));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 1;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = b_count_m_id;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				if(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					count_m_id = to_int(b_count_m_id);
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
		buff << "ERROR SELECT * FROM LIRY 43";
		return buff.str();
	}
	
	if(count_m_id >= 3) {
		buff << "error: max 3 liry";
		disconect_db();
		return buff.str();
	}
	
	sprintf(sql_query, "INSERT INTO LIRY(M_ID, FORM, FORM_ZV, MAGAZ, NAME, BR_KOL_LIR) VALUES (%d, 'i=x', 'x=i', 1, 'srakadupacurvamaty11', 4);", m_id);
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
	isc_commit_transaction(status_vector, &tr_handle);
	if (status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		buff << "ERROR INSERT INTO LIRY 64";
		return buff.str();
	}
	
	sprintf(sql_query, "SELECT L_ID FROM LIRY WHERE NAME='srakadupacurvamaty11' AND M_ID=%d", m_id);
	
	int l_id = 0;
	bufer_int(b_l_id);
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(1));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 1;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = b_l_id;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				if(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					l_id = to_int(b_l_id);
				}
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}
	isc_commit_transaction(status_vector, &tr_handle);
	
	free(sqlda_output);
	if(status_vector[1] || l_id == 0) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		buff << "ERROR SELECT L_ID FROM LIRY 97";
		return buff.str();
	}
	
	sprintf(sql_query, "UPDATE LIRY SET NAME='new lire' WHERE L_ID=%d", l_id);
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
	isc_commit_transaction(status_vector, &tr_handle);
	if (status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		buff << "ERROR UPDATE LIRY 109";
		return buff.str();
	}
	
	buff << l_id;
	disconect_db();
	return buff.str();
}


std::string get_add_mash()
{
	printf("GET ADD-MASH\n");
	
	std::stringstream buff;
	
	buff << "<h1 class='icon add_icon' data-i18n='new_machine'>novyj verstat</h1>";
	buff << "<form id='add-mash-form' action='add-mash' enctype='multipart/form-data' method='post'>";
		buff << "<input type='hidden' name='mash[M_ID]' value=''/>";
		buff << "<p><label data-i18n='machine_name'>im'ja verstata</label><input name='mash[NAME]' class='txt medium fc_bl_mash_name' value=''/></p>";
		buff << "<p style='display: none;'><label>magaz_1</label><input name='mash[M1]' class='txt dovhe input_non_enter fc_bl_mash_m1' value='11 22 33 44 55 66 77 88 99 111 222'/></p>";
		buff << "<p style='display: none;'><label>magaz_2</label><input name='mash[M2]' class='txt dovhe input_non_enter fc_bl_mash_m2' value=''/></p>";
		buff << "<p><input type='submit' class='save-new-mash' data-i18n-value='save' value='Save'/></p>";
	buff << "</form>";
	
	return buff.str();
}


std::string post_add_mash(const char* mash, const char* m1)
{
	printf("POST ADD-MASH\n");
	
	std::stringstream buff;
	Status status;
	
	connect_db();
	bufer_int(m_id);
	ISC_SHORT rr[1];
	
	XSQLDA* sqlda_output;
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(1));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 1;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = m_id;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	
	char sql_query[100];
	sprintf(sql_query, "SELECT M_ID FROM MASHYNES WHERE NAME='%s'", mash);
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	
	isc_stmt_handle stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				if(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					status.error("taka mashynka vzhe isnuje", "srv_mash_exists");
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
		free(sqlda_output);
		return buff.str();
	}
	free(sqlda_output);
	
	if(strlen(mash) <= 1)
		status.error("im'ja verstata maje buty dovshe 1 symvola.", "srv_name_min1");
	
	
	if(status.success()) {
		isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
		sprintf(sql_query, "INSERT INTO MASHYNES(NAME, M1) VALUES('%s','%s')", mash, m1);
		isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
		isc_commit_transaction(status_vector, &tr_handle);
		if (status_vector[1]) {
			isc_print_status(status_vector);
			isc_rollback_transaction(status_vector, &tr_handle);
			disconect_db();
			status.error("M1(M2) maje literu abo NAME maje ne-ASCII symvoly.", "srv_m1m2_or_name");
			buff << status.html();
			return buff.str();
		}
		
		sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(1));
		sqlda_output->version = SQLDA_VERSION1;
		sqlda_output->sqln = 1;
		sqlda_output->sqld = 1;
		sqlda_output->sqlvar[0].sqldata = m_id;
		sqlda_output->sqlvar[0].sqlind = &rr[0];
		
		sprintf(sql_query, "SELECT M_ID FROM MASHYNES WHERE NAME='%s'", mash);
		
		isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
		int fl_find = 0;
		isc_stmt_handle stmt_handle = 0;
		if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
			if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
				if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
					if(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
						fl_find = 1;
						buff << to_int(m_id);
					}
				}
			}
			isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
		}
		
		isc_commit_transaction(status_vector, &tr_handle);
		free(sqlda_output);
		
		if(!fl_find) {
			status.error("M1(M2) maje literu.", "srv_m1m2");
			buff << status.html();
		}
	}
	else
		buff << status.html();
	
	disconect_db();
	
	return buff.str();
}

