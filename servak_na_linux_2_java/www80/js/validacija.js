

function get_lit_um(_this) {
	var res = _this.closest('.odna-umova').find('input[name="usl[L_ID]"]').val();
	res = "#usl-lenta-" + res;
	res += " .odna-umova";
	var maslit = '';
	$(res).each(function() {
		str = $(this).find('input[name="usl[UMOVA]"]').val();
		if(str.length > 1 && str[1] === ':' && !$(this).is(_this.closest('.odna-umova'))) {
			maslit += str.slice(0, 1);
		}
	});
	return maslit;
}


function analiz_umovy(_this) {
	var mem;
	var str = _this.val().replace(/\s+/g, '');
	str = str.toLowerCase();
	if(_this.is('.fc_bl_umovy_umova')) {
		mem = _this.closest('.odna-umova').find('input[name="usl[UMOVA]"]');
		if(str === mem.val()) {
			return true;
		}
	}else {
		mem = _this.closest('.spoy-panel').find('.npp_mem_um');
		if(str === mem.val()) {
			return true;
		}
	}
	if(str.length < 3) {
		push_my_console_err("условието има по малко от 3 знака");
		return false;
	}
	var strfinal = '';
	if(/^[e-z()|&^!]*$/.test(str) && _this.is('.fc_bl_umovy_umova')) {
		push_my_console_ok("логічний вираз");
		let match;
		var regex = /\(/g;
		var n1 = 0;
		while((match = regex.exec(str)) !== null)
			n1++;
		regex = /\)/g;
		var n2 = 0;
		while((match = regex.exec(str)) !== null)
			n2++;
		if(n1 !== n2) {
			push_my_console_err("'(' != ')'");
			return false;
		}
		var zm = 0;
		var di = 0;
		var maslit = get_lit_um(_this);
		for(var i = 0; i < str.length; i++) {
			if(/[e-z]/.test(str[i])) {
				zm++;
			}
			else if(str[i] === '!' || str[i] === '&' || str[i] === '^' || str[i] === '|') {
				di++;
			}
		}
		if((str = analiz_i_redaguvannja(str)) == null) {
			return false;
		}
		for(var i = 0; i < str.length; i++) {
			if(/[e-z]/.test(str[i])) {
				zm--;
				var j;
				for(j = 0; j < maslit.length && maslit[j] !== str[i]; j++);
				if(j === maslit.length) {
					push_my_console_err("алиас '" + str[i] + "' не дефиниран");
					maslit += str[i];
				}
			}
			else if(str[i] === '!' || str[i] === '&' || str[i] === '^' || str[i] === '|') {
				di--;
			}
		}
		if(zm !== 0 || di !== 0) {
			push_my_console_err("jakas dyvna pomylka)skladovi;)");
			return false;
		}
		if(str[0] === '(') {
			str = str.slice(1, str.length - 1);
		}
		strfinal = str;
	}else {
		var str0 = '';
		if(str[1] === ':' && _this.is('.fc_bl_umovy_umova')) {
			if(!/[e-z]/.test(str[0])) {
				push_my_console_err("грешен синтаксис f:1=1");
				return false;
			}else {
				push_my_console_ok("частина логічного виразу");
				var maslit = get_lit_um(_this);
				for(var i = 0; i < maslit.length; i++) {
					if(str[0] === maslit[i]) {
						push_my_console_err("алиас '" + str[0] + "' зает.");
						return false;
					}
				}
				strfinal += str[0] + ':';
				str0 = str.slice(0, 2);
				str = str.slice(2);
			}
		}
		if(!/^[a-d0-9+\-*/=<>!().%]*$/.test(str) && _this.is('.fc_bl_umovy_umova')) {
			push_my_console_err("Недопустим символ");
			return false;
		}
		if(!/^[e-hj-z0-9+\-*/=<>!().%]*$/.test(str) && _this.is('.fc_bl_npp_umova')) {
			push_my_console_err("Недопустим символ");
			return false;
		}
		var str1, str2;
		var porivn;
		if(str[0] === '!' && _this.is('.fc_bl_umovy_umova')) {
			str1 = '';
			porivn = '!';
			str2 = str.slice(1);
			if(!/^[0-9]*$/.test(str2)) {
				push_my_console_err("недопустими знаци след '!'");
				return false;
			}
			if(str2.length > 3) {
				str2 = str2.slice(0, 3);
			}
		}else {
			for(var i = 0; i < str.length; i++) {
				if(/[!=<>]/.test(str[i])) {
					if(/[!=<>]/.test(str[i + 1])) {
						porivn = str.slice(i, i + 2);
						str2 = str.slice(i + 2);
					}else {
						porivn = str.slice(i, i + 1);
						str2 = str.slice(i + 1);
					}
					str1 = str.slice(0, i);
					break;
				}
			}
			if(!(porivn === ">=" || porivn === "<=" || porivn === "!=" || porivn === ">" || porivn === "<" || porivn === "=")) {
				push_my_console_err("'" + porivn + "' не е валидна операция");
				return false;
			}//push_my_console_ok("1 " + str1);push_my_console_ok("2 " + porivn);push_my_console_ok("3 " + str2);
			let match;
			var regex = /\(/g;
			var n1 = 0;
			while((match = regex.exec(str1)) !== null)
				n1++;
			regex = /\)/g;
			var n2 = 0;
			while((match = regex.exec(str1)) !== null)
				n2++;
			if(n1 !== n2) {
				push_my_console_err("'(' != ')'");
				return false;
			}
			var zm = 0;
			var cf = 0;
			var di = 0;
			for(var i = 0; i < str1.length; i++) {
				if(/[0-9]/.test(str1[i])) {
					cf++;
				}
				else if(/[a-d]/.test(str1[i])) {
					zm++;
				}
				else if(str1[i] === '*' || str1[i] === '/' || str1[i] === '+' || str1[i] === '-') {
					di++;
				}
			}
			if((str1 = analiz_i_redaguvannja(str1)) == null) {
				return false;
			}
			for(var i = 0; i < str1.length; i++) {
				if(/[0-9]/.test(str1[i])) {
					cf--;
				}
				else if(/[a-d]/.test(str1[i])) {
					zm--;
				}
				else if(str1[i] === '*' || str1[i] === '/' || str1[i] === '+' || str1[i] === '-') {
					di--;
				}
			}
			if(cf !== 0 || zm !== 0 || di !== 0) {
				push_my_console_err("jakas dyvna pomylka)skladovi;)");
				return false;
			}
			match;
			regex = /\(/g;
			n1 = 0;
			while((match = regex.exec(str2)) !== null)
				n1++;
			regex = /\)/g;
			n2 = 0;
			while((match = regex.exec(str2)) !== null)
				n2++;
			if(n1 !== n2) {
				push_my_console_err("'(' ≠ ')'");
				return false;
			}
			zm = 0;
			cf = 0;
			di = 0;
			for(var i = 0; i < str2.length; i++) {
				if(/[0-9]/.test(str2[i])) {
					cf++;
				}
				else if(/[a-z]/.test(str2[i])) {
					zm++;
				}
				else if(str2[i] === '*' || str2[i] === '/' || str2[i] === '+' || str2[i] === '-') {
					di++;
				}
			}
			if((str2 = analiz_i_redaguvannja(str2)) == null) {
				return false;
			}
			for(var i = 0; i < str2.length; i++) {
				if(/[0-9]/.test(str2[i])) {
					cf--;
				}
				else if(/[a-z]/.test(str2[i])) {
					zm--;
				}
				else if(str2[i] === '*' || str2[i] === '/' || str2[i] === '+' || str2[i] === '-') {
					di--;
				}
			}
			if(cf !== 0 || zm !== 0 || di !== 0) {
				push_my_console_err("jakas dyvna pomylka)skladovi;)");
				return false;
			}
			if(str1[0] === '(') {
				str1 = str1.slice(1, str1.length - 1);
			}
			if(str2[0] === '(') {
				str2 = str2.slice(1, str2.length - 1);
			}
		}
		strfinal = str0 + str1 + porivn + str2;
	}
//	push_my_console_ok(strfinal);
	_this.val(strfinal);
	if(mem !== undefined) {
		mem.val(strfinal);
	}
	if(_this.is('.fc_bl_umovy_umova')) {
		_this.closest('.odna-umova').find('.save-usl').trigger('click');
		return true;
	}else {
		_this.closest('.spoy-panel').find('.save-npp').trigger('click');
		return true;
	}
}


