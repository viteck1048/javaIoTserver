
std::string save_zm(int m_id, int l_id, int z_id, char bukva, int npp_s, const char* name, double znachennja, const char* lng)
{
	printf("PUT SAVE ZMINNA\tm_id = %d, l_id = %d, z_id = %d, bukva = %c, name = %s, znach = %.20f, lng = %s\n", m_id, l_id, z_id, bukva, name, znachennja, lng);
	std::string txt1, txt2, txt3, txt5, txt6;
	if(lng[0] == 'u' && lng[1] == 'k') {
		txt1 = "змінна ";
		txt2 = "перемекач ";
		txt3 = " збережена.";
		txt5 = "індекс M_ID або Z_ID";
		txt6 = " дорівнює нулю.";
	}
	else if(lng[0] == 'b' && lng[1] == 'g') {
		txt1 = "променлива ";
		txt2 = "преключвател ";
		txt3 = " сьхранена.";
		txt5 = "индекс M_ID или Z_ID";
		txt6 = " е нула.";
	}
	else if(lng[0] == 'e' && lng[1] == 'n') {
		txt1 = "the variable ";
		txt2 = " the swich ";
		txt3 = " is preserved.";
		txt5 = "index M_ID or Z_ID";
		txt6 = " is equal to zero.";
	}
	else {
		txt1 = "err";
		txt2 = "err";
		txt3 = "err";
		txt6 = "err";
		txt5 = "err";
	}
	Status status;
	if(npp_s < 0 || npp_s > 2)
		status.error("NPP_S != 0...2");
	if(m_id == 0 || z_id == 0)
		status.error((txt5 + txt6).c_str());
	if(status.success()) {
		if(npp_s == 0)
			status.info((txt1 + bukva + txt3).c_str());
		else
			status.info((txt2 + bukva + txt3).c_str());
		connect_db();
		char sql_query[200];
		if(znachennja != 0)
			sprintf(sql_query, "UPDATE ZMINNY SET ZNACHENNJA='%.20f' WHERE Z_ID='%d'", znachennja, z_id);
		else
			sprintf(sql_query, "UPDATE ZMINNY SET ZNACHENNJA=null WHERE Z_ID='%d'", z_id);
		isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
		isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
		isc_commit_transaction(status_vector, &tr_handle);
		if(status_vector[1]) {
			isc_print_status(status_vector);
			isc_rollback_transaction(status_vector, &tr_handle);
			status.error("ERROR INSERT INTO ZMINNY ZNACHENNJA");
		}
		if(name[0] != 0)
			sprintf(sql_query, "UPDATE ZMINNY SET NAME='%s' WHERE Z_ID='%d'", name, z_id);
		else
			sprintf(sql_query, "UPDATE ZMINNY SET NAME=null WHERE Z_ID='%d'", z_id);
		isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
		isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
		isc_commit_transaction(status_vector, &tr_handle);
		if(status_vector[1]) {
			isc_print_status(status_vector);
			isc_rollback_transaction(status_vector, &tr_handle);
			status.error("ERROR INSERT INTO ZMINNY NAME");
		}
		sprintf(sql_query, "UPDATE ZMINNY SET NPP_S='%d' WHERE Z_ID='%d'", npp_s, z_id);
		isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
		isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
		isc_commit_transaction(status_vector, &tr_handle);
		if(status_vector[1]) {
			isc_print_status(status_vector);
			isc_rollback_transaction(status_vector, &tr_handle);
			status.error("ERROR INSERT INTO ZMINNY NPP_S");
		}
		disconect_db();
	}
	return status.html().c_str();
	
}


std::string save_usl(int u_id, int l_id, const char* umova, const char* lng)
{
	printf("PUT SAVE UMOVA\tu_id = %d, l_id = %d, umova = %s, lng = %s\n", u_id, l_id, umova, lng);
	std::string txt1, txt2, txt3, txt4;
	if(lng[0] == 'u' && lng[1] == 'k') {
		txt1 = "умова ";
		txt2 = " збережена.";
		txt3 = " має меньше 3-х символів.";
		txt4 = "індекс U_ID або L_ID дорівнює нулю.";
	}
	else if(lng[0] == 'b' && lng[1] == 'g') {
		txt1 = "условието ";
		txt2 = " сьхранено.";
		txt3 = " има по малко от 3 символа.";
		txt4 = "индекс U_ID или L_ID е нула.";
	}
	else if(lng[0] == 'e' && lng[1] == 'n') {
		txt1 = "the condition ";
		txt2 = " is preserved.";
		txt3 = " should be more than 3 character.";
		txt4 = "index U_ID or L_ID is equal to zero.";
	}
	else {
		txt1 = "err";
		txt2 = "err";
		txt3 = "err";
		txt4 = "err";
	}
	Status status;
	if(strlen(umova) < 3)
		status.error((txt1 + umova + txt3).c_str());
	if(u_id == 0 || l_id == 0)
		status.error(txt4.c_str());
	
	if(status.success()) {
		status.info((txt1 + umova + txt2).c_str());
		char sql_query[200];
		sprintf(sql_query, "UPDATE UMOVY SET UMOVA='%s' WHERE U_ID=%d", umova, u_id);
		connect_db();
		isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
		isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
		isc_commit_transaction(status_vector, &tr_handle);
		if(status_vector[1]) {
			isc_print_status(status_vector);
			isc_rollback_transaction(status_vector, &tr_handle);
			status.error("ERROR INSERT INTO UMOVY");
		}
		disconect_db();
	}
	return status.html().c_str();
}


