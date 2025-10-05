	
function html_lira(i, m_id, l_id, name, magaz, br_kol_lir, form_, form_zv, fl_add, json_data) {
	var html = '';
	html += 
		"<ui class='spoy-kn spoy-mini spoy-mini-l' style='display: none;'>" + (name || '') + "</ui><a class='spoy-kn'>spoyler</a><div class='spoy-panel'>" + 
			"<h2>lira " + (parseInt(i) + 1) + "</h2>" +
			"<mem_promizhna_f id='mem-prom-f-" + i + "' style='display: none' value=''/>" + 
			"<knop_del_lir><a href='delete_lira.php?l_id=" + (l_id || 0) + "' class='icon delete_icon delete-lira' id='delete-lira-" + i + "' tabindex='-1'>del lira</a></knop_del_lir>" +
			"<input type='hidden' class='lir_name_mem' value='" + (name || '') + "'/>" +
			"<form class='edit-lira-form' action='save_lira.php' enctype='multipart/form-data' method='post'>" +
				"<input type='hidden' name='lira[M_ID]' value='" + (m_id || 0) + "'/>" +
				"<input type='hidden' name='lira[L_ID]' value='" + (l_id || 0) + "'/>" +
				"<p><label>NAME</label><input name='lira[NAME]' class='txt input_non_enter fc_bl_lira_name' value='" + (name || '') + "'/>" +
				"<p><label for='selectMag-" + i + "'>Магазин:</label>" +
				"<select class='input_non_enter fc_bl_lira_magaz' id='selectMag-" + i + "' name='lira[MAGAZ]'>" +
					"<option value=1>Набір коліс 1</option>" +
					"<option value=2>Набір коліс 2</option>" +
				"</select>";
			if(magaz == 1) {
				html += "<script>document.getElementById('selectMag-" + i + "').value = 1;</script>";
			}else if(magaz == 2) {
				html += "<script>document.getElementById('selectMag-" + i + "').value = 2;</script>";
			}
			html +=
				"<p><label for='selectbrk-" + i + "'>Тип ліри:</label>" +
				"<select class='input_non_enter fc_bl_lira_br_kol_lir' id='selectbrk-" + i + "' name='lira[BR_KOL_LIR]'>" +
					"<option value=2>2 колеса: a/b</option>" +
					"<option value=3>3 колеса: a/c</option>" +
					"<option value=4>4: (a/b)*(c/d)</option>" +
				"</select>";
			if(br_kol_lir == 2) {
				html += "<script>document.getElementById('selectbrk-" + i + "').value = 2;</script>";
			}else if(br_kol_lir == 3) {
				html += "<script>document.getElementById('selectbrk-" + i + "').value = 3;</script>";
			}else if(br_kol_lir == 4) {
				html += "<script>document.getElementById('selectbrk-" + i + "').value = 4;</script>";
			}
			html +=
				"<p><input type='hidden' name='lira[FORM]' id='mem-form-lir-" + i + "' value='" + (form_ || '') + "'/>" +
				"<p><input type='hidden' name='lira[FORM_ZV]' id='form-zv-lir-" + i + "' value='" + (form_zv || '') + "'/>" +
				"<p><input type='submit' class='save-lira' value='Save' style='display: none;'>" +
			"</form>" + 
			"<p><label>Формула</label><input class='txt medium input_non_enter fc_bl_lira_form' id='form-lir-" + i + "' value='" + (form_ || '') + "'/>" +
			"<p><knop id='knop-zadijaty-zminy-" + i + "'><label>обратна ф-я</label><label2long>" + (form_zv || '') + "</label2long></knop></p>" +
			"<zminni id='zminni-" + i + "'>";
			var jj = 0;
			var fl_zm = 0;
			html += "<table class='layout'>";
			if(fl_add === 0) {
				$.each(json_data.liry_zm_arr[i], function(j, zm) {
					html += "<tr id='zm-det-show" + i + "-" + j + "'><td class='panel_zm spoy-batja'><ui class='spoy-kn spoy-mini' style='display: none; margin-left: 45px;'>" + (zm.BUKVA == 'u' ? 'β' : zm.BUKVA) + "</ui><a class='spoy-kn'>spoyler</a><div class='spoy-panel' id='zm-det-" + i + "-" + j + "'>";
					
					html += html_zm(m_id, l_id, zm.Z_ID, zm.BUKVA, i, j, zm.NAME, zm.ZNACHENNJA, zm.NPP_S);	
							
					html += "</div></td><tr>";
					jj = j;
					fl_zm = 1;
				});
			}
			if(fl_zm == 0) {
				html += "<tr style='display: none;' id='zm-det-show" + i + "-" + jj + "'><td class='panel_zm spoy-batja'><ui class='spoy-kn spoy-mini' style='display: none; margin-left: 45px;'></ui><a class='spoy-kn'>spoyler</a><div class='spoy-panel' id='zm-det-" + i + "-" + jj + "'></div></td><tr>";
			}
			while(jj < 9) {
				jj++;
				html += "<tr style='display: none;' id='zm-det-show" + i + "-" + jj + "'><td class='panel_zm spoy-batja'><ui class='spoy-kn spoy-mini' style='display: none; margin-left: 45px;'></ui><a class='spoy-kn'>spoyler</a><div class='spoy-panel' id='zm-det-" + i + "-" + jj + "'></div></td><tr>";
			}
			html += 
				"</table></zminni>" + 
			"<p><a href='view_umovy-json.php?l_id=" + l_id + "' id='umovy_" + l_id + "' class=umovy-edit-details style='display: none;'>Условия за зацепване</a></p>" +
			"<div id='usl-det_" + l_id + "'></div>" + 
			"<script>document.getElementById('umovy_" + l_id + "').click();</script>" +
		"</div>";
	return html;
}