function analiz_form(_this, flag) {
	var li = -1;
	var bb = [];
	if(_this.is('#form-lir-0'))
		li = 0;
	else if(_this.is('#form-lir-1'))
		li = 1;
	else if(_this.is('#form-lir-2'))
		li = 2;
	if(flag === 1 && $("#mem-form-lir-" + li).attr('value') === _this.val()) {
		return true;
	}else {
		var ff = _this.val().replace(/\s+/g, '');
		ff = ff.toLowerCase();
		if(ff[0] != 'i' || ff[1] != '=') {
			push_my_console("<p class='icon error_icon msg_err'>not i=</p>");
			return false;
		}else {
			ff = ff.slice(2);
		}
		ff = ff.replace(/(sin|cos|tan|asin|acos|atan|pi)/g, function(match) {
			switch(match) {
				case "sin": return "A";
				case "cos": return "B";
				case "tan": return "C";
				case "asin": return "D";
				case "acos": return "E";
				case "atan": return "F";
				case "pi": return "3.14159265358979323846"
			}
		});
		var regex = /^[e-hj-z0-9A-F+\-*/().]*$/;
		if(!regex.test(ff)) {
			push_my_console("<p class='icon error_icon msg_err'>присутні непідтримувані символи</p>");
			return false;
		}
		regex = /^[0-9A-F+\-*/().]*$/;
		if(regex.test(ff)) {
			push_my_console("<p class='icon error_icon msg_err'>відсутні змінні</p>");
			return false;
		}
		let match;
		regex = /\(/g;
		var n1 = 0;
		while((match = regex.exec(ff)) !== null)
			n1++;
		regex = /\)/g;
		var n2 = 0;
		while((match = regex.exec(ff)) !== null)
			n2++;
		if(n1 !== n2) {
			push_my_console_err("'(' != ')'");
			return false;
		}
		var zm = 0;
		var cf = 0;
		var di = 0;
		for(var i = 0; i < ff.length; i++) {
			if(/[0-9.]/.test(ff[i])) {
				cf++;
			}
			else if(/[e-hj-z]/.test(ff[i])) {
				zm++;
				bb.push(ff[i]);
			}
			else if(ff[i] === 'A' || ff[i] === 'B' || ff[i] === 'C' || ff[i] === 'D' || ff[i] === 'E' || ff[i] === 'F' || ff[i] === '!' || ff[i] === '*' || ff[i] === '/' || ff[i] === '&' || ff[i] === '+' || ff[i] === '-' || ff[i] === '^' || ff[i] === '|') {
				di++;
			}
		}
		if((ff = analiz_i_redaguvannja(ff)) == null) {
			return false;
		}
		for(var i = 0; i < ff.length; i++) {
			if(/[0-9.]/.test(ff[i])) {
				cf--;
			}
			else if(/[e-hj-z]/.test(ff[i])) {
				zm--;
				
			}
			else if(ff[i] === 'A' || ff[i] === 'B' || ff[i] === 'C' || ff[i] === 'D' || ff[i] === 'E' || ff[i] === 'F' || ff[i] === '!' || ff[i] === '*' || ff[i] === '/' || ff[i] === '&' || ff[i] === '+' || ff[i] === '-' || ff[i] === '^' || ff[i] === '|') {
				di--;
			}
		}
		if(cf !== 0 || zm !== 0 || di !== 0) {
			push_my_console_err("jakas dyvna pomylka)skladovi;).");
			return false;
		}
	}
	if(ff.length !== 1) {
		ff = ff.slice(1, ff.length - 1);
	}
	var res = "#mem-prom-f-" + li;
	$(res).val(ff);
	
	combobpox_dlja_f(bb, li);
	
	return true; 
}


