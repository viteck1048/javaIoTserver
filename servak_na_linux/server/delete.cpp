
std::string delete_zm_npp_all(int z_id)
{
	printf("DELETE ZM NPP ALL  \tz_id = %d\n", z_id);
	std::stringstream buff;
	Status status;
	char sql_query[100];
	connect_db();
	sprintf(sql_query, "DELETE FROM ZMINNY_NPP WHERE Z_ID='%d'", z_id);
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
	isc_commit_transaction(status_vector, &tr_handle);
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		status.error("ERROR 16 npp deleted");
	}
	sprintf(sql_query, "UPDATE ZMINNY SET NPP_S=0 WHERE Z_ID='%d'", z_id);
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
	isc_commit_transaction(status_vector, &tr_handle);
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		status.error("ERROR 25 npp all deleted");
	}
	disconect_db();
	if(status.success())
		status.info("npp all deleted.", "srv_npp_all_deleted");
	buff << status.html();
	return buff.str();
}


std::string delete_zm_npp(int n_id)
{
	printf("DELETE ZM NPP\t\tn_id = %d\n", n_id);
	std::stringstream buff;
	Status status;
	char sql_query[100];
	connect_db();
	sprintf(sql_query, "DELETE FROM ZMINNY_NPP WHERE N_ID='%d'", n_id);
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
	isc_commit_transaction(status_vector, &tr_handle);
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		status.error("ERROR 17 npp deleted");
	}
	disconect_db();
	if(status.success())
		status.info("npp deleted.", "srv_npp_deleted");
	buff << status.html();
	return buff.str();
}


std::string delete_zm(int l_id, int z_id)
{
	printf("DELETE ZMINNA\t\tl_id = %d, z_id = %d\n", l_id, z_id);
	
	Status status;
	std::stringstream buff;
	char sql_query[500];
	sprintf(sql_query, "SELECT M_ID FROM LIRY WHERE L_ID='%d'", l_id);
	int m_id = 0;
	bufer_int(b_int_id);
//	bufer_int(b_int_id_2);
	connect_db();
	
	ISC_SHORT rr[1];
	
	XSQLDA* sqlda_output;
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(1));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 1;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = b_int_id;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	
	isc_stmt_handle stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				if(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					m_id = to_int(b_int_id);
				}
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}

	isc_commit_transaction(status_vector, &tr_handle);
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		status.error("ERROR SELECT * FROM LIRY 43");
	}
	free(sqlda_output);
	
	sprintf(sql_query, "SELECT Z_ID FROM ZMINNY_LIRA WHERE Z_ID='%d'", z_id);
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(1));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 1;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = b_int_id;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	int npp_s = 0;
	stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				while(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					npp_s++;
				}
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}
	isc_commit_transaction(status_vector, &tr_handle);
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		status.error("ERROR SELECT * 76");
	}
	free(sqlda_output);
	
	if(npp_s == 1) {
		npp_s = 0;
		sprintf(sql_query, "SELECT NPP_S FROM ZMINNY WHERE Z_ID='%d'", z_id);
		sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(1));
		sqlda_output->version = SQLDA_VERSION1;
		sqlda_output->sqln = 1;
		sqlda_output->sqld = 1;
		sqlda_output->sqlvar[0].sqldata = b_int_id;
		sqlda_output->sqlvar[0].sqlind = &rr[0];
		
		isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
		int n_z_id = 0;
		int npp_s = 0;
		stmt_handle = 0;
		if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
			if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
				if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
					if(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
						npp_s = to_int(b_int_id);
					}
				}
			}
			isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
		}
		isc_commit_transaction(status_vector, &tr_handle);
		if(status_vector[1]) {
			isc_print_status(status_vector);
			isc_rollback_transaction(status_vector, &tr_handle);
			status.error("ERROR SELECT * 76");
		}
		free(sqlda_output);
		
		sprintf(sql_query, "DELETE FROM ZMINNY WHERE Z_ID='%d'", z_id);
		isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
		isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
		isc_commit_transaction(status_vector, &tr_handle);
		if(status_vector[1]) {
			isc_print_status(status_vector);
			isc_rollback_transaction(status_vector, &tr_handle);
			status.error("ERROR DELETE FROM ZMINNY");
		}
		
		if(npp_s) {
			sprintf(sql_query, "DELETE FROM ZMINNY_NPP WHERE Z_ID='%d'", z_id);
			isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
			isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
			isc_commit_transaction(status_vector, &tr_handle);
			if(status_vector[1]) {
				isc_print_status(status_vector);
				isc_rollback_transaction(status_vector, &tr_handle);
				status.error("ERROR ZMINNY_NPP 102 umova deleted");
			}
		}
	}
		
	sprintf(sql_query, "DELETE FROM ZMINNY_LIRA WHERE L_ID='%d' AND Z_ID='%d'", l_id, z_id);
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
	isc_commit_transaction(status_vector, &tr_handle);
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		status.error("ERROR DELETE FROM ZMINNY_LIRA");
	}
	
	disconect_db();
	if(status.success())
		status.info("zminna deleted.", "srv_zm_deleted");
	buff << status.html();
	return buff.str();
}


