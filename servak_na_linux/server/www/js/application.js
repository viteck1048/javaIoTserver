var br_lir = 0;
var m_id_mem = 0;
var xhttp = new XMLHttpRequest();
var lng = navigator.language.slice(0, 2);


// Підсвітка активного пункту бокового меню. Стиль .links-list a.active приходить
// із home_page.css — той самий, що на решті сайту.
// Кожна машина має ДВА посилання: видиме "show" і приховане "edit" (index.cpp
// клікає його програмно). Вони різняться лише префіксом id, тож підсвічуємо
// завжди видиме — інакше активним лишався б невидимий пункт.
function markActiveMashyn(href) {
	var m = /[?&]m_id=(\d+)/.exec(href || '');
	$('#links-list a').removeClass('active');
	if (m) {
		$('#menu-mashyn-show-details-id' + m[1]).addClass('active');
	}
}


/* ============================================================================
   Консоль звітів — плаваюче вікно (#lc-console), у якому живе сам #instr_zvity.
   Кнопка виклику статична, у футері. Поведінка за зразком ші-чата.
   ============================================================================ */

function lcConsoleIsOpen() {
	var w = document.getElementById('lc-console');
	return !!w && w.classList.contains('open');
}

function lcConsoleOpen() {
	var w = document.getElementById('lc-console');
	if(!w) return;
	w.classList.add('open');
	var body = document.getElementById('instr_zvity');
	if(body) body.scrollTop = body.scrollHeight;
}

function lcConsoleClose() {
	var w = document.getElementById('lc-console');
	if(w) w.classList.remove('open');
}

// Позначка біля кнопки у футері дублює іконку ОСТАННЬОГО повідомлення.
// Поки повідомлень немає — місце зарезервоване, але порожнє.
function lcUpdateConsoleMark() {
	var mark = document.getElementById('lc-console-mark');
	if(!mark) return;
	mark.className = 'console-mark';
	var rows = document.querySelectorAll('#instr_zvity > p');
	if(!rows.length) return;
	var last = rows[rows.length - 1];
	if(last.classList.contains('ok_icon'))         mark.classList.add('mark_ok');
	else if(last.classList.contains('info_icon'))  mark.classList.add('mark_warn');
	else if(last.classList.contains('error_icon')) mark.classList.add('mark_err');
}