function analiz_i_redaguvannja(str) {
	if(!str.length) {
		push_my_console_err("mezhdu '(' i ')' porozhnjo.");
		return null;
	}
	var str2 = [];
	var dija = [];
	var cory = [];
	var str3 = '';
	var i = 0;
	while(i < str.length) {
		if(str[i] === '|') {
			dija.push({dija: str[i], oper: 4, i: i});
			i++;
			continue;
		}
		if(str[i] === '+' || str[i] === '-' || str[i] === '^') {
			dija.push({dija: str[i], oper: 3, i: i});
			i++;
			continue;
		}
		if(str[i] === '*' || str[i] === '/' || str[i] === '&' || str[i] === '%') {
			dija.push({dija: str[i], oper: 2, i: i});
			i++;
			continue;
		}
		if(str[i] === 'A' || str[i] === 'B' || str[i] === 'C' || str[i] === 'D' || str[i] === 'E' || str[i] === 'F' || str[i] === '!') {
			dija.push({dija: str[i], oper: 1, i: i});
			i++;
			continue;
		}
		let ddd = anmathform(str, i);
		if(ddd == null) {
	//		push_my_console_err("jakas dyvna pomylka.anmathform");
			return null;
		}
		cory.push(ddd);
		i = ddd.end;
	}
	for(i = 0; i < cory.length - 1; i++) {
		if(cory[i].end == cory[i + 1].start) {
			push_my_console_err("м-у 2та операнда няма оператори.");
			return null;
		}
	}
	for(i = 0; i < dija.length - 1; i++) {
		if(dija[i].i + 1 === dija[i + 1].i) {
			if(dija[i + 1].oper !== 1 || dija[i].oper === 1) {
				push_my_console_err("м-у 2та оператора няма операнди.");
				return null;
			}
		}
	}
	if(dija.length == 0 && cory.length == 1 && str[0] === '(') {
		return analiz_i_redaguvannja(str.slice(1, str.length - 1));
	}else if(dija.length == 0 && cory.length == 1) {
		return str;
	}
	i = 0;
	while(i < cory.length) {
		if(str[cory[i].start] === '(') {
			let strtmp = analiz_i_redaguvannja(str.slice(cory[i].start + 1, cory[i].end - 1));
			if(strtmp == null) {
				return null;
			}
			str2.push({str: strtmp, scb: 1});
		}else {
			str2.push({str: str.slice(cory[i].start, cory[i].end), scb: 0});
		}
		if(str2[i] == null) {
			push_my_console_err("jakas dyvna pomylka.str2.push");
			return null;
		}
		i++;
	}
	i = 0;
	var max_p;
	while(i < dija.length) {
		var j = 0;
		max_p = {i: 0, p: 5};
		while(j < dija.length) {
			if(max_p.p > dija[j].oper) {
				max_p.p = dija[j].oper;
				max_p.i = j;
			}
			j++;
		}
		j = 0;
		while(j < cory.length) {
			if(dija[max_p.i].i < cory[j].start) {
				break;
			}
			j++;
		}
		if(j === cory.length) {
			push_my_console_err("няма операнд.");
			return null;
		}
		if(dija[max_p.i].oper == 1) {
			dija[max_p.i].oper = 5;
			str3 = '(' + dija[max_p.i].dija + (str2[j].str[0] !== '(' ? '(' : '') + str2[j].str + (str2[j].str[0] !== '(' ? ')' : '') + ')';
			str2[j].str = str3;
			str2[j].scb = 1;
		}else {
			var k = j - 1;
			while(k >= 0 && str2[k].str == null) {
				k--;
			}
			if(k === -1) {
				push_my_console_err("няма операнд.");
				return null;
			}
			dija[max_p.i].oper = 5;
			str3 = '(' + str2[k].str + dija[max_p.i].dija + str2[j].str + ')';
			str2[j].str = null;
			str2[k].str = str3;
			str2[k].scb = 1;
		}
		i++;
	}
	return str3;
}


