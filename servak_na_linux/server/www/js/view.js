

function hide_umovy(href) {
	$.getJSON(href, function(json_data) {
		var res = '#usl-det_' + json_data.l_id;
		$(res).html('');
		res = '#umovy_' + json_data.l_id;
		const element = document.querySelector(res);
		element.classList.replace('umovy-hide-details', 'umovy-show-details');
	});
}


function show_umovy(href) {
	$.getJSON(href, function(json_data) {
		var res = '#usl-det_' + json_data.l_id;
		var html = '';
		html += "<usl_det>";
		$.each(json_data.umovy, function(i, umovy) {
			html += "<p>" + umovy.UMOVA + "</p>";
		});
		html += "</usl_det>";
		$(res).html(html);
		res = '#umovy_' + json_data.l_id;
		const element = document.querySelector(res);
		element.classList.replace('umovy-show-details', 'umovy-hide-details');
		
	});
}


function hide_zm_npp(href) {
	$.getJSON(href, function(json_data) {
		var res = '#npp-det_' + json_data.z_id + '_' + json_data.l_id;
		$(res).html('');
		res = '#zminni_npp_' + json_data.z_id + '_' + json_data.l_id;
		const element = document.querySelector(res);
		element.classList.replace('zminni_npp-hide-details', 'zminni_npp-show-details');
	});
}


function show_zm_npp(href) {
	$.getJSON(href, function(json_data) {
		var res = '#npp-det_' + json_data.z_id + '_' + json_data.l_id;
		var html = '';
		
		html += "<npp_det>";
		$.each(json_data.zm_npp, function(i, zm_npp) {
			html += "<p>" + zm_npp.COMENT + "</p>";
		});
		
		html += "</npp_det>";
		
		$(res).html(html);
		res = '#zminni_npp_' + json_data.z_id + '_' + json_data.l_id;
		const element = document.querySelector(res);
		element.classList.replace('zminni_npp-show-details', 'zminni_npp-hide-details');
	});
}


function show_mash(href) {
	$.getJSON(href, function(json_data) {
		var html = '';
		var m_id = json_data.mash.M_ID;
		var name = json_data.name1;

		html += "<mash-viev>";

		// Заголовок у загальному стилі сайту: назва + горизонтальна лінія, тим
		// самим шрифтом і кольором, що й заголовок бокового меню.
		// Дії сховані під кнопкою-гамбургером у правому краю смуги. Олівець біля
		// назви прибрано — редактор тепер відкривається пунктом «Редактор».
		html +=
			"<div class='view-header'>" +
				"<h3 class='view-title'>" + name + "</h3>" +
				"<button type='button' class='view-menu-btn' aria-label='Меню'>&#9776;</button>" +
				"<div class='view-menu'>" +
					"<a href='?m_id=" + m_id + "&name=" + name + "&json=liry' class='mashyn-edit-details'>Редактор</a>" +
					"<a href='download?m_id=" + m_id + "' download='" + name + ".cnf'>Download .cnf</a>" +
					"<a href='preview?m_id=" + m_id + "'>Preview .cnf</a>" +
				"</div>" +
			"</div>";

		$.each(json_data.liry, function(i, lira) {
			html +=
				"<lir_det>" +
				"<h1>Lira za " + lira.NAME + "  izpolzva " + (lira.MAGAZ == 1 ? "magaz 1 " : "magaz 2 ") + "</h1>" +
				"<p>" + lira.FORM + "</p>";

			$.each(json_data.liry_zm_arr[i], function(j, zm) {
				html +=
					"<zm_det>" +
						"<p>" + (zm.BUKVA == 'u' ? 'β' : zm.BUKVA) + (zm.NAME == null ? '' : " - ") +
						((zm.NPP_S == 0) ? ((zm.NAME || '') + "</p>") : ("<a href='?z_id=" + zm.Z_ID + "&l_id=" + lira.L_ID + "&json=npp' id='zminni_npp_" + zm.Z_ID + "_" + lira.L_ID + "' class='zminni_npp-show-details'>" + zm.NAME + "</a></p><div id='npp-det_" + zm.Z_ID + "_" + lira.L_ID + "'></div>")) +
					"</zm_det>";
			});

			html +=
				"<p><a href='?l_id=" + lira.L_ID + "&json=umovy' id='umovy_" + lira.L_ID + "' class='umovy-show-details'>Условия за зацепване</a></p>" +
				"<div id='usl-det_" + lira.L_ID + "'></div>" +
				"</lir_det>";	// раніше не додавалось: між двома літералами бракувало
								// '+', і автопідстановка крапки з коми обривала оператор
		});

		html += "</mash-viev>";
		$('#mashyn-details').html(html);
	});
}


//export 