// (lng === "uk" ? "nazva verstatu" : (lng === "bg" ? "imeto na mashina" : "Mashine"))
$(document).ready(function() {
	
	xhttp.onreadystatechange = function() {
		if (this.readyState == 4 && this.status == 200) {
			$('#instr_coment').html(this.responseText);
		}
	};
	
	
	$(document).on('change', 'select', function() {
		if($(this).hasClass('fc_bl_lira_br_kol_lir')) {
			$(this).closest('.edit-lira-form').find('.save-lira').trigger('click');
			return true;
		}
		if($(this).hasClass('fc_bl_lira_magaz')) {
			if($(this).val() == 2) {
				var m2 = $('#mashyn-details').find('input[name="mash[M2]"]');
				if(m2.val().length < 3) {
					$(this).val('1');
					push_my_console_err("няма магаз 2.");
					return false;
				}
			}
			$(this).closest('.edit-lira-form').find('.save-lira').trigger('click');
			return true;
		}
		if($(this).hasClass('fc_bl_zm_npp_s')) {
			var npp_s_new = $(this).val();
			var npp_s_old = $(this).closest('.kont-zm').find('input[name="zm[NPP_S]"]').val();
			var z_id = $(this).closest('.kont-zm').find('input[name="zm[Z_ID]"]').val();
			if(npp_s_new != 0 && npp_s_old != 0) {
				if(npp_s_new == 1) {
					$('.class-zm-z_id-' + z_id).find('.um-show').css('display', '');
				}else {
					$('.class-zm-z_id-' + z_id).find('.um-show').css('display', 'none');
				}
				$('.class-zm-z_id-' + z_id).find('input[name="zm[NPP_S]"]').val(npp_s_new);
				$('.class-zm-z_id-' + z_id).find('.fc_bl_zm_npp_s').val(npp_s_new);
				$(this).closest('.kont-zm').find('.save-zm').trigger('click');
				return true;
			}else if(npp_s_new != 0) {
				$('.class-zm-z_id-' + z_id).find('input[name="zm[NPP_S]"]').val(npp_s_new);
				$('.class-zm-z_id-' + z_id).find('.fc_bl_zm_npp_s').val(npp_s_new);
				$('.npp-add-del-' + z_id).css('display', '');
				$('.class-zm-z_id-' + z_id).find('.stoynost-show').css('display', 'none');
				$(this).closest('.kont-zm').find('.add-npp').trigger('click');
				$(this).closest('.kont-zm').find('.add-npp').trigger('click');
				$(this).closest('.kont-zm').find('.save-zm').trigger('click');
				return true;
			}else {
				var requestData = {
					z_id: z_id
				};
				$.ajax({
					url: 'delete-zm-npp-all',
					type: 'DELETE',
					data: requestData,
					success: function (data) {
						push_my_console(data);
					},
					error: function (error) {
						console.error('DELETE request failed');
					}
				});
				$('.npp-det-list-' + z_id).html('');
				$('.npp-add-del-' + z_id).css('display', 'none');
				$('.class-zm-z_id-' + z_id).find('input[name="zm[NPP_S]"]').val(npp_s_new);
				$('.class-zm-z_id-' + z_id).find('.fc_bl_zm_npp_s').val(npp_s_new);
				$('.class-zm-z_id-' + z_id).find('.stoynost-show').css('display', '');
				$(this).closest('.kont-zm').find('.save-zm').trigger('click');
				return true;
			}
		}
	});
	
	
	$(document).on('click', 'a.add-npp', function(e) {
		add_npp($(this).attr('href'), $(this).attr('value'), $(this).closest('.kont-zm').find('.fc_bl_zm_npp_s').val());
		return false;
	});
	
	
	$(document).on('click', 'a.delete-npp', function(e) {
		var z_id = $(this).attr('value');
		if($(this).closest('npp_det').find('.npp-det-list-' + z_id).find('.npp-kont').toArray().length < 3) {
			push_my_console_warn("преключвател не може да има по малку от 2 полож.");
			return false;
		}
		var href = $(this).attr('href');
		var res = $(this).closest('npp_det').find('.npp-det-list-' + z_id).find('.npp-kont:last');
		var n_id = res.find('input[name="npp[N_ID]"]').val();
		e.preventDefault();
		var requestData = {
			n_id: n_id
		};
		
		$.ajax({
			url: href,
			type: 'DELETE',
			data: requestData,
			success: function (data) {
				push_my_console(data);
			},
			error: function (error) {
				console.error('DELETE request failed');
			}
		});
		$('.npp-det-list-' + z_id).find('.npp-kont:last').remove();
		
		return false;
	});
	
	
	$(document).on('click', 'a.umovy-hide-details, a.umovy-show-refresh', function(e) {
		hide_umovy($(this).attr('href'));
		return false;
	});

	
	$(document).on('click', 'a.umovy-show-details', function(e) {
		show_umovy($(this).attr('href'));
		return false;
	});
	
	
	$(document).on('click', 'a.zminni_npp-hide-details, a.zminni_npp-show-refresh', function(e) {
		hide_zm_npp($(this).attr('href'));
		return false;
	});
	
	
	$(document).on('click', 'a.zminni_npp-show-details', function(e) {
		show_zm_npp($(this).attr('href'));
		return false;
	});
	
	
	$(document).on('click', 'a.mashyn-show-details, a.mashyn-show-refresh', function(e) {
		markActiveMashyn($(this).attr('href'));
		show_mash($(this).attr('href'));
		return false;
	});


	$(document).on('click', 'a.mashyn-edit-details', function(e) {
		markActiveMashyn($(this).attr('href'));
		edit_mash($(this).attr('href'));
		return false;
	});


	$(document).on('click', 'a.add-mash', function(e) {
		$('#links-list a').removeClass('active');
		$(this).addClass('active');
	});


	// Меню дій у заголовку перегляду (Редактор / Download .cnf / Preview .cnf).
	// Делеговане, бо заголовок вставляється динамічно з view.js.
	$(document).on('click', '.view-menu-btn', function(e) {
		e.stopPropagation();
		var menu = $(this).closest('.view-header').find('.view-menu');
		var wasOpen = menu.hasClass('open');
		$('.view-menu.open').removeClass('open');
		if(!wasOpen) {
			menu.addClass('open');
		}
	});

	// Закриття по кліку будь-де поза меню (в т.ч. по самому пункту)
	$(document).on('click', function(e) {
		if(!$(e.target).closest('.view-menu').length) {
			$('.view-menu.open').removeClass('open');
		}
	});
	$(document).on('click', '.view-menu a', function(e) {
		$('.view-menu.open').removeClass('open');
	});
	
	/* 
	$(document).on('click', '.mysqlviev', function(e) {
		$.get($(this).attr('href'), function(data) {
			$('main').html(data);
		});
		return false;
	});
	 */
	
	$(document).on('click', 'a.umovy-edit-details', function(e) {
		edit_umovy($(this).attr('href'));
		return false;
	});
	
	
	$(document).on('click', 'a.zminni_npp-edit-details', function(e) {
		edit_zm_npp($(this).attr('href'));
		return false;
	});
	
	
	$(document).on('click', 'a.add-mash', function(e) {
		$.get($(this).attr('href'), function(data) {
			$('#mashyn-details').html(data);
		});
		return false;
	});
	
	
	$(document).on('click', '.add-lira', function(e) {
		var mIdValue = $(this).data('m_id');
		add_lira($(this).attr('href'), mIdValue);
		return false;
	});
	
	
	$(document).on('submit', '#edit-mash-form', function(e) {
		e.preventDefault();
		sub_edit_mash($(this).attr('action'), $(this).serialize(), 'PUT');
		return false;
	});
	
	
	$(document).on('submit', '#add-mash-form', function(e) {
		e.preventDefault();
		sub_edit_mash($(this).attr('action'), $(this).serialize(), 'POST');
		return false;
	});
	
	
	$(document).on('submit', '.edit-lira-form, .edit-zm-form, .edit-npp-form, .edit-usl-form', function(e) {
		e.preventDefault(); // Prevent the default form submission
		// Extract form data
		var formData = $(this).serialize();
		// Get the form action URL
		var url = $(this).attr('action');
		// Send a PUT request
		$.ajax({
			url: url,
			type: 'PUT',
			data: formData,
		//	contentType: 'application/json',
			success: function(data) {
				push_my_console(data);
			},
			error: function(error) {
				console.error('Error:', error);
			}
		});
		return false;
	});
	
	
	$(document).on('click', '#save-all', function(e) {
		/* $('.save-mash').trigger('click');
		$('.save-lira').trigger('click');
		$('.save-zm').trigger('click');
		$('.save-npp').trigger('click');
		$('.save-usl').trigger('click'); */
		return false;
	});
	
	
	$(document).on('focus', '.input_non_enter', function() {
		prnt_instr($(this));
		$(this).css("background", "");
		if($(this).hasClass('fc_bl_lira_form')) {
			var res = $(this).closest('.panel_lira');
			var ii;
			if(res.is('#lir-det-0')) {
				ii = 0;
			}else if(res.is('#lir-det-1')) {
				ii = 1;
			}else if(res.is('#lir-det-2')) {
				ii = 2;
			}
			$('#knop-zadijaty-zminy-' + ii).html("<label>обратна ф-я</label><label2long>" + (res.find('input[name="lira[FORM_ZV]"]').val() || '') + "</label2long>");
		}
		return false;
	});
	
	
	$(document).on('blur', '.input_non_enter', function() {
		$('#instr_coment').html('');
		if($(this).hasClass('fc_bl_lira_form')) {
			if(!analiz_form($(this), 1)) {
				$(this).css("background-color", "red");
			}else {
				$(this).css("background-color", "");
			}
			return false;
		}
		if($(this).hasClass('fc_bl_umovy_umova') || $(this).hasClass('fc_bl_npp_umova')) {
			if(!analiz_umovy($(this))) {
				$(this).css("background-color", "red");
			}else {
				$(this).css("background-color", "");
			}
			return false;
		}
		if($(this).hasClass('fc_bl_lira_name')) {
			if($(this).val() !== $(this).closest('.spoy-panel').find('.lir_name_mem').attr('value')) {
				if($(this).val().length > 3) {
					$(this).closest('.spoy-panel').find('.lir_name_mem').val($(this).val());
					$(this).closest('.spoy-batja').find('.spoy-mini-l').html($(this).val());
					$(this).closest('.spoy-panel').find('.save-lira').trigger('click');
				}else {
					$(this).val($(this).closest('.spoy-panel').find('.lir_name_mem').attr('value'));
				}
			}
			return false;
		}
		if($(this).hasClass('fc_bl_mash_name')) {
			if($(this).val() !== $(this).closest('.spoy-panel').find('input[name="mash[NAME]"]').attr('value')) {
				if($(this).val().length > 3) {
					$(this).closest('.spoy-panel').find('input[name="mash[NAME]"]').val($(this).val());
					$(this).closest('.spoy-batja').find('.spoy-mini-m').html($(this).val());
					$('.save-mash').trigger('click');
				}else {
					$(this).val($(this).closest('.spoy-panel').find('input[name="mash[NAME]"]').attr('value'));
				}
			}
			return false;
		}
		if($(this).hasClass('fc_bl_zm_name')) {
			if($(this).val() !== $(this).closest('.kont-zm').find('input[name="zm[NAME]"]').val()) {
				$(this).closest('.kont-zm').find('input[name="zm[NAME]"]').val($(this).val());
				$(this).closest('.kont-zm').find('.save-zm').trigger('click');
				var z_id = $(this).closest('.kont-zm').find('input[name="zm[Z_ID]"]').val();
				$('.class-zm-z_id-' + z_id).find('input[name="zm[NAME]"]').val($(this).val());
				$('.class-zm-z_id-' + z_id).find('.fc_bl_zm_name').val($(this).val());
			}
			return false;
		}
		if($(this).hasClass('fc_bl_npp_coment')) {
			if($(this).val() !== $(this).closest('.spoy-panel').find('.npp_mem_coment').attr('value')) {
				if($(this).val().length >= 1) {
					$(this).closest('.spoy-panel').find('.npp_mem_coment').val($(this).val());
					$(this).closest('.spoy-batja').find('.spoy-mini-n').html($(this).val());
					$(this).closest('.spoy-panel').find('.save-npp').trigger('click');
				}else {
					$(this).val($(this).closest('.spoy-panel').find('.npp_mem_coment').attr('value'));
				}
			}
			return false;
		}
		if($(this).hasClass('fc_bl_npp_znachennja')) {
			if(!analiz_konst_npp($(this))) {
				$(this).css("background-color", "red");
			}else {
				$(this).css("background-color", "");
			}
			return false;
		}
		if($(this).hasClass('fc_bl_zm_znachennja')) {
			if(!analiz_konst_zm($(this))) {
				$(this).css("background-color", "red");
			}else {
				$(this).css("background-color", "");
			}
			return false;
		}
		if($(this).hasClass('fc_bl_npp_umova')) {
			if(!analiz_umovy_npp($(this))) {
				$(this).css("background-color", "red");
			}else {
				$(this).css("background-color", "");
			}
			return false;
		}
		if($(this).hasClass('fc_bl_mash_m1')) {
			if(!analiz_mash_m1($(this))) {
				$(this).css("background-color", "red");
			}else {
				$(this).css("background-color", "");
			}
			return false;
		}
		if($(this).hasClass('fc_bl_mash_m2')) {
			if(!analiz_mash_m2($(this))) {
				$(this).css("background-color", "red");
			}else {
				$(this).css("background-color", "");
			}
			return false;
		}
	});
	
	
	$(document).on('click', '.zadijaty-zminy-liry', function(e) {
		obrobka_zmin_v_formuli($(this));
		return false;
	});
	
	
	$(document).on('click', 'a.spoy-kn', function(e) {
		var res = $(this).closest('.spoy-batja').find('.spoy-panel').first();
		var res_mini = $(this).closest('.spoy-batja').find('.spoy-mini').first();
		if(res.css("display") === "none") {
			res.css("display", "");
			res_mini.css("display", "none");
		}else {
			res.css("display", "none");
			res_mini.css("display", "");
		}
	});
	
	
	$(document).on('click', 'a.delete-um', function(e) {
		e.preventDefault();
		var mIdValue = $(this).data('u_id');
		var requestData = {
			u_id: mIdValue
		};
		
		$.ajax({
			url: $(this).attr('href'),
			type: 'DELETE',
			data: requestData,
			success: function (data) {
				push_my_console(data);
			},
			error: function (error) {
				console.error('DELETE request failed');
			}
		});
		$(this).closest('.odna-umova').remove();
		return false;
	});
	
	
	$(document).on('click', 'a.delete-lira', function(e) {
		e.preventDefault();
		var mIdValue = $(this).data('l_id');
		var requestData = {
			l_id: mIdValue
		};
		
		$.ajax({
			url: $(this).attr('href'),
			type: 'DELETE',
			data: requestData,
			success: function (data) {
				push_my_console_ok("lira delete");
				$('head').html(data);
			},
			error: function (error) {
				console.error('DELETE request failed');
			}
		});
		$(this).closest('.odna-umova').remove();
		return false;
	});
	
	
	$(document).on('click', 'a.delete-mash', function(e) {
		e.preventDefault();
		var mIdValue = $(this).data('m_id');
		var requestData = {
			m_id: mIdValue
		};
		
		$.ajax({
			url: $(this).attr('href'),
			type: 'DELETE',
			data: requestData,
			success: function (data) {
				push_my_console_ok("mashyna delete");
				$('head').html('<meta http-equiv="refresh" content="0;url=./">');
			},
			error: function (error) {
				console.error('DELETE request failed');
			}
		});
		$(this).closest('.odna-umova').remove();
		return false;
	});
	
	
	$(document).on('click', 'a.add-um', function(e) {
		var _this = this;
		var mIdValue = $(this).data('l_id');
		var postData = {
			l_id: mIdValue
			// Додайте інші дані, які вам потрібні, до об'єкту postData
		};
		$.post($(this).attr('href'), postData, function(data) {
			if(data.length > 12) {
				push_my_console(data);
			}else {
				add_um(_this, data);
			}
		});
		return false;
	});
	
	
	$(document).on('mouseover', '.zadijaty-zminy-liry, .input_non_enter, .mysqlviev', function(e) {
//	$(document).on('mouseover', '.zadijaty-zminy-liry, .mysqlviev', function(e) {
		if($(this).hasClass('mysqlviev')) {
			$('#instr_coment').html('viev DataBase');
			return false;
		}
		prnt_instr($(this));
		return false;
	});
	
	
	$(document).on('mouseout', '.zadijaty-zminy-liry, .input_non_enter, .mysqlviev', function(e) {
//	$(document).on('mouseout', '.mysqlviev', function(e) {
		if(!($(this).is(":focus"))) {
			if($(':focus').is('.input_non_enter')) {
				prnt_instr($(':focus'));
			}
			else {
				$('#instr_coment').html('');
			}
		}
		return false;
	});
	
	
});