function anmathform(str, i) {
	var start;
	var end;
	var f = 0;
	while(i < str.length) {
		if(!(str[i] === 'A' || str[i] === 'B' || str[i] === 'C' || str[i] === 'D' || str[i] === 'E' || str[i] === 'F' || str[i] === '!' || str[i] === '*' || str[i] === '/' || str[i] === '&' || str[i] === '+' || str[i] === '-' || str[i] === '^' || str[i] === '|' || str[i] === '%')) {
			start = i;
			break;
		}
		i++;
	}
	if(str[i] === '(') {
		while(i < str.length) {
			if(str[i] === '(') {
				f++;
			}
			if(str[i] === ')') {
				f--;
				if(f == 0) {
					end = i + 1;
					break;
				}
			}
			i++;
		}
	}
	else if(/[0-9.]/.test(str[i])) {
		while(i < str.length) {
			if(!(/[0-9.]/.test(str[i]))) {
				end = i;
				break;
			}
			i++;
		}
		if(i === str.length) {
			end = i;
		}
	}else {
		i++;
		end = i;
	}
	if(/[0-9.]/.test(str[start])) {
		var regex = /^\d+(\.\d+)?$/;
		if(!regex.test(str.slice(start, end))) {
			push_my_console_err(str.slice(start, end) + " - некоректна константа.");
			return null;
		}
	}
	return {start: start, end: end};
}


