

std::string index_cpp_get(int method, int m_id, const char* name)
{
	printf("GEN INDEX.HTML\t%s\n", method == GET ? "GET" : "POST");
	
	std::stringstream buff;
	buff << "<!DOCTYPE html>";
	buff << "<html>";
	buff << "<head>";
		buff << "<title>Lira Calc Config Editor</title>";
		buff << "<meta http-equiv='Content-Type' content='text/html; charset=UTF-8' />";
		buff << "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">";
	 	buff << "<link rel=\"stylesheet\" type=\"text/css\" href=\"css/default.css\">";
		buff << "<link rel=\"icon\" href=\"css/famfam/gear.png\" type=\"image/png\">";
	
	buff << "</head>";
	buff << "<body>";
		buff << "<!-- Бокове меню навігації -->";
		
		buff << "<nav>";
			buff << "<ul>";
				buff << "<h1><a href='./' class='icon refresh_icon'>Машини</a></h1>";
				buff << "<form action=\"\" method=\"POST\">";
					buff << "<input type=\"text\" name=\"search_m\" id=\"search_m\" placeholder=\"Poshuk za nazvoyu\">";
					buff << "<input type=\"submit\" value=\"Тьрсене\">";
				buff << "</form>";
	
	
	connect_db();
	
	int praporec_nicjoho_ne_znajdeno = 1;
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
	if(method == POST) {
		sql_query << "WHERE LOWER(NAME) LIKE LOWER('%" << name << "%') ";
	}
	sql_query << " ORDER BY M_ID";
	
	
	isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
	
	isc_stmt_handle stmt_handle = 0;
	if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
		if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, sql_query.str().c_str(), 1, sqlda_output) == 0) {
			if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
				while(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
					praporec_nicjoho_ne_znajdeno = 2;
					norm_str(buf_name);
					buff << "<li><a href='?m_id=" << *(int*)buf_m_id << "&name=" << ((VARY*)buf_name)->vary_string;
					buff << "&json=liry' class='icon view_icon mashyn-show-details' id='menu-mashyn-show-details-id" << *(int*)buf_m_id << "'>" << ((VARY*)buf_name)->vary_string << "</a><li>";
					buff << "<li><a href='?m_id=" << *(int*)buf_m_id << "&name=" << ((VARY*)buf_name)->vary_string << "&json=liry' class='icon edit_icon mashyn-edit-details' id='menu-mashyn-edit-details-id";
					buff << *(int*)buf_m_id << "' style='display: none;'>" << ((VARY*)buf_name)->vary_string << "</a><li>";
				}
			}
		}
		isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
	}

	isc_commit_transaction(status_vector, &tr_handle);
	if(praporec_nicjoho_ne_znajdeno == 1) {
		isc_start_transaction(status_vector, &tr_handle, 1, &db_handle, 0, NULL);
		praporec_nicjoho_ne_znajdeno = 0;
		stmt_handle = 0;
		if (isc_dsql_allocate_statement(status_vector, &db_handle, &stmt_handle) == 0) {
			if (isc_dsql_prepare(status_vector, &tr_handle, &stmt_handle, 0, "SELECT NAME, M_ID FROM MASHYNES ORDER BY M_ID", 1, sqlda_output) == 0) {
				if (isc_dsql_execute(status_vector, &tr_handle, &stmt_handle, 1, NULL) == 0) {
					while(isc_dsql_fetch(status_vector, &stmt_handle, 1, sqlda_output) == 0) {
						norm_str(buf_name);
						buff << "<li><a href='?m_id=" << *(int*)buf_m_id << "&name=" << ((VARY*)buf_name)->vary_string;
						buff << "&json=liry' class='icon view_icon mashyn-show-details' id='menu-mashyn-show-details-id" << *(int*)buf_m_id << "'>" << ((VARY*)buf_name)->vary_string << "</a><li>";
						buff << "<li><a href='?m_id=" << *(int*)buf_m_id << "&name=" << ((VARY*)buf_name)->vary_string << "&json=liry' class='icon edit_icon mashyn-edit-details' id='menu-mashyn-edit-details-id";
						buff << *(int*)buf_m_id << "' style='display: none;'>" << ((VARY*)buf_name)->vary_string << "</a><li>";
					}
				}
			}
			isc_dsql_free_statement(status_vector, &stmt_handle, DSQL_close);
		}

		isc_commit_transaction(status_vector, &tr_handle);
	}
	
	if(status_vector[1]) {
		printf("ЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖ\n");
		isc_print_status(status_vector);
		isc_rollback_transaction(status_vector, &tr_handle);
		disconect_db();
		printf("ЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖЖ\n");
		buff << "\n\nERRORE\n\n";
		return buff.str();
	}
	free(sqlda_output);
	
	disconect_db();
	
	
				buff << "<li><a href='?index=add-mash' class='icon add_icon add-mash' tabindex='-1'>Add new mashin</a></li>";
			buff << "</ul>";
			buff << "<p><a href='FireBird' class='icon user_icon mysqlviev'>преглед на БД</a>";
		buff << "</nav>";
		buff << "<!-- Контент сторінки -->";
		buff << "<main>";
			buff << "<div class='pole_opysu' id='mashyn-details'>";
			/*
			if(praporec_nicjoho_ne_znajdeno != 0) {
				buff <<  "<h2>Vyberit' verstat zi spysku zliva</h2>";
				buff <<  "<h2>(abo natysnit' 'add')</h2>";
				buff <<  "<h2>((abo skorystajtes' poshukom))</h2>";
				buff <<  "<p>tut budut' vidobrazheni jiji detali</p>";
			}
			else {
				buff << "<p>Nichogo ne znajdeno</p>";
			}
			*/
			buff << "</div>";
			buff << "<button id=\"menuToggle\">⋮</button>";
			buff << "<!--h1>Заголовок сторінки</h1>";
			buff << "<p>Тут розміщений вміст сторінки, який може бути оформлений за зразком вікіпедії.</p>";
			buff << "<p>Також можна додати таблиці, списки, зображення тощо.</p-->";
			buff << "<div class='pole_instr' id='instr_coment'></div>";
			buff << "<div class='pole_instr' id='instr_zvity'>";
			buff << "</div>";
		buff << "</main>";
		buff << "<script type=\"text/javascript\" src=\"js/jquery-2.1.3.js\"></script>";
		buff << "<script type=\"text/javascript\" src=\"js/application.js\"></script>";
		buff << "<script type=\"text/javascript\" src=\"./js/edit.js\"></script>";
		buff << "<script type=\"text/javascript\" src=\"./js/view.js\"></script>";
		buff << "<script type=\"text/javascript\" src=\"./js/coment.js\"></script>";
		buff << "<script type=\"text/javascript\" src=\"./js/validacija.js\"></script>";
		if(method == GET && m_id != -1) {
			buff << "<script type='text/javascript'>";
				buff << "$(document).ready(function() {";
				buff << "$('#menu-mashyn-edit-details-id" << m_id << "').click();";
				buff << "});";
			buff << "</script>";
		}
		buff << "<script>";
			buff << "var instr_zvity_mem = localStorage.getItem('instr_zvity');";
			buff << "if(instr_zvity_mem != null && instr_zvity_mem.length > 20000) {";
				buff << "instr_zvity_mem = instr_zvity_mem.slice(-20000);";
				buff << "var index = instr_zvity_mem.indexOf('<p');";
				buff << "instr_zvity_mem = instr_zvity_mem.slice(index);";
				buff << "localStorage.setItem('instr_zvity', instr_zvity_mem);";
			buff << "}";
		
			buff << "$('#instr_zvity').html(instr_zvity_mem || '');";
			buff << "$('#instr_zvity').scrollTop($('#instr_zvity').prop('scrollHeight'));";
		buff << "</script>";
		buff << "</body>";
	buff << "</html>";
//	printf(buff.str().c_str());
	return buff.str();
}
