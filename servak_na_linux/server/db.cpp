
#ifdef __linux__
	#include <ibase.h>
	#ifndef UNISTD_H
		#include <unistd.h>
	#endif
#elif _WIN32
	#include "c:\Program Files (x86)\Firebird\Firebird_3_0\include\ibase.h"
#endif

#ifndef STDLIB_H
	#include <stdlib.h>
#endif

#ifndef CSTDLIB
	#include <cstring>
#endif

#ifndef MALLOC_H
	#include <malloc.h>
#endif

#define to_str(aa) ((VARY*)aa)->vary_string
#define norm_str(aa) (((VARY*)aa)->vary_string)[((VARY*)aa)->vary_length] = 0
#define to_int(aa) *(int*)aa
#define to_short(aa) *(short*)aa
#define to_char(aa) *(char*)aa
#define to_float(aa) *(float*)aa
#define to_double(aa) *(double*)aa

#define bufer_str(aa, bb) char aa[(bb) + 4]; memset(aa, 0, (bb) + 4)
#define bufer_int(aa) char aa[8]
#define bufer_short(aa) char aa[6]
#define bufer_char(aa) char aa[5]
#define bufer_float(aa) char aa[8]
#define bufer_double(aa) char aa[12]

#ifdef MAIN_SERVER

#if defined(__THREADS) || defined(__FORK)
pthread_mutex_t db_mutex;
#endif


std::string select_mash(int prf);
std::string select_liry(int prf);
std::string select_zminny(int prf);
std::string select_zminny_npp(int prf);
std::string select_umovy(int prf);
std::string select_zminny_lira(int prf);
#endif

isc_db_handle db_handle;
isc_tr_handle tr_handle;
ISC_STATUS status_vector[20];


int disconect_db()
{
	isc_detach_database(status_vector, &db_handle);
#ifdef MAIN_SERVER
#if defined(__THREADS) || defined(__FORK)
pthread_mutex_unlock(&db_mutex);
#endif
#endif
	return 0;
}


std::string request_db(int prf)
{
	std::stringstream buff;
	buff << "<!DOCTYPE html>";
	buff << "<html>";
	buff << "<head>";
		buff << "<title>db</title>";
		buff << "<meta http-equiv='Content-Type' content='text/html; charset=UTF-8' />";
		buff << "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">";
		buff << "<link rel=\"icon\" href=\"css/famfam/gear.png\" type=\"image/png\">";
	buff << "</head>";
	buff << "<body>";
	buff << "<pre style='margin-left: 5%;'>";
	
	buff << select_mash(prf);
	buff << select_liry(prf);
	buff << select_zminny(prf);
	buff << select_zminny_npp(prf);
	buff << select_umovy(prf);
	buff << select_zminny_lira(prf);
	
	buff << "</pre>";
	buff << "</body></html>";
	return buff.str();
}


int connect_db()
{
#ifdef MAIN_SERVER
#if defined(__THREADS) || defined(__FORK)
pthread_mutex_lock(&db_mutex);
#endif
#endif
#ifdef TEST_DB
	char db_name[] = "_database.fdb";
#else
	char db_name[] = "database.fdb";
#endif
	char user_name[] = "username";
	char user_password[] = "password";
	
	char dpb_buffer[256], *dpb, *p;
	db_handle = 0;
	short dpb_length;
	dpb = dpb_buffer;
	*dpb++ = isc_dpb_version1;
	*dpb++ = isc_dpb_user_name;
	*dpb++ = strlen(user_name);
	for(p = user_name; *p;)
		*dpb++ = *p++;
	*dpb++ = isc_dpb_password;
	*dpb++ = strlen(user_password);
	for(p = user_password; *p;)
		*dpb++ = *p++;
	dpb_length = dpb - dpb_buffer;
	
	isc_attach_database(status_vector, 0, db_name, &db_handle, dpb_length, dpb_buffer);
	if(status_vector[1]) {
		isc_print_status(status_vector);
		tr_handle = 0;
		db_handle = 0;
		if(isc_create_database(status_vector, 0, db_name, &db_handle, dpb_length, dpb_buffer, 0) == 0) {
			FILE *ff = fopen("www/DBscripts/firebird.sql", "r");
			fseek(ff, 0L, SEEK_END);
			int sz = ftell(ff), nsz;
			rewind(ff);
			
			char *sql_query = (char*)malloc((sz + 1) * sizeof(char));
			for(int i = 0; i < sz; i++) {
				*(sql_query + i) = fgetc(ff);
				if(*(sql_query + i) == 10 || *(sql_query + i) == 13)
					*(sql_query + i) = ' ';
				if(*(sql_query + i - 2) == '-' && *(sql_query + i - 1) == '-' && *(sql_query + i) == '-') {
					*(sql_query + i - 2) = 0;
					*(sql_query + i - 1) = ' ';
					*(sql_query + i) = ' ';
					nsz = i - 2;
				}
			}
			*(sql_query + sz) = 0;
//			char result[256];
			int i = 0;
			
			do {
				if(i)
					i++;
				isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
				printf("%d   %s\n", i, sql_query + i);
				isc_dsql_execute_immediate(status_vector, &db_handle, &tr_handle, 0, sql_query + i, SQL_DIALECT_V6, /* sqlda */NULL);
				if (status_vector[1]) {
					isc_print_status(status_vector);
					isc_rollback_transaction(status_vector, &tr_handle);
					disconect_db();
					remove(db_name);
					return -1; // Помилка виконання SQL-запиту
				}
				isc_commit_transaction(status_vector, &tr_handle);
				for(; *(sql_query + i); i++);
			}while(i != nsz);
			free(sql_query);
#ifdef MAIN_SERVER
			disconect_db();
			select_mash(1);
			select_liry(1);
			select_zminny(1);
			select_zminny_npp(1);
			select_umovy(1);
			select_zminny_lira(1);
			connect_db();
#endif
		}
		else {
			puts("err create");
			isc_print_status(status_vector);
			return(1);	
		}
			
	}
	return 0;
}


