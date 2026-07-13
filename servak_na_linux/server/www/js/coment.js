
function prnt_instr(_this) {
	var res = '';
	res += "script-coment?res=";
	
	if(_this.hasClass('fc_bl_lira_br_kol_lir')) {
		res += "lira_br_kol_lir";
	}else
	if(_this.hasClass('fc_bl_zm_znachennja')) {
		res += "zm_znachennja";
	}else
	if(_this.hasClass('fc_bl_zm_npp_s')) {
		res += "zm_npp_s";
	}else
	if(_this.hasClass('fc_bl_zm_name')) {
		res += "zm_name";
	}else
	if(_this.hasClass('fc_bl_umovy_umova')) {
		res += "umovy_umova";
	}else
	if(_this.hasClass('fc_bl_npp_znachennja')) {
		res += "npp_znachennja";
	}else
	if(_this.hasClass('fc_bl_npp_umova')) {
		res += "npp_umova";
	}else
	if(_this.hasClass('fc_bl_npp_coment')) {
		res += "npp_coment";
	}else
	if(_this.hasClass('fc_bl_mash_name')) {
		res += "mash_name";
	}else
	if(_this.hasClass('fc_bl_mash_m1')) {
		res += "mash_m1";
	}else
	if(_this.hasClass('fc_bl_mash_m2')) {
		res += "mash_m2";
	}else
	if(_this.hasClass('fc_bl_lira_name')) {
		res += "lira_name";
	}else
	if(_this.hasClass('fc_bl_lira_magaz')) {
		res += "lira_magaz";
	}else
	if(_this.hasClass('fc_bl_lira_form_zv')) {
		res += "lira_form_zv";
	}else
	if(_this.hasClass('fc_bl_lira_form')) {
		res += "lira_form";
	}else
	if(_this.hasClass('zadijaty-zminy-liry')) {
		res += "mouse_on_zadijaty_zminy";
	}else		{
		push_my_console("<p class='icon error_icon msg_err'>ERRcoment невідомий class</p>");
		return false;
	}
	
//	res += ".txt";
	xhttp.open("GET", res, true);
//	xhttp.setRequestHeader('Cache-Control', 'max-age=3600');
//	xhttp.setRequestHeader('Expires', new Date(Date.now() + 3600000).toUTCString());
	xhttp.send();
}


// #instr_zvity живе у плаваючому вікні консолі (#lc-console), яке відкривається
// кнопкою у футері. Вміст і далі пише себе в localStorage — це пам'ять звітів.
// Плюс дублюємо вивід простим текстом у консоль браузера, з відповідним рівнем.

function push_my_console_err (data) {
	var currentTime = new Date();
	var time = currentTime.toLocaleTimeString('uk-UA', { timeZone: 'Europe/Kyiv' });
	console.error('[LiraCalc ' + time + '] ' + data);
	push_my_console("<p class='icon error_icon'><lable class='msg_err'>" + data + "</lable><lable class='msg_time'>" + time + "</lable></p>");
}


function push_my_console_warn(data) {
	var currentTime = new Date();
	var time = currentTime.toLocaleTimeString('uk-UA', { timeZone: 'Europe/Kyiv' });
	console.warn('[LiraCalc ' + time + '] ' + data);
	push_my_console("<p class='icon info_icon'><lable class='msg_warn'>" + data + "</lable><lable class='msg_time'>" + time + "</lable></p>");
}


function push_my_console_ok (data) {
	var currentTime = new Date();
	var time = currentTime.toLocaleTimeString('uk-UA', { timeZone: 'Europe/Kyiv' });
	console.log('[LiraCalc ' + time + '] ' + data);
	push_my_console("<p class='icon ok_icon'><lable class='msg_ok'>" + data + "</lable><lable class='msg_time'>" + time + "</lable></p>");
}


function push_my_console(data) {
	if(data.startsWith("<p class='icon")) {
		$('#instr_zvity').append(data);
		$('#instr_zvity').scrollTop($('#instr_zvity').prop('scrollHeight'));
		localStorage.setItem('instr_zvity', $('#instr_zvity').html());
		// Позначка біля кнопки у футері дублює іконку останнього запису
		if(typeof lcUpdateConsoleMark === 'function') lcUpdateConsoleMark();
	}else {
		$('#mashyn-details').append(data);
		$('#mashyn-details').scrollTop($('#instr_zvity').prop('scrollHeight'));
	}
}


function sub_edit_mash(action, formData, method) {
	$.ajax({
		url: action,
		type: method,
		data: formData,
		success: function(data) {
			// Обидві гілки женемо через push_my_console: він і дописує, і скролить,
			// і зберігає в localStorage, і оновлює позначку біля кнопки консолі.
			// Раніше тут усе це дублювалось руками — і позначка не оновлювалась.
			if(data.startsWith("<p class='icon")) {
				push_my_console(data);
			}else if((data + 0) > 0) {
				push_my_console("<p class='icon ok_icon msg_ok'>Машинка успішно додана.</p>");
				var newUrl = "?m_id=" + data;
				// Перезавантажуємо сторінку з новим URL
				window.location.href = newUrl;
			}else {
				$('#mashyn-details').append(data);
				$('#mashyn-details').scrollTop($('#mashyn-details').prop('scrollHeight'));
			}
		},
		error: function(error) {
			console.error('Error:', error);
		}
	});
}

	