function edit_mash(href) {
	$.getJSON(href, function(json_data) {
		m_id_mem = json_data.mash.M_ID;
		var html = '';
		html += 
			"<mash-edit><table class='layout'><tr><td class='panel_mash spoy-batja'>" +
			"<ui class='spoy-kn spoy-mini spoy-mini-m' style='display: none;'>" + (json_data.mash.NAME || '') + "</ui>" +
			"<a class='spoy-kn' tabindex='-1'>spoyler</a><div class='spoy-panel'>" +
			"<h2>mashyna</h2>";
		html +=
			"<a href='delete_mash.php?m_id=" + (json_data.mash.M_ID || 0) + "' class='icon delete_icon delete-mash' tabindex='-1'>del mash</a>" +
			"<form id='edit-mash-form' action='save_mash.php' enctype='multipart/form-data' method='post'>" +
				"<input type='hidden' name='mash[M_ID]' value='" + (json_data.mash.M_ID || 0) + "'/>" +
				"<input type='hidden' name='mash[NAME]' value='" + (json_data.mash.NAME || '') + "'/>" +
				"<input type='hidden' name='mash[M1]' value='" + (json_data.mash.M1 || '') + "'/>" +
				"<input type='hidden' name='mash[M2]' value='" + (json_data.mash.M2 || '') + "'/>" +
				"<input type='submit' class='save-mash' value='Save' style='display: none;'>" +
			"</form>";
		html +=
			"<p><label>" + (lng === "uk" ? "nazva verstatu" : (lng === "bg" ? "imeto na mashina" : "Mashine")) + "</label><input class='txt medium input_non_enter fc_bl_mash_name' value='" + (json_data.mash.NAME || '') + "'/></p>" +
			"<p><label>magaz_1</label><textarea class='txt dovhe input_non_enter fc_bl_mash_m1'>" + (json_data.mash.M1 || '') + "</textarea></p>" +
			"<p><label>magaz_2</label><textarea class='txt dovhe input_non_enter fc_bl_mash_m2'>" + (json_data.mash.M2 || '') + "</textarea></p>";
		
		html += "<table class='layout'>";
		var ii = 0;
		var fl_li = 0;
		ii = 0;
		br_lir = 0;
		$.each(json_data.liry, function(i, lira) {
			html += "<tr><td class='panel_lira spoy-batja' id='lir-det-" + i + "'>";
			html += html_lira(i, json_data.mash.M_ID, lira.L_ID, lira.NAME, lira.MAGAZ, lira.BR_KOL_LIR, lira.FORM, lira.FORM_ZV, 0, json_data);
			html += "</td></tr>";
			ii = i;
			fl_li = 1;
		});
		br_lir = ii;
		if(fl_li == 0) {
			html += "<tr style='display: none;' id='lir-det-shou" + ii + "'><td class='panel_lira spoy-batja' id='lir-det-" + ii + "'></td></tr>";
			br_lir = -1;
		}
		while(ii < 2) {
			ii++;
			html += "<tr style='display: none;' id='lir-det-shou" + ii + "'><td class='panel_lira spoy-batja' id='lir-det-" + ii + "'></td></tr>";
		}
		
		html += "<tr><td><div id=add-lira><a href='add_0_lira.php?m_id=" + (json_data.mash.M_ID || 0) + "' class='icon add_icon add-lira' ";
		if(br_lir >= 2)
			html += "style='display: none;' ";
		html += " tabindex='-1'>Add Lira</a></div></tr>";
		
		html += "</div></td></tr></td></table></mash-edit>";
		html += "<p><input type='button' id='save-all' value='Save All' style='display: none;'></p>";
		$('#mashyn-details').html(html);
	});
}