typedef struct vary {
	short vary_length;
	char vary_string[1];
} VARY;


std::string select_mash(int prf)
{
	std::stringstream buff;
	char strbuff[500];
	connect_db();
	bufer_int(m_id);
	bufer_str(name, 60);
	bufer_str(m1, 1000);
	bufer_str(m2, 1000);
	ISC_SHORT rr[4];
	
	XSQLDA* sqlda_output;
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(4));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 4;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = m_id;
	sqlda_output->sqlvar[1].sqldata = name;
	sqlda_output->sqlvar[2].sqldata = m1;
	sqlda_output->sqlvar[3].sqldata = m2;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	sqlda_output->sqlvar[1].sqlind = &rr[1];
	sqlda_output->sqlvar[2].sqlind = &rr[2];
	sqlda_output->sqlvar[3].sqlind = &rr[3];
	
	char sql_query[] = "SELECT * FROM MASHYNES ORDER BY M_ID";
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	
	isc_stmt_handle stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				sprintf(strbuff, "\n=========================================================================================================================================================================================================\nMASHYNES\n");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%10.10s ||  ", sqlda_output->sqlvar[0].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%30.30s ||  ", sqlda_output->sqlvar[1].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%70.70s ||  ", sqlda_output->sqlvar[2].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%70.70s", sqlda_output->sqlvar[3].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "\n---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				while(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					sprintf(strbuff, "\n%10d ||  ", *(int*)m_id);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%30.30s ||  ", ((VARY*)name)->vary_string);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%70.70s ||  ", ((VARY*)m1)->vary_string);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%70.70s", ((VARY*)m2)->vary_string);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
				}
				sprintf(strbuff, "\n=========================================================================================================================================================================================================\n");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "\n");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}

	isc_commit_transaction(status_vector, &tr_handle);
	
	
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		return buff.str();
	}
	free(sqlda_output);
	disconect_db();
	return buff.str();
}