$(document).on('keydown', '.input_non_enter', function(event) {
	if(event.which == 13) {
		if($(this).hasClass('fc_bl_lira_form')) {
			$(this).blur();
			if(!analiz_form($(this), 0)) {
				$(this).css("background", "red");
			}else {
				$(this).css("background", "");
			}
			return false;
		}
		event.preventDefault();
		var currentIndex = $('.input_non_enter').index($(this));
		var lastIndex = $('.input_non_enter').length - 1;
		if(currentIndex === lastIndex) {
			$('.input_non_enter').eq(currentIndex).blur();
		}else {
			while($('.input_non_enter').eq(currentIndex + 1).is(":hidden")) {
				if(currentIndex === lastIndex) {
					$('.input_non_enter').eq(currentIndex).blur();
					return false;
				}
				currentIndex++;
			}
			$('.input_non_enter').eq(currentIndex + 1).focus();
		}
	}
});


document.addEventListener('DOMContentLoaded', function() {
	var currentUrl = window.location.href;
	// Розділяємо URL-адресу на шлях та параметри запиту
	var urlParts = currentUrl.split('?');
	var path = urlParts[0];
	var queryParams = urlParts[1];
	// Якщо є параметри запиту, видаляємо їх
	if (queryParams) {
		// Замінюємо поточний URL-адрес на шлях без параметрів
		history.replaceState({}, document.title, path);
	}
});