function edit_umovy(href) {
	$.getJSON(href, function(json_data) {
		var res = '#usl-det_' + json_data.l_id;
		var html = '';
		
		html += 
			"<usl_det><table class='layout'><tr><td class='panel_um spoy-batja'><ui class='spoy-kn spoy-mini' style='display: none;'>Условия за зацепване</ui><a class='spoy-kn'>spoyler</a><div class='spoy-panel'><h2>Условия за зацепване</h2>" +
			"<div id='usl-lenta-" + json_data.l_id + "'>";
			
		$.each(json_data.umovy, function(i, umovy) {
			html += html_um(umovy.U_ID, umovy.L_ID, umovy.UMOVA);
		});
		
		html += "</div>";
		html += "<p><a href='add_0_umova.php?l_id=" + json_data.l_id + "' class='icon add_icon add-um' tabindex='-1' value='" + json_data.l_id + "' style='margin-left: 50px;'>Add umova</a></p>"
		html += "</div></td></tr></table></usl_det>";
		$(res).html(html);
	});
}


function add_um(_this, data) {
	var l_id = $(_this).attr('value');
	var res = "#usl-lenta-" + l_id;
	var html = '';
	html += html_um(data, l_id, '1=1');
	$(res).append(html);
}


function html_um(u_id, l_id, umova) {
	var html = '';
	html += 
		"<div class='odna-umova'>" +
			"<form class='edit-usl-form' action='save_usl.php' enctype='multipart/form-data' method='post'>" +
				"<input type='hidden' name='usl[U_ID]' value='" + (u_id || 0) + "'/>" +
				"<input type='hidden' name='usl[L_ID]' value='" + (l_id || 0) + "'/>" +
				"<input type='hidden' name='usl[UMOVA]' value='" + (umova || '') + "'/>" +
				"<input type='submit' class='save-usl' value='Save' style='display: none;'>" +
			"</form>" +
			"<p>" +
				"<input class='txt medium input_non_enter fc_bl_umovy_umova' value='" + (umova || '') + "' style='margin-left: 50px;'/>" +
				"<a href='delete_um.php?u_id=" + (u_id || 0) + "' class='icon delete_icon delete-um' tabindex='-1' style='margin-left: 10px;'>del umova</a>" +
			"</p>" +
		"</div>";
	return html;
}