std::string select_liry(int prf)
{
	std::stringstream buff;
	char strbuff[500];
	connect_db();
	bufer_int(l_id);
	bufer_int(m_id);
	bufer_str(name, 60);
	bufer_int(magaz);
	bufer_str(form, 100);
	bufer_str(form_zv, 100);
	bufer_int(br_kol_lir);
	ISC_SHORT rr[7];
	
	XSQLDA* sqlda_output;
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(7));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 7;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = l_id;
	sqlda_output->sqlvar[1].sqldata = m_id;
	sqlda_output->sqlvar[2].sqldata = name;
	sqlda_output->sqlvar[3].sqldata = magaz;
	sqlda_output->sqlvar[4].sqldata = form;
	sqlda_output->sqlvar[5].sqldata = form_zv;
	sqlda_output->sqlvar[6].sqldata = br_kol_lir;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	sqlda_output->sqlvar[1].sqlind = &rr[1];
	sqlda_output->sqlvar[2].sqlind = &rr[2];
	sqlda_output->sqlvar[3].sqlind = &rr[3];
	sqlda_output->sqlvar[4].sqlind = &rr[4];
	sqlda_output->sqlvar[5].sqlind = &rr[5];
	sqlda_output->sqlvar[6].sqlind = &rr[6];
	
	char sql_query[] = "SELECT * FROM LIRY ORDER BY L_ID";
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	isc_stmt_handle stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				sprintf(strbuff, "\n=========================================================================================================================================================================================================\nLIRY\n");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%8.8s ||  ", sqlda_output->sqlvar[0].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%8.8s ||  ", sqlda_output->sqlvar[1].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%3.3s ||  ", sqlda_output->sqlvar[3].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%55.55s ||  ", sqlda_output->sqlvar[4].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%55.55s ||  ", sqlda_output->sqlvar[5].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%3.3s ||  ", sqlda_output->sqlvar[6].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%30.30s", sqlda_output->sqlvar[2].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "\n---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				while(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					sprintf(strbuff, "\n%8d ||  ", *(int*)l_id);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%8d ||  ", *(int*)m_id);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%3d ||  ", *(int*)magaz);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%55.55s ||  ", ((VARY*)form)->vary_string);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%55.55s ||  ", ((VARY*)form_zv)->vary_string);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%3d ||  ", *(int*)br_kol_lir);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "        %s", ((VARY*)name)->vary_string);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
				}
				sprintf(strbuff, "\n=========================================================================================================================================================================================================\n");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "\n");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
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
		return buff.str();
	}
	disconect_db();
	return buff.str();
}


std::string select_zminny(int prf)
{
	std::stringstream buff;
	char strbuff[500];
	connect_db();
	bufer_int(z_id);
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
	sqlda_output->sqlvar[0].sqldata = z_id;
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
	
	char sql_query[] = "SELECT * FROM ZMINNY ORDER BY Z_ID";
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	
	isc_stmt_handle stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				sprintf(strbuff, "\n=========================================================================================================================================================================================================\nZMINNY\n");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%8.8s ||  ", sqlda_output->sqlvar[0].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%8.8s ||  ", sqlda_output->sqlvar[1].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%s ||  ", sqlda_output->sqlvar[3].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%20.20s ||  ", sqlda_output->sqlvar[4].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%6.6s ||  ", sqlda_output->sqlvar[5].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%30.30s", sqlda_output->sqlvar[2].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "\n---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				while(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					sprintf(strbuff, "\n%8d ||  ", *(int*)z_id);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%8d ||  ", *(int*)m_id);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "    %c ||  ", *(char*)bukva);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%20.16g ||  ", *(double*)znachennja);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%6d ||  ", *(int*)npp_s);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "                         %s", ((VARY*)name)->vary_string);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
				}
				sprintf(strbuff, "\n=========================================================================================================================================================================================================\n");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "\n");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
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
		return buff.str();
	}
	disconect_db();
	return buff.str();
}


std::string select_zminny_npp(int prf)
{
	std::stringstream buff;
	char strbuff[500];
	connect_db();
	bufer_int(n_id);
	bufer_int(z_id);
	bufer_double(znachennja);
	bufer_str(umova, 50);
	bufer_str(coment, 100);
	ISC_SHORT rr[5];
	
	XSQLDA* sqlda_output;
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(5));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 5;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = n_id;
	sqlda_output->sqlvar[1].sqldata = z_id;
	sqlda_output->sqlvar[2].sqldata = znachennja;
	sqlda_output->sqlvar[3].sqldata = umova;
	sqlda_output->sqlvar[4].sqldata = coment;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	sqlda_output->sqlvar[1].sqlind = &rr[1];
	sqlda_output->sqlvar[2].sqlind = &rr[2];
	sqlda_output->sqlvar[3].sqlind = &rr[3];
	sqlda_output->sqlvar[4].sqlind = &rr[4];
	
	char sql_query[] = "SELECT * FROM ZMINNY_NPP ORDER BY N_ID";
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	
	isc_stmt_handle stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				sprintf(strbuff, "\n=========================================================================================================================================================================================================\nZMINNY_NPP\n");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%8.8s ||  ", sqlda_output->sqlvar[0].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%8.8s ||  ", sqlda_output->sqlvar[1].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%20.20s ||  ", sqlda_output->sqlvar[2].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%30.30s ||  ", sqlda_output->sqlvar[3].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%30.30s", sqlda_output->sqlvar[4].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "\n---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				while(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					sprintf(strbuff, "\n%8d ||  ", *(int*)n_id);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%8d ||  ", *(int*)z_id);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%20.16g ||  ", *(double*)znachennja);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%30.30s ||  ", ((VARY*)umova)->vary_string);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "                         %s", ((VARY*)coment)->vary_string);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
				}
				sprintf(strbuff, "\n=========================================================================================================================================================================================================\n");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "\n");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}

	isc_commit_transaction(status_vector, &tr_handle);
	
	
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		return buff.str();
	}
	free(sqlda_output);
	disconect_db();
	return buff.str();
}


