

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
		
		html += "<mash-viev>";
		html += 
		"<h1><a href='?m_id=" + json_data.mash.M_ID + "&name=" + json_data.name1 + "&json=liry' class='icon edit_icon mashyn-edit-details'>" + json_data.name1 + "</a>" + 
		"<a href=download?m_id=" + json_data.mash.M_ID + " class='dwnldcnf' download='" + json_data.name1 + ".cnf'>Download.cnf</a>" + 
		"<a href=preview?m_id=" + json_data.mash.M_ID + " class='dwnldcnf'>(preview)</a><h1>";
		$.each(json_data.liry, function(i, lira) {
				html += 
				"<lir_det>" + 
				"<h1>Lira za " + lira.NAME + "  izpolzva " + (lira.MAGAZ == 1 ? "magaz 1 " : "magaz 2 ") + "</h1>" + 
				
				"<p>" + lira.FORM + "</p>" ;
			
			$.each(json_data.liry_zm_arr[i], function(j, zm) {
				html += 
					"<zm_det>" +
						"<p>" + (zm.BUKVA == 'u' ? 'β' : zm.BUKVA) + (zm.NAME == null ? '' : " - ") + 
						((zm.NPP_S == 0) ? ((zm.NAME || '') + "</p>") : ("<a href='?z_id=" + zm.Z_ID + "&l_id=" + lira.L_ID + "&json=npp' id=zminni_npp_" + zm.Z_ID + "_" + lira.L_ID + " class=zminni_npp-show-details>" + zm.NAME + "</a></p><div id='npp-det_" + zm.Z_ID + "_" + lira.L_ID + "'></div>")) + 
					"</zm_det>";
					
			});
			
			html += 
				"<p><a href='?l_id=" + lira.L_ID + "&json=umovy' id='umovy_" + lira.L_ID + "' class=umovy-show-details>Условия за зацепване</a></p><div id='usl-det_" + lira.L_ID + "'></div>"
				"</lir_det>";
		});
		html += "</mash-viev>";	
		$('#mashyn-details').html(html);
	});
}


//export 