function html_zm(m_id, l_id, z_id, bukva, i, j, name, znach, npp_s) {
	var html = '';
	html += 
		"<div class='kont-zm class-zm-z_id-" + z_id + "'>" +
			"<form class='edit-zm-form' action='save_zm.php' enctype='multipart/form-data' method='post'>" + 
				"<input type='hidden' name='zm[M_ID]' value='" + m_id + "'/>" +
				"<input type='hidden' name='zm[L_ID]' value='" + l_id + "'/>" +
				"<input type='hidden' name='zm[Z_ID]' value='" + z_id + "'/>" +
				"<input type='hidden' name='zm[BUKVA]' value='" + bukva + "'/>" +
				"<input type='hidden' name='zm[NPP_S]' value='" + npp_s + "'/>" +
				"<input type='hidden' name='zm[NAME]' value='" + (name || '') + "'/>" + 
				"<input type='hidden' name='zm[ZNACHENNJA]' value='" + (znach || '') + "'/>" +
				"<input type='submit' class='save-zm' value='Save' style='display: none;'>" +
			"</form>" + 
			
			"<input type='hidden' name='var_i' value='" + i + "'>" +
			"<input type='hidden' name='var_j' value='" + j + "'>" +
			
			"<p><label class='Bukva'>" + (bukva == 'u' ? 'β' : bukva) + "</label><input class='txt input_non_enter fc_bl_zm_name' value='" + (name || '') + "'/>" + 
			
			"<p><label> </label>" +
			"<select class='input_non_enter fc_bl_zm_npp_s' id='selectZm-" + i + "-" + j + "' >" +
				"<option value=0>змінна</option>" +
				"<option value=1>перемикач із умовами</option>" +
				"<option value=2>перемикач без умов</option>" +
			"</select>" +
			
			"<p id='stoynost-show-" + i + "-" + j + "' class='stoynost-show' style='display: none;'>" +
				"<label>стойност</label><input class='txt input_non_enter fc_bl_zm_znachennja' value='" + (znach || '') + "'/>" +
			"</p>";
			
		if(npp_s == 0) {
			html += "<script>document.getElementById('selectZm-" + i + "-" + j + "').value = 0;</script>";
			html += "<script>document.getElementById('stoynost-show-" + i + "-" + j + "').style.display='';</script>";
		}else if(npp_s == 1) {
			html += "<script>document.getElementById('selectZm-" + i + "-" + j + "').value = 1;</script>";
		}else if(npp_s == 2) {
			html += "<script>document.getElementById('selectZm-" + i + "-" + j + "').value = 2;</script>";
		}
		html +=
			"<p><a href='view_npp-json.php?z_id=" + z_id + "&l_id=" + l_id + "' id='zminni_npp_" + z_id + "_" + l_id + "' class=zminni_npp-edit-details style='display: none;'>" + name + "</a>" +
			"<div id='knop-zadijaty-zminy-zm-npp-" + i + "-" + j + "' class='zm-npp-" + z_id + "'></div>" +
			"<div id='npp-det_" + z_id + "_" + l_id + "'></div>" +
			"<script>document.getElementById('zminni_npp_" + z_id + "_" + l_id + "').click();</script>" +
		"</div>";
	return html;
}