// --- Бокове меню: свайп і тап ---
// Каркас тепер спільний із рештою сайту: <nav> — це ВЕРХНЯ панель, а бокове
// меню — #sidebar (як на MachineTime і релейках). На мобілці воно керується
// виключно свайпом; #menuToggle схований у CSS і лишається лише як частина
// спільної розмітки, обробника не має.
document.addEventListener('DOMContentLoaded', function() {
    const sidebar = document.getElementById('sidebar');
    const mainContent = document.getElementById('mashyn-details');

    // Якщо #mashyn-details порожній — меню відкрите
    if (sidebar && mainContent && mainContent.innerHTML.trim() === '') {
        sidebar.classList.add('active');
    }

    // Закриття меню по кліку на основний контент
    if (mainContent && sidebar) {
        mainContent.addEventListener('click', function(event) {
            if (sidebar.classList.contains('active') && !event.target.closest('#sidebar')) {
                sidebar.classList.remove('active');
            }
        });
    }

    // Закриття меню по кліку на посилання
    document.body.addEventListener('click', function(event) {
        if (sidebar && sidebar.classList.contains('active')) {
            if (event.target.tagName === 'A' || event.target.closest('a')) {
                sidebar.classList.remove('active');
            }
        }
    });

    // Свайпи
    let touchStartX = 0, touchEndX = 0, touchStartY = 0, touchEndY = 0;

    document.addEventListener('touchstart', function(event) {
        touchStartX = event.changedTouches[0].screenX;
        touchStartY = event.changedTouches[0].screenY;
    }, false);

    document.addEventListener('touchend', function(event) {
        touchEndX = event.changedTouches[0].screenX;
        touchEndY = event.changedTouches[0].screenY;
        handleGesture();
    }, false);

    function handleGesture() {
        if (!sidebar) return;
        if (lcConsoleIsOpen()) return;   // поки консоль відкрита, свайпи їй, не сайдбару
        if (touchEndX < touchStartX && Math.abs(touchStartX - touchEndX) > 50 && Math.abs(touchStartX - touchEndX) > Math.abs(touchStartY - touchEndY)) {
            sidebar.classList.remove('active'); // свайп вліво
        }
        if (touchEndX > touchStartX && Math.abs(touchStartX - touchEndX) > 50 && touchStartX < 200 && Math.abs(touchStartX - touchEndX) > Math.abs(touchStartY - touchEndY)) {
            sidebar.classList.add('active'); // свайп вправо
        }
    }
});
// --- Кінець блоку свайп/тап ---