std::string select_umovy(int prf)
{
	std::stringstream buff;
	char strbuff[500];
	connect_db();
	bufer_int(u_id);
	bufer_int(l_id);
	bufer_str(umova, 100);
	ISC_SHORT rr[3];
	
	XSQLDA* sqlda_output;
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(3));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 3;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = u_id;
	sqlda_output->sqlvar[1].sqldata = l_id;
	sqlda_output->sqlvar[2].sqldata = umova;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	sqlda_output->sqlvar[1].sqlind = &rr[1];
	sqlda_output->sqlvar[2].sqlind = &rr[2];
	
	char sql_query[] = "SELECT * FROM UMOVY ORDER BY U_ID";
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	
	isc_stmt_handle stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				sprintf(strbuff, "\n=========================================================================================================================================================================================================\nUMOVY\n");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%8.8s ||  ", sqlda_output->sqlvar[0].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%8.8s ||  ", sqlda_output->sqlvar[1].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%100.100s", sqlda_output->sqlvar[2].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "\n---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				while(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					sprintf(strbuff, "\n");
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%8d ||  ", *(int*)u_id);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%8d ||  ", *(int*)l_id);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%100.100s", ((VARY*)umova)->vary_string);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
				}
				sprintf(strbuff, "\n=========================================================================================================================================================================================================\n");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "\n");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}

	isc_commit_transaction(status_vector, &tr_handle);
	
	
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		return buff.str();
	}
	free(sqlda_output);
	disconect_db();
	return buff.str();
}


std::string select_zminny_lira(int prf)
{
	std::stringstream buff;
	char strbuff[500];
	connect_db();
	bufer_int(zm_id);
	bufer_int(z_id);
	bufer_int(l_id);
	bufer_int(m_id);
	ISC_SHORT rr[4];
	
	XSQLDA* sqlda_output;
	sqlda_output = (XSQLDA*)malloc(XSQLDA_LENGTH(4));
	sqlda_output->version = SQLDA_VERSION1;
	sqlda_output->sqln = 4;
	sqlda_output->sqld = 1;
	sqlda_output->sqlvar[0].sqldata = zm_id;
	sqlda_output->sqlvar[1].sqldata = z_id;
	sqlda_output->sqlvar[2].sqldata = l_id;
	sqlda_output->sqlvar[3].sqldata = m_id;
	sqlda_output->sqlvar[0].sqlind = &rr[0];
	sqlda_output->sqlvar[1].sqlind = &rr[1];
	sqlda_output->sqlvar[2].sqlind = &rr[2];
	sqlda_output->sqlvar[3].sqlind = &rr[2];
	
	char sql_query[] = "SELECT * FROM ZMINNY_LIRA ORDER BY ZL_ID";
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	
	isc_stmt_handle stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query, 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				sprintf(strbuff, "\n=========================================================================================================================================================================================================\nZMINNY_LIRA\n");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%8.8s ||  ", sqlda_output->sqlvar[0].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%8.8s ||  ", sqlda_output->sqlvar[1].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%8.8s ||  ", sqlda_output->sqlvar[2].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "%8.8s", sqlda_output->sqlvar[3].sqlname);
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "\n---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				while(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					sprintf(strbuff, "\n");
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%8d ||  ", *(int*)zm_id);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%8d ||  ", *(int*)z_id);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%8d ||  ", *(int*)l_id);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
					sprintf(strbuff, "%8d", *(int*)m_id);
					buff << strbuff;
					if(prf)
						printf("%s", strbuff);
				}
				sprintf(strbuff, "\n=========================================================================================================================================================================================================\n");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
				sprintf(strbuff, "\n");
				buff << strbuff;
				if(prf)
					printf("%s", strbuff);
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}

	isc_commit_transaction(status_vector, &tr_handle);
	
	
	if(status_vector[1]) {
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		return buff.str();
	}
	free(sqlda_output);
	disconect_db();
	return buff.str();
}


#ifndef MAIN_SERVER
int main()
{
	if(!connect_db()) {
		disconect_db();
		select_mash(1);
		select_liry(1);
		select_zminny(1);
		select_zminny_npp(1);
		select_umovy(1);
		select_zminny_lira(1);
	}
	return 0;
}
#endif