std::string save_npp(int n_id, int z_id, double znachennja, const char* umova, const char* coment, const char* lng)
{
	printf("PUT SAVE NPP\tn_id = %d, z_id = %d, coment = %s, lng = %s\n", n_id, z_id, coment, lng);
	std::string txt1, txt2, txt3, txt4, txt5, txt6, txt7;
	if(lng[0] == 'u' && lng[1] == 'k') {
		txt1 = "умова ";
		txt5 = "коментар ";
		txt2 = " збережена.";
		txt3 = " має меньше 3-х символів.";
		txt4 = "індекс N_ID або Z_ID";
		txt6 = " дорівнює нулю.";
		txt7 = "константа ";
	}
	else if(lng[0] == 'b' && lng[1] == 'g') {
		txt1 = "условието ";
		txt5 = "коментарий ";
		txt2 = " сьхранена.";
		txt3 = " има по малко от 3 символа.";
		txt4 = "индекс N_ID или Z_ID";
		txt6 = " е нула.";
		txt7 = "константа ";
	}
	else if(lng[0] == 'e' && lng[1] == 'n') {
		txt1 = "the condition ";
		txt5 = "the coment ";
		txt2 = " is preserved.";
		txt3 = " should be more than 3 character.";
		txt4 = "index N_ID or Z_ID";
		txt6 = " is equal to zero.";
		txt7 = "the constant ";
	}
	else {
		txt1 = "err";
		txt5 = "err";
		txt2 = "err";
		txt3 = "err";
		txt4 = "err";
		txt6 = "err";
		txt7 = "err";
	}
	Status status;
	if(strlen(umova) < 3)
		status.error((txt1 + umova + txt3).c_str());
	if(strlen(coment) < 1)
		status.error((txt5 + coment + txt3).c_str());
	if(n_id == 0 || z_id == 0)
		status.error((txt4 + txt6).c_str());
	if(znachennja == 0)
		status.error((txt7 + coment + txt6).c_str());
	
	if(status.success()) {
		status.info((txt7 + coment + txt2).c_str());
		char sql_query[200];
		sprintf(sql_query, "UPDATE ZMINNY_NPP SET UMOVA='%s', COMENT='%s', ZNACHENNJA='%.20f' WHERE N_ID='%d'", umova, coment, znachennja, n_id);
		connect_db();
		isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
		isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
		isc_commit_transaction(status_vector, &tr_handle);
		if(status_vector[1]) {
			isc_print_status(status_vector);
			isc_rollback_transaction(status_vector, &tr_handle);
			status.error("ERROR INSERT INTO ZMINNY_NPP");
		}
		disconect_db();
	}
	return status.html().c_str();
}