function gen_zv_form(str, b) {
	var str2 = 'i';
	var cory = [];
	var dija;
	var di;
	var i = 0;
	if(str === b) {
		str2 = b + '=i';
		return str2;
	}
	while(str !== b) {
		var fl = 0;
		var fl_i = 0;
		i = 0;
		while(i < str.length) {
			if((str[i] === 'A' || str[i] === 'B' || str[i] === 'C' || str[i] === 'D' || str[i] === 'E' || str[i] === 'F')) {
				dija = str[i];
				di = i;
				i++;
				fl = 1;
				continue;
			}
			if((str[i] === '*' || str[i] === '/' || str[i] === '+' || str[i] === '-')) {
				dija = str[i];
				di = i;
				i++;
				fl = 2;
				continue;
			}
			let ddd = anmathform(str, i);
			if(ddd == null) {
				return null;
			}
			let stst = str.slice(ddd.start, ddd.end);
			cory.push({start: ddd.start, end: ddd.end, str: stst, fl: (stst.includes(b) === true ? 1 : 0)});
			i = ddd.end;
		}
		fl_i = 0;
		switch(dija) {
			case 'A':{
				dija = 'D';
				break;
			}
			case 'B':{
				dija = 'E';
				break;
			}
			case 'C':{
				dija = 'F';
				break;
			}
			case 'D':{
				dija = 'A';
				break;
			}
			case 'E':{
				dija = 'B';
				break;
			}
			case 'F':{
				dija = 'C';
				break;
			}
			case '+':{
				dija = '-';
				break;
			}
			case '-':{
				if(cory[0].fl === 1) {
					dija = '+';
				}else {
					dija = '-';
					fl_i = 1;
				}
				break;
			}
			case '*':{
				dija = '/';
				break;
			}
			case '/':{
				if(cory[0].fl === 1) {
					dija = '*';
				}else {
					dija = '/';
					fl_i = 1;
				}
				break;
			}
		}
		if(fl === 1) {
			
			str2 = '(' + dija + str2 + ')';
			
			if(str[cory[0].start] === '(') {
				cory[0].start++;
				cory[0].end--;
			}
			str = str.slice(cory[0].start, cory[0].end);
			
		}
		if(fl === 2) {
			fl = (cory[0].fl === 0 ? 0 : 1);
			if(fl_i === 0) {
				str2 = '(' + str2 + dija + cory[fl].str + ')';
			}else {
				str2 = '(' + cory[fl].str + dija + str2 + ')';
			}
			fl = (fl === 1 ? 0 : 1);
			if(str[cory[fl].start] === '(') {
				cory[fl].start++;
				cory[fl].end--;
			}
			str = str.slice(cory[fl].start, cory[fl].end);
		}
		cory.splice(0, cory.length);
	}
	str2 = b + '=' + str2.slice(1, str2.length - 1);
	return str2;
}


function analiz_mash_m(pole, _post, this_mem_f) {
	var regex = /^[0-9\s]+$/;
	if(!regex.test(pole)) {
		return false;
	}
	var numbers = pole.split(" ");
	for(var i = 0; i < numbers.length; i++) {
		var number = parseInt(numbers[i]);
		if(number > 300 || number < 10) {
			push_my_console_err("едно от колела извьн диапазона 10..300.");
			return false;
		}
	}
	this_mem_f.val(pole);
	_post.trigger('click');
	return true;
}