// --- Консоль: відкриття, закриття, перетяг ---
(function() {
	'use strict';

	// Далі цього порогу жест уже не тап, а прокрутка чи перетяг — і закривати
	// вікно не можна. Саме це відрізняє «подивився і зачинив» від гортання логу.
	var TAP_SLOP = 8;

	var downX = 0, downY = 0, moved = false;
	var drag = null;

	function markMoved(x, y) {
		if(Math.abs(x - downX) > TAP_SLOP || Math.abs(y - downY) > TAP_SLOP) {
			moved = true;
		}
	}

	// ---- відкриття/закриття -------------------------------------------------
	$(document).on('click', '#lc-console-btn', function(e) {
		e.preventDefault();
		e.stopPropagation();
		if(lcConsoleIsOpen()) {
			lcConsoleClose();
		} else {
			lcConsoleOpen();
		}
	});

	// Закриває клік/тап БУДЬ-ДЕ, включно з тілом вікна — але тільки якщо це був
	// саме тап, а не прокрутка. Слухаємо на фазі захоплення, щоб спрацювати
	// раніше за інші обробники.
	function onUp(e) {
		if(!lcConsoleIsOpen()) return;
		if(moved) return;                                       // прокрутка або перетяг
		if($(e.target).closest('#lc-console-btn').length) return;  // кнопкою керує її ж обробник
		lcConsoleClose();
	}

	document.addEventListener('mousedown', function(e) {
		downX = e.clientX; downY = e.clientY; moved = false;
	}, true);
	document.addEventListener('mousemove', function(e) {
		markMoved(e.clientX, e.clientY);
	}, true);
	document.addEventListener('mouseup', onUp, true);

	document.addEventListener('touchstart', function(e) {
		if(e.touches.length !== 1) return;
		downX = e.touches[0].clientX; downY = e.touches[0].clientY; moved = false;
	}, { passive: true, capture: true });
	document.addEventListener('touchmove', function(e) {
		if(e.touches.length !== 1) return;
		markMoved(e.touches[0].clientX, e.touches[0].clientY);
	}, { passive: true, capture: true });
	document.addEventListener('touchend', onUp, true);

	// ---- перетяг за смугу (як у чаті) ---------------------------------------
	function dragStart(x, y) {
		var win = document.getElementById('lc-console');
		if(!win || window.innerWidth <= 768) return;   // на мобілці вікно прибите до країв
		var r = win.getBoundingClientRect();
		drag = { ox: x - r.left, oy: y - r.top };
		win.style.right = 'auto';
		win.style.bottom = 'auto';
		win.style.left = r.left + 'px';
		win.style.top = r.top + 'px';
	}
	function dragMove(x, y) {
		if(!drag) return;
		var win = document.getElementById('lc-console');
		win.style.left = (x - drag.ox) + 'px';
		win.style.top  = (y - drag.oy) + 'px';
	}

	$(document).on('mousedown', '#lc-console-bar', function(e) {
		e.preventDefault();
		dragStart(e.clientX, e.clientY);
	});
	$(document).on('touchstart', '#lc-console-bar', function(e) {
		var t = e.originalEvent.touches;
		if(t.length !== 1) return;
		dragStart(t[0].clientX, t[0].clientY);
	});

	document.addEventListener('mousemove', function(e) {
		if(drag) { e.preventDefault(); dragMove(e.clientX, e.clientY); }
	});
	document.addEventListener('touchmove', function(e) {
		if(drag && e.touches.length === 1) { e.preventDefault(); dragMove(e.touches[0].clientX, e.touches[0].clientY); }
	}, { passive: false });

	document.addEventListener('mouseup',  function() { drag = null; });
	document.addEventListener('touchend', function() { drag = null; });
})();
// --- Кінець блоку консолі ---

function prnt_instr(element) {
    // Створюємо спливаюче вікно, якщо його ще немає
    let tooltip = document.querySelector('.tooltip');
    if (!tooltip) {
        tooltip = document.createElement('div');
        tooltip.className = 'tooltip';
        document.body.appendChild(tooltip);
    }

    // Отримуємо текст інструкції
    let instrText = '';
    if (element.hasClass('mysqlviev')) {
        instrText = 'viev DataBase';
    } else {
        // Отримуємо текст з #instr_coment
        instrText = $('#instr_coment').html();
    }

    if (instrText) {
        // Позиціонуємо спливаюче вікно над елементом
        const rect = element[0].getBoundingClientRect();
        tooltip.style.display = 'block';
        tooltip.style.top = (rect.top - tooltip.offsetHeight - 10) + 'px';
        tooltip.style.left = rect.left + 'px';
        tooltip.innerHTML = instrText;

        // Приховуємо спливаюче вікно при втраті фокусу
        element.one('blur', function() {
            tooltip.style.display = 'none';
        });
    } else {
        tooltip.style.display = 'none';
    }
}
