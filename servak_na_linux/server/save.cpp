
std::string save_zm(int m_id, int l_id, int z_id, char bukva, int npp_s, const char* name, double znachennja)
{
	printf("PUT SAVE ZMINNA\tm_id = %d, l_id = %d, z_id = %d, bukva = %c, name = %s, znach = %.20f\n", m_id, l_id, z_id, bukva, name, znachennja);
	const std::string txt1 = "zminna ";
	const std::string txt2 = "peremykach ";
	const std::string txt3 = " zberezhena.";
	const std::string txt5 = "indeks M_ID abo Z_ID";
	const std::string txt6 = " dorivnjuje nulju.";
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


std::string save_usl(int u_id, int l_id, const char* umova)
{
	printf("PUT SAVE UMOVA\tu_id = %d, l_id = %d, umova = %s\n", u_id, l_id, umova);
	const std::string txt1 = "umova ";
	const std::string txt2 = " zberezhena.";
	const std::string txt3 = " maje menshe 3-h symvoliv.";
	const std::string txt4 = "indeks U_ID abo L_ID dorivnjuje nulju.";
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


std::string save_npp(int n_id, int z_id, double znachennja, const char* umova, const char* coment)
{
	printf("PUT SAVE NPP\tn_id = %d, z_id = %d, coment = %s\n", n_id, z_id, coment);
	const std::string txt1 = "umova ";
	const std::string txt5 = "komentar ";
	const std::string txt2 = " zberezhena.";
	const std::string txt3 = " maje menshe 3-h symvoliv.";
	const std::string txt4 = "indeks N_ID abo Z_ID";
	const std::string txt6 = " dorivnjuje nulju.";
	const std::string txt7 = "konstanta ";
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


std::string save_mash(int m_id, const char* name, const char* m1, const char* m2)
{
	printf("PUT SAVE MASH\tm_id = %d, name = %s\n", m_id, name);
	const std::string txt1 = "verstat ";
	const std::string txt2 = " maje menshe 3-h symvoliv.";
	const std::string txt3 = " zberezheno.";
	const std::string txt6 = "im'ja verstata ";
	const std::string txt7 = "perelik kolis ";
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


std::string save_lira(int m_id, int l_id, const char* name, int magaz, int br_kol_lir, const char* form, const char* form_zv)
{
	printf("PUT SAVE LIRA\tm_id = %d, l_id = %d, name = %s\n", m_id, l_id, name);
	const std::string txt1 = "lira ";
	const std::string txt2 = " maje menshe 3-h symvoliv.";
	const std::string txt3 = " zberezhena.";
	const std::string txt5 = "indeks M_ID abo L_ID dorivnjuje nulju.";
	const std::string txt6 = "im'ja liry ";
	const std::string txt7 = "osnovna formula liry ";
	const std::string txt8 = "zvorotnja formula liry ";
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