function analiz_mash_m1(_this) {
	var pole = _this.val();
	var mem_f = _this.closest('.spoy-panel').find('input[name="mash[M1]"]').attr('value');
	if(pole === mem_f) {
		return true;
	}
	var this_mem_f = _this.closest('.spoy-panel').find('input[name="mash[M1]"]');
	if(pole.length < 10) {
		push_my_console_err("непопьлнено поле.");
		return false;
	}
	return analiz_mash_m(pole, _this.closest('.spoy-panel').find('.save-mash'), this_mem_f);
}


function analiz_mash_m2(_this) {
	var pole = _this.val();
	var mem_f = _this.closest('.spoy-panel').find('input[name="mash[M2]"]').attr('value');
	if(pole === mem_f) {
		return true;
	}
	var this_mem_f = _this.closest('.spoy-panel').find('input[name="mash[M2]"]');
	if(pole.length == 0) {
		if($('.fc_bl_lira_magaz').filter(function() {
			return $(this).val() == 2;
		}).length > 0) {
			push_my_console_err("набор от колела 2 използва поне 1на лира.");
			return false;
		}else {
			this_mem_f.val(pole);
			_this.closest('.spoy-panel').find('.save-mash').trigger('click');
			return true;
		}
	}
	return analiz_mash_m(pole, _this.closest('.spoy-panel').find('.save-mash'), this_mem_f);
}


function analiz_konst_npp(_this) {
	var pole = _this.val();
	var mem = _this.closest('.spoy-panel').find('.npp_mem_znachennja').attr('value');
	if(pole === mem) {
		return true;
	}
	var this_mem = _this.closest('.spoy-panel').find('.npp_mem_znachennja');
	if(pole.length < 1) {
		push_my_console_err("непопьлнено поле.");
		return false;
	}
	return analiz_konst(pole, _this.closest('.spoy-panel').find('.save-npp'), this_mem);
}


function analiz_konst_zm(_this) {
	var pole = _this.val();
	var mem = _this.closest('.kont-zm').find('input[name="zm[ZNACHENNJA]"]').attr('value');
	if(pole === mem) {
		return true;
	}
	var this_mem = _this.closest('.kont-zm').find('input[name="zm[ZNACHENNJA]"]');
	if(pole.length < 1) {
		push_my_console_err("непопьлнено поле.");
		return false;
	}
	if(analiz_konst(pole, _this.closest('.kont-zm').find('.save-zm'), this_mem) === true) {
		var z_id = _this.closest('.kont-zm').find('input[name="zm[Z_ID]"]').val();
		$('.class-zm-z_id-' + z_id).find('input[name="zm[ZNACHENNJA]"]').val(pole);
		$('.class-zm-z_id-' + z_id).find('.fc_bl_zm_znachennja').val(pole);
		return true;
	}
	return false;
}


function analiz_konst(pole, _post, this_mem) {
	var regex = /^\d+(\.\d+)?$/;
	if(!regex.test(pole)) {
		push_my_console_err("введено невалидно значение.");
		return false;
	}
	this_mem.val(pole);
	_post.trigger('click');
	return true;
}


function combobpox_dlja_f(bb, li) {
	var html = '';
	html +=
		"<p><label for='selectbb-" + li + "'>Найважната променлива</label>" +
		"<select class='input_non_enter fc_bl_lira_form_zv' id='selectbb-" + li + "' >";
		for(var i = 0; i < bb.length; i++) {
			html += "<option value='" + bb[i] + "'>" + bb[i] + "</option>";
		}
	html += "</select>";
	html += "<script>document.getElementById('selectbb-" + li + "').value = '" + bb[0] + "';</script>";
	html += "<button class='zadijaty-zminy-liry' id='zadijaty-zminy-liry-" + li + "'>Задействай промени</button>";
	var res = "#knop-zadijaty-zminy-" + li;
	$(res).html(html);
	xhttp.open("GET", "script_coment.php?res=lira_form_zv.txt", true);
	xhttp.send();
}