std::string save_mash(int m_id, const char* name, const char* m1, const char* m2, const char* lng)
{
	printf("PUT SAVE MASH\tm_id = %d, name = %s, lng = %s\n", m_id, name, lng);
	std::string txt1, txt2, txt3, txt6, txt7, txt8;
	if(lng[0] == 'u' && lng[1] == 'k') {
		txt1 = "верстат ";
		txt2 = " має меньше 3-х символів.";
		txt3 = " збережено.";
		txt6 = "ім'я верстата ";
		txt7 = "перелік коліс ";
		txt8 = "налічує непідтримувані символи.";
	}
	else if(lng[0] == 'b' && lng[1] == 'g') {
		txt1 = "лира ";
		txt2 = " има по малко от 3 символа.";
		txt3 = " сьхранена.";
		txt6 = "името на машина ";
		txt7 = "поле 'зьбни колела' ";
		txt8 = "сьдьржа букви";
	}
	else if(lng[0] == 'e' && lng[1] == 'n') {
		txt1 = "the mashine ";
		txt2 = " should be more than 3 character.";
		txt3 = " is preserved.";
		txt6 = "";
		txt7 = "list of gears ";
		txt8 = "has characters.";
	}
	else {
		txt1 = "err";
		txt2 = "err";
		txt3 = "err";
		txt6 = "err";
		txt7 = "err";
		txt8 = "err";
	}
	Status status;
	if(strlen(name) < 3)
		status.error((txt6 + name + txt2).c_str());
	if(strlen(m1) < 3)
		status.error((txt7 + txt2).c_str());
	
	if(status.success()) {
		status.info((txt1 + name + txt3).c_str());
		char sql_query[2000];
		if(m2[0] == 0)
			sprintf(sql_query, "UPDATE MASHYNES SET NAME='%s', M1='%s', M2=null WHERE M_ID='%d'", name, m1, m_id);
		else
			sprintf(sql_query, "UPDATE MASHYNES SET NAME='%s', M1='%s', M2='%s' WHERE M_ID='%d'", name, m1, m2, m_id);
		connect_db();
		isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
		isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
		isc_commit_transaction(status_vector, &tr_handle);
		if(status_vector[1]) {
			isc_print_status(status_vector);
			isc_rollback_transaction(status_vector, &tr_handle);
			status.error("ERROR INSERT INTO MASHYNES");
		}
		disconect_db();
	}
	return status.html().c_str();
}


std::string save_lira(int m_id, int l_id, const char* name, int magaz, int br_kol_lir, const char* form, const char* form_zv, const char* lng)
{
	printf("PUT SAVE LIRA\tm_id = %d, l_id = %d, name = %s, lng = %s\n", m_id, l_id, name, lng);
	std::string txt1, txt2, txt3, txt5, txt6, txt7, txt8;
	if(lng[0] == 'u' && lng[1] == 'k') {
		txt1 = "ліра ";
		txt2 = " має меньше 3-х символів.";
		txt3 = " збережена.";
		txt5 = "індекс M_ID або L_ID дорівнює нулю.";
		txt6 = "ім'я ліри ";
		txt7 = "основна формула ліри ";
		txt8 = "зворотня формула ліри ";
	}
	else if(lng[0] == 'b' && lng[1] == 'g') {
		txt1 = "лира ";
		txt2 = " има по малко от 3 символа.";
		txt3 = " сьхранена.";
		txt5 = "индекс M_ID или L_ID е нула.";
		txt6 = "името на лира ";
		txt7 = "основна формула на лира ";
		txt8 = "обратна формула на лира ";
	}
	else if(lng[0] == 'e' && lng[1] == 'n') {
		txt1 = "the lira ";
		txt2 = " should be more than 3 character.";
		txt3 = " is preserved.";
		txt5 = "index M_ID or L_ID is equal to zero.";
		txt6 = "the name of the lira ";
		txt7 = "the basic formula of the lira ";
		txt8 = "he inverse formula of the lira ";
	}
	else {
		txt1 = "err";
		txt2 = "err";
		txt3 = "err";
		txt5 = "err";
		txt6 = "err";
		txt7 = "err";
		txt8 = "err";
	}
	Status status;
	if(strlen(name) < 3)
		status.error((txt6 + name + txt2).c_str());
	if(strlen(form) < 3)
		status.error((txt7 + name + txt2).c_str());
	if(strlen(form_zv) < 3)
		status.error((txt8 + name + txt2).c_str());
	if(m_id == 0 || l_id == 0)
		status.error((txt1 + name + txt5).c_str());
	if(magaz != 1 && magaz != 2)
		status.error("MAGAZ != 1/2");
	if(br_kol_lir < 2 || br_kol_lir > 4)
		status.error("BR_KOL_LIR != 2...4");
	
	if(status.success()) {
		char sql_query[2000];
		sprintf(sql_query, "UPDATE LIRY SET NAME='%s', FORM='%s', FORM_ZV='%s', MAGAZ='%d', BR_KOL_LIR='%d' WHERE L_ID='%d';", name, form, form_zv, magaz, br_kol_lir, l_id);
		connect_db();
		isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
		isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, NULL);
		isc_commit_transaction(status_vector, &tr_handle);
		if(status_vector[1]) {
			isc_print_status(status_vector);
			isc_rollback_transaction(status_vector, &tr_handle);
			status.error("ERROR INSERT INTO LIRY");
		}
		else {
			status.info((txt1 + name + txt3).c_str());
		}
		disconect_db();
	}
	return status.html().c_str();
}