function edit_zm_npp(href) {
	$.getJSON(href, function(json_data) {
		var res = '#npp-det_' + json_data.z_id + '_' + json_data.l_id;
		var html = '';
		
		html += 
			"<npp_det data-npp_det>" +
			"<div class='npp-add-del-" + json_data.z_id + "' style='display: none;' id='npp-none-" + json_data.z_id + "-" + json_data.l_id + "'>" +
				"<p style='float: right'>" +
					"<a href='add_0_zm_npp.php?z_id=" + json_data.z_id + "' class='icon add_icon add-npp' value='" + json_data.z_id + "' tabindex='-1'>add</a>" +
					"<a href='delete_zm_npp.php?n_id=' style='margin-left: 15px' class='icon delete_icon delete-npp' value='" + json_data.z_id + "' tabindex='-1'>del</a>" +
				"</p>" +
			"</div>" +
			"<table class='layout npp-det-list-" + json_data.z_id + "'>";
		$.each(json_data.zm_npp, function(i, zm_npp) {
			html += html_zm_npp(zm_npp.N_ID, zm_npp.Z_ID, zm_npp.ZNACHENNJA, zm_npp.UMOVA, zm_npp.COMENT, json_data.npp_s);
		});
		if(json_data.npp_s != 0) {
			html += "<script>document.getElementById('npp-none-" + json_data.z_id + "-" + json_data.l_id + "').style.display = '';</script>";
		}
		html += "</table></npp_det>";
		$(res).html(html);
	});
}


function html_zm_npp(n_id, z_id, znach, umova, coment, npp_s) {
	var html = '';
	html += 
		"<tr class='npp-kont'>" +
			"<td class='panel_zm_npp spoy-batja'><ui class='spoy-kn spoy-mini spoy-mini-n' style='display: none;'>" + (coment || '') + "</ui>" +
				"<a class='spoy-kn'>spoyler</a>" +
				"<div class='spoy-panel'>" +
					"<form class='edit-npp-form' action='save_npp.php' enctype='multipart/form-data' method='post'>" +
						"<input type='hidden' name='npp[N_ID]' value='" + (n_id || 0) + "'/>" +
						"<input type='hidden' name='npp[Z_ID]' value='" + (z_id || 0) + "'/>" +
						"<input type='hidden' name='npp[ZNACHENNJA]' class='npp_mem_znachennja' value='" + (znach || '') + "'/>" +
						"<input type='hidden' name='npp[UMOVA]' class='npp_mem_um' value='" + (umova || '') + "'/>" +
						"<input type='hidden' name='npp[COMENT]' class='npp_mem_coment' value='" + (coment || '') + "'/>" +
						"<p><input type='submit' class='save-npp' value='Save' style='display: none;'>" +
					"</form>" +
					
					"<p style='margin-left: 0px;'><label>константа</label><input class='txt input_non_enter fc_bl_npp_znachennja' value='" + (znach || '') + "'/>";
				if(npp_s == 1) {
					html += "<p style='margin-left: 0px;' class='um-show'><label>умова</label><input class='txt input_non_enter fc_bl_npp_umova' value='" + (umova || '') + "'/>";
				}else {
					html += "<p style='margin-left: 0px; display: none;' class='um-show'><label>умова</label><input class='txt input_non_enter fc_bl_npp_umova' value='" + (umova || '') + "'/>";
				}
				html += 
					"<p style='margin-left: 0px;'><label>коментар</label><input class='txt input_non_enter fc_bl_npp_coment' value='" + (coment || '') + "'/>";
				"</div>" +
			"</td>" +
		"</tr>";
	return html;
}


function add_npp(href, z_id, npp_s) {
	$.get(href, function(n_id) {
		var html = html_zm_npp(n_id, z_id, 1, '1=1', 'положення ' + n_id, npp_s);
		$('.npp-det-list-' + z_id).append(html);
		push_my_console_ok("add npp " + n_id);
	});
}


function add_lira(href) {
	$.get(href, function(data) {
		br_lir++;
		var i = br_lir;
		var html = '';
		var teg = "#lir-det-shou" + br_lir;
		$(teg).show();
		html += html_lira(i, m_id_mem, data, 'New lira', 1, 4, 'i=x', 'x=i', 1, null);
		
		if(br_lir >= 2) {
			$('#add-lira').html('');
		}
		var res = '';
		res += "#lir-det-" + br_lir;
		$(res).html(html);
	});
}