function obrobka_zmin_v_formuli(_this) {
	var li = -1;
	if(_this.is('#zadijaty-zminy-liry-0'))
		li = 0;
	else if(_this.is('#zadijaty-zminy-liry-1'))
		li = 1;
	else if(_this.is('#zadijaty-zminy-liry-2'))
		li = 2;
	
	var res = "#mem-prom-f-" + li;
	var ff = $(res).val();
	$(res).val('');
	
	if(ff === '') {
		var res = "#form-lir-" + li;
		var ff2 = $(res).val();
		ff = ff2[2];
	}
	
	res = "#selectbb-" + li;
	var ff_zv = gen_zv_form(ff, $(res).val());
	ff_zv = analiz_i_redaguvannja(ff_zv.slice(2));
	ff_zv = $(res).val() + "=" + (ff_zv.length !== 1 ? ff_zv.slice(1, ff_zv.length - 1) : ff_zv);
	
	ff = ff.replace(/(A|B|C|D|E|F)/g, function(match) {
		switch(match) {
			case "A": return "sin";
			case "B": return "cos";
			case "C": return "tan";
			case "D": return "asin";
			case "E": return "acos";
			case "F": return "atan";
		}
	});
	ff_zv = ff_zv.replace(/(A|B|C|D|E|F)/g, function(match) {
		switch(match) {
			case "A": return "sin";
			case "B": return "cos";
			case "C": return "tan";
			case "D": return "asin";
			case "E": return "acos";
			case "F": return "atan";
		}
	});
	ff = "i=" + ff;
	res = '#mem-form-lir-' + li;
	$(res).val(ff);
	res = '#form-lir-' + li;
	$(res).val(ff);
	res = '#form-zv-lir-' + li;
	$(res).val(ff_zv);
	console.log(ff);
	console.log(ff_zv);
	
	var l_id = $('#lir-det-' + li + ' .edit-lira-form input[name="lira[L_ID]"]').val();
	var m_id = $('#lir-det-' + li + ' .edit-lira-form input[name="lira[M_ID]"]').val();
	
	res = "#selectbb-" + li;
	var bb2 = $(res).get().map(function(option) {
		return $(option).text();
	});
	var bb = bb2[0];
	for(var i = 0; i < 10; i++) {
		res = "#zm-det-" + li + "-" + i;
		if($(res).children().length > 0) {
			var bukva = $(res + ' .edit-zm-form input[name="zm[BUKVA]"]').val();
			if(bb.includes(bukva) == false) {
				var z_id = $(res + ' .edit-zm-form input[name="zm[Z_ID]"]').val();
				$.get('delete_zm.php?l_id=' + l_id + '&z_id=' + z_id, function(data) {
					push_my_console(data);
				});
			}
			$(res).html('');
			res = "#zm-det-show" + li + "-" + i;
			$(res).hide();
		}
	}
	
	for(var i = 0; i < bb.length; i++) {
		$.getJSON('add_0_zminna.php?l_id=' + l_id + '&bukva=' + bb[i] + '&zm_poz=' + i, function(data) {
			if(data.length > 50) {
				push_my_console(data);
				return false;
			}
			var z_id = data.z_id;
			var zm_poz = data.zm_poz;
			$.getJSON('getjson_zm.php?z_id=' + z_id + '&zm_poz=' + zm_poz, function(jdata) {
				var html = '';
				
				html += html_zm(m_id, l_id, z_id, jdata.zm.BUKVA, li, jdata.zm_poz, jdata.zm.NAME, jdata.zm.ZNACHENNJA, jdata.zm.NPP_S);
				
				res = "#zm-det-" + li + "-" + jdata.zm_poz;
				$(res).html(html);
				res = "#zm-det-show" + li + "-" + jdata.zm_poz;
				$(res).find('.spoy-mini').html(jdata.zm.BUKVA == 'u' ? 'β' : jdata.zm.BUKVA);
				$(res).show();
			})
		});
	}
	
	$('#lir-det-' + li + ' .save-lira').trigger('click');
	
	res = "#knop-zadijaty-zminy-" + li;
	$(res).html('<p><label>обратна ф-я</label><label2long>' + ff_zv + '</label2long></p>');
	
}