std::string delete_um(int u_id)
{
	printf("DELETE UMOVA\t\tu_id = %d\n", u_id);
	std::stringstream buff;
	Status status;
	char sql_query[100];
	connect_db();
	sprintf(sql_query, "DELETE FROM UMOVY WHERE U_ID='%d'", u_id);
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
	isc_commit_transaction(status_vector, &tr_handle);
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		status.error("ERROR 17 umova deleted");
	}
	disconect_db();
	if(status.success())
		status.info("umova deleted.", "srv_umova_deleted");
	buff << status.html();
	return buff.str();
}


std::string delete_mash(int m_id)
{
	printf("DELETE MASH\t\tm_id = %d\n", m_id);
	std::stringstream buff;
	char sql_query[200];
	Status status;
	bufer_int(b_int_id);
	ISC_SHORT rr[10];
	int l_id_arr[3] = {0};
	int k = 0;
	sprintf(sql_query, "SELECT L_ID FROM LIRY WHERE M_ID='%d'", m_id);
	connect_db();
	XSQLDA* sqlda_output;
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(1));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 1;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = b_int_id;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	
	isc_stmt_handle stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				while(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					l_id_arr[k] = to_int(b_int_id);
					k++;
					if(k == 3)
						break;
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
		status.error("ERROR SELECT * 39");
		return buff.str();
	}
	free(sqlda_output);
	k = 0;
	while(l_id_arr[k] && k < 3) {
		sprintf(sql_query, "DELETE FROM UMOVY WHERE L_ID='%d'", l_id_arr[k]);
		isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
		isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
		isc_commit_transaction(status_vector, &tr_handle);
		if(status_vector[1]) {
			isc_print_status(status_vector);
			isc_rollback_transaction(status_vector, &tr_handle);
			status.error("ERROR 58");
		}
		k++;
	}
	k = 0;
	int z_id_arr[10] = {0};
	sprintf(sql_query, "SELECT Z_ID FROM ZMINNY WHERE M_ID='%d' AND NPP_S!=0", m_id);
	
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(1));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 1;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = b_int_id;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	
	stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				while(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					z_id_arr[k] = to_int(b_int_id);
					k++;
					if(k == 10)
						break;
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
		status.error("ERROR SELECT * 95");
		return buff.str();
	}
	free(sqlda_output);
	k = 0;
	
	while(z_id_arr[k] && k < 10) {
		sprintf(sql_query, "DELETE FROM ZMINNY_NPP WHERE Z_ID='%d'", z_id_arr[k]);
		isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
		isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
		isc_commit_transaction(status_vector, &tr_handle);
		if(status_vector[1]) {
			isc_print_status(status_vector);
			isc_rollback_transaction(status_vector, &tr_handle);
			status.error("ERROR 109");
		}
		k++;
	}
	
	sprintf(sql_query, "DELETE FROM ZMINNY_LIRA WHERE M_ID='%d'", m_id);
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
	isc_commit_transaction(status_vector, &tr_handle);
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		status.error("ERROR 109");
	}
	sprintf(sql_query, "DELETE FROM ZMINNY WHERE M_ID='%d'", m_id);
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
	isc_commit_transaction(status_vector, &tr_handle);
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		status.error("ERROR 109");
	}
	sprintf(sql_query, "DELETE FROM LIRY WHERE M_ID='%d'", m_id);
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
	isc_commit_transaction(status_vector, &tr_handle);
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		status.error("ERROR 109");
	}
	sprintf(sql_query, "DELETE FROM MASHYNES WHERE M_ID='%d'", m_id);
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
	isc_commit_transaction(status_vector, &tr_handle);
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		status.error("ERROR 109");
	}
	
	disconect_db();
	if(status.success())
		status.info("OK");
	buff << status.html();
	return buff.str();
}


