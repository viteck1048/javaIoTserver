

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
		// Спільний каркас сайту + власні стилі LiraCalc — як на MachineTime і релейках
	 	buff << "<link rel=\"stylesheet\" type=\"text/css\" href=\"css/home_page.css\">";
	 	buff << "<link rel=\"stylesheet\" type=\"text/css\" href=\"css/default.css\">";
		buff << "<link rel=\"icon\" href=\"css/famfam/gear.png\" type=\"image/png\">";

	buff << "</head>";
	buff << "<body>";
	buff << "<div class=\"main-container\">";

		// Верхня панель — за зразком дочірніх сторінок: лише логотип, без логіну/виходу
		buff << "<nav>";
			buff << "<div class=\"container\">";
				buff << "<ul class=\"nav-links\">";
					buff << "<li><a href=\"https://mijservak.pp.ua/\" id=\"nav-home\" class=\"logo\">MijServak</a></li>";
				buff << "</ul>";
			buff << "</div>";
		buff << "</nav>";

		// Меню на мобілці керується виключно свайпом. Кнопка схована в CSS і
		// обробника не має — лишається лише як частина спільної розмітки, так
		// само як на MachineTime і релейках.
		buff << "<button id=\"menuToggle\">&#9776;</button>";

		buff << "<div class=\"content\">";

		buff << "<div class=\"sidebar\" id=\"sidebar\">";

	// Пункти меню збираємо окремо: заголовок треба віддати вже ПІСЛЯ запиту,
	// бо іконка «показати всі» з'являється лише тоді, коли список відфільтрований
	// пошуком, а це відомо тільки за результатом запиту.
	std::stringstream list_buff;

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
					list_buff << "<li><a href='?m_id=" << *(int*)buf_m_id << "&name=" << ((VARY*)buf_name)->vary_string;
					list_buff << "&json=liry' class='mashyn-show-details' id='menu-mashyn-show-details-id" << *(int*)buf_m_id << "'>" << ((VARY*)buf_name)->vary_string << "</a></li>";
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
						list_buff << "<li><a href='?m_id=" << *(int*)buf_m_id << "&name=" << ((VARY*)buf_name)->vary_string;
						list_buff << "&json=liry' class='mashyn-show-details' id='menu-mashyn-show-details-id" << *(int*)buf_m_id << "'>" << ((VARY*)buf_name)->vary_string << "</a></li>";
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


			// praporec == 2 означає, що ПЕРШИЙ запит повернув рядки. Для POST це і
			// є успішний пошук, тобто список відфільтрований і неповний — тільки
			// тоді має сенс іконка «показати всі». Для GET той самий praporec == 2
			// означає просто повний список, і скидати нічого.
			bool pokazaty_vsi = (method == POST && praporec_nicjoho_ne_znajdeno == 2);

			buff << "<h3 class=\"sidebar-title\"><span data-i18n=\"machines\">Mashyny</span>";
			if(pokazaty_vsi) {
				buff << "<a href='./' class='refresh-link' data-i18n-title='show_all_machines' data-i18n-aria='show_all_machines' title='Pokazaty vsi verstaty' aria-label='Pokazaty vsi verstaty'></a>";
			}
			buff << "</h3>";

			buff << "<form class=\"sidebar-search\" action=\"\" method=\"POST\">";
				buff << "<input type=\"text\" name=\"search_m\" id=\"search_m\" data-i18n-placeholder=\"search_by_name\" placeholder=\"Poshuk za nazvoju\">";
				buff << "<input type=\"submit\" data-i18n-value=\"search_button\" value=\"Znajty\">";
			buff << "</form>";

			buff << "<ul class=\"links-list\" id=\"links-list\">";
				buff << list_buff.str();
				buff << "<li><a href='?index=add-mash' class='icon add_icon add-mash' tabindex='-1' data-i18n='add_machine'>Add new mashin</a></li>";
			buff << "</ul>";
		buff << "</div>";	// #sidebar

		buff << "<div class=\"main-content\">";
			buff << "<div class='pole_opysu' id='mashyn-details'></div>";

			// Пам'ять інструкцій. Схована (.pole_instr -> display:none), але не
			// вирізана — у неї пише xhttp-відповідь у application.js.
			buff << "<div class='pole_instr' id='instr_coment'></div>";
		buff << "</div>";	// .main-content

		buff << "</div>";	// .content

		buff << "<footer class=\"thin-footer\">";
			buff << "<a href='FireBird' class='footer-link icon user_icon mysqlviev' data-i18n='view_db'>perehljad BD</a>";
			buff << "<p data-i18n=\"footer_text\">&copy; 2025 MijServak. Vsi prava zahyshcheno.</p>";
			// Кнопка консолі — праворуч, дзеркально до посилання на БД. Місце під
			// іконку зарезервоване завжди; картинку в нього ставить JS, дублюючи
			// іконку ОСТАННЬОГО повідомлення в консолі (див. lcUpdateConsoleMark).
			buff << "<a href='#' class='footer-console' id='lc-console-btn'>";
				buff << "<span class='console-mark' id='lc-console-mark'></span><span data-i18n='console'>konsol</span>";
			buff << "</a>";
		buff << "</footer>";

		// Плаваюче вікно консолі. Сам #instr_zvity переїхав сюди — тобто
		// push_my_console() і далі дописує в нього, а localStorage працює як
		// працював. Ніякого дзеркалення вмісту немає.
		buff << "<div id='lc-console' role='dialog' data-i18n-aria='console_title' aria-label='Konsol zvitiv'>";
			buff << "<div id='lc-console-bar'>";
				buff << "<span data-i18n='console'>Konsol</span>";
				buff << "<span class='lc-hint' data-i18n='console_hint'>tap — zhornuty</span>";
			buff << "</div>";
			buff << "<div id='instr_zvity'></div>";
		buff << "</div>";

	buff << "</div>";	// .main-container
		buff << "<script type=\"text/javascript\" src=\"js/jquery-2.1.3.js\"></script>";
		buff << "<script type=\"text/javascript\" src=\"./js/translations.js\"></script>";
		buff << "<script type=\"text/javascript\" src=\"./js/translate.js\"></script>";
		buff << "<script type=\"text/javascript\" src=\"js/application.js\"></script>";
		buff << "<script type=\"text/javascript\" src=\"./js/edit.js\"></script>";
		buff << "<script type=\"text/javascript\" src=\"./js/view.js\"></script>";
		buff << "<script type=\"text/javascript\" src=\"./js/coment.js\"></script>";
		buff << "<script type=\"text/javascript\" src=\"./js/validacija.js\"></script>";
		if(method == GET && m_id != -1) {
			// Прихованих пунктів меню (mashyn-edit-details) більше немає — вони
			// плодили порожній <li> на кожен верстат і роздували відступи.
			// Href для редактора беремо просто з видимого пункту: він містить
			// і m_id, і name. У C++ його не зібрати — KM_server.cpp:491 передає
			// сюди name як NULL.
			buff << "<script type='text/javascript'>";
				buff << "$(document).ready(function() {";
				buff << "var a = document.getElementById('menu-mashyn-show-details-id" << m_id << "');";
				buff << "if(a) { markActiveMashyn(a.getAttribute('href')); edit_mash(a.getAttribute('href')); }";
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
			// Позначка біля кнопки консолі має відповідати останньому повідомленню
			// вже при завантаженні, а не лише після нового запису.
			buff << "lcUpdateConsoleMark();";
		buff << "</script>";
		buff << "</body>";
	buff << "</html>";
//	printf(buff.str().c_str());
	return buff.str();
}