std::string delete_lira(int l_id)
{
	printf("DELETE LIRA\t\tl_id = %d\n", l_id);
	
	Status status;
	std::stringstream buff;
	char sql_query[500];
	sprintf(sql_query, "SELECT M_ID FROM LIRY WHERE L_ID='%d'", l_id);
	int m_id = 0;
	bufer_int(b_int_id);
	connect_db();
	
	ISC_SHORT rr[1];
	
	XSQLDA* sqlda_output;
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(1));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 1;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = b_int_id;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	
	isc_stmt_handle stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				if(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					m_id = to_int(b_int_id);
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
		status.error("ERROR SELECT * FROM LIRY 43");
		return buff.str();
	}
	free(sqlda_output);
	
	int zm_id_arr[10] = {0};
	sprintf(sql_query, "SELECT Z_ID FROM (SELECT * FROM (SELECT ZL_ID, Z_ID, L_ID FROM ZMINNY_LIRA ZMLR1 WHERE M_ID='%d') ZMLR2 GROUP BY Z_ID HAVING COUNT(Z_ID)=1) ZMLR3 WHERE L_ID='%d'", m_id, l_id);
	
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(1));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 1;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = b_int_id;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	int k = 0;
	stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				while(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					zm_id_arr[k] = to_int(b_int_id);
					k++;
					if(k == 10)
						break;
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
		status.error("ERROR SELECT * FROM LIRY 83");
		return buff.str();
	}
	free(sqlda_output);
	
	int zm_npp_id_arr[10] = {0};
	k = 0;
	sprintf(sql_query, "SELECT ZMLR4.Z_ID AS Z_ID FROM (SELECT Z_ID FROM (SELECT * FROM (SELECT ZL_ID, Z_ID, L_ID FROM ZMINNY_LIRA ZMLR1 WHERE M_ID='%d') ZMLR2 GROUP BY Z_ID HAVING COUNT(Z_ID)=1) ZMLR3 WHERE L_ID='%d') ZMLR4 LEFT JOIN ZMINNY ZM ON ZMLR4.Z_ID=ZM.Z_ID WHERE ZM.NPP_S!=0", m_id, l_id);
	
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(1));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 1;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = b_int_id;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	k = 0;
	stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				while(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					zm_npp_id_arr[k] = to_int(b_int_id);
					k++;
					if(k == 10)
						break;
				}
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}

	isc_commit_transaction(status_vector, &tr_handle);
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		status.error("ERROR SELECT * FROM LIRY 122");
		return buff.str();
	}
	free(sqlda_output);
	
	sprintf(sql_query, "DELETE FROM UMOVY WHERE L_ID=%d", l_id);
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
	isc_commit_transaction(status_vector, &tr_handle);
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		status.error("ERROR INSERT INTO ZMINNY NPP_S");
	}
	
	sprintf(sql_query, "DELETE FROM ZMINNY_LIRA WHERE L_ID=%d", l_id);
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
	isc_commit_transaction(status_vector, &tr_handle);
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		status.error("ERROR 142");
	}
	
	k = 0;
	while(zm_npp_id_arr[k] && k < 10) {
		sprintf(sql_query, "DELETE FROM ZMINNY_NPP WHERE Z_ID=%d", zm_npp_id_arr[k]);
		isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
		isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
		isc_commit_transaction(status_vector, &tr_handle);
		if(status_vector[1]) {
			isc_print_status(status_vector);
			isc_rollback_transaction(status_vector, &tr_handle);
			status.error("ERROR 154");
		}
		k++;
	}
	k = 0;
	while(zm_id_arr[k] && k < 10) {
		sprintf(sql_query, "DELETE FROM ZMINNY WHERE Z_ID=%d", zm_id_arr[k]);
		isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
		isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
		isc_commit_transaction(status_vector, &tr_handle);
		if(status_vector[1]) {
			isc_print_status(status_vector);
			isc_rollback_transaction(status_vector, &tr_handle);
			status.error("ERROR 167");
		}
		k++;
	}
	
	sprintf(sql_query, "DELETE FROM LIRY WHERE L_ID=%d", l_id);
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query, SQL_DIALECT_V6, /* sqlda */NULL);
	isc_commit_transaction(status_vector, &tr_handle);
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		status.error("ERROR 179");
	}
	disconect_db();
	if(status.success())
		buff << "<meta http-equiv=\"refresh\" content=\"0;url=./?m_id=" << m_id << "\">";
	else
		buff << status.html();
	return buff.str();
}

