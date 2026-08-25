var gad = null;
var autoupdate_storinka = 0;

$(document).ready(function() {
	get_perelik_prystrojiv();
	setInterval(update_det_gad, 5000);
	
	$(document).on('click', 'a.get_gad', function(e) {
		gad = $(this).attr('href');
		autoupdate_storinka = 1;
		update_det_gad();
		return false;
	});
	
	$(document).on('click', 'a#add_prystrij', function(e) {
		autoupdate_storinka = 0;
		$.get($(this).attr('href'), function(data) {
			$('#field2').html(data);
			localizeUI();
		});
		return false;
	});
	
	$(document).on('click', 'button.reset', function(e) {
		/* e.preventDefault(); // Зупиняє стандартну поведінку елемента
		e.stopPropagation(); // Зупиняє подальше поширення події */
		
		let userInput = prompt("Вьведи PIN");
		
		if (userInput != null && /^\d{4}$/.test(userInput)) {
			let butt = $(this);
			butt.css('background-color', 'red');
			console.log("Введено коректно: " + userInput);
			$('#comentar').html("изпраща запрос кьм устройството");
			$('#comentar').css("display", "");
			$('#namerele').css("display", "none");
			let fdata = { pin: userInput };
			autoupdate_storinka = 0;
			$.post($(this).attr('href'), fdata, function(data) {
				if(data.length > 150) {
					$('#field2').html(data);
					$('#namerele').css("display", "");
				}
				else {
					butt.css('background-color', 'green');
					$('#comentar').html(data);
					$('#comentar').css("display", "");
					$('#namerele').css("display", "none");
				}
				autoupdate_storinka = 1;
			});
			return true;
		} else {
			$('#comentar').html("Невірний формат. Введіть рівно 4 цифри.");
			$('#comentar').css("display", "");
			$('#namerele').css("display", "none");
			return false;
		}
	
		return false;
	});
	
});

let isRedirecting = false;

function update_det_gad() {
	if (isRedirecting) return; // Якщо вже редиректимо, нічого не шлемо
	if(gad != null && autoupdate_storinka != 0) {
		$.ajax({
			url: gad,
			method: 'GET',
			success: function(data, textStatus, xhr) {
				if (xhr.status !== 200) {
					isRedirecting = true;
					window.location.href = '/index.html';
					return;
				}
				$('#field2').html(data);
				get_perelik_prystrojiv();
			},
			error: function(xhr) {
				// Будь-яка помилка від Апача (не 200) теж ловиться тут
				isRedirecting = true;
				window.location.href = '/index.html';
			}
	    });
	}
	else {
		get_perelik_prystrojiv();
	}
	return false;
}

function get_perelik_prystrojiv() {
	if (isRedirecting) return;
//	gad = null;
//	autoupdate_storinka = 1;
	$.ajax({
		url:'get_perelik_prystrojiv', // URL до якого виконується запит
		method: 'GET', // Метод запиту (GET, POST і т.д.)
		success: function(data, textStatus, xhr) {
			//const data = JSON.parse(response);
			if (xhr.status !== 200) {
				isRedirecting = true;
				window.location.href = '/index.html';
				return;
			}
			$('#perelik_prystrojiv').empty(); // Очищаємо контейнер перед додаванням нових елементів
			
			data.gadgets.forEach(gadget => {
				addLinkToSidebar(gadget.sn, gadget.name, gadget.class, gadget.href);
			});
			
			if(document.getElementById('field2').innerHTML.trim() === "") {
				sidebar.classList.add('active');
			}
			// Підсвітлюємо активний пункт меню після оновлення
			if (typeof highlightMenuItem !== 'undefined') {
				highlightMenuItem(activeMenuItemIndex);
			}
		},
		error: function(xhr, status, error) {
			// ОБОВ'ЯЗКОВО перевіряємо статус тут, бо jQuery при 401/403 падає в error
			if (xhr.status === 401 || xhr.status === 403) {
				console.log("Сесію втрачено, редирект...");
				window.location.href = '/index.html';
				return;
			}
			// Обробка інших помилок мережі (сервер перезапускається)
			console.error("Помилка запиту: ", error);
		}
	});
}

$(document).ready(function() {
	$(document).on('submit', '.matrycja_zjednanj2', function(event) {
		event.preventDefault(); // Запобігаємо стандартній відправці форми
	console.log("qwe-----------------------------rtyuiop");
		let matrix = [];
		// Заповнюємо масив значеннями з інпутів
		$('input.kl_1_znak').each(function(index) {
			let row = Math.floor(index / 16); // 16 стовпців
			let col = index % 16;

			// Ініціалізуємо рядок, якщо він ще не створений
			if (!matrix[row]) {
				matrix[row] = [];
			}

			matrix[row][col] = $(this).val();
		});

		// Перетворюємо масив у JSON
		let matrixData = JSON.stringify(matrix);

		// Відправляємо дані на сервер через AJAX
		$.ajax({
			url: $(this).attr('action'),
			method: 'POST',
			data: { matrix: matrixData },
			success: function(response) {
				console.log('Дані відправлено успішно!');
			},
			error: function() {
				console.error('Помилка при відправці.');
			}
		});
	});
});

// Змінна для збереження індексу активного пункту меню
var activeMenuItemIndex = -1;

// Функція для створення посилання в сайдбарі
function addLinkToSidebar(sn, name, classStr, href) {
    const linkElement = document.createElement('a');
    linkElement.href = href;
    linkElement.className = classStr + " link-item";
    linkElement.textContent = name;
    
    const paragraph = document.createElement('li');
    paragraph.appendChild(linkElement);
    
    document.getElementById('perelik_prystrojiv').appendChild(paragraph);
}

// Функція для підсвічування пункту меню за індексом
function highlightMenuItem(index) {
	const menuItems = document.getElementsByClassName('active-menu-item-arr');
	// Видаляємо підсвітку з всіх елементів
	for (let i = 0; i < menuItems.length; i++) {
		menuItems[i].classList.remove('active');
	}
	// Підсвітлюємо вибраний елемент
	if (index >= 0 && index < menuItems.length) {
		menuItems[index].classList.add('active');
	}
	//console.log("index: " + index);
	//console.log("menuItems.length: " + menuItems.length);
}

document.addEventListener('DOMContentLoaded', function() {
    // Делегуємо подію кліку на весь документ
    document.body.addEventListener('click', function(event) {
        const target = event.target.closest('.sidebar a.active-menu-item-arr');
        if (target) {
            const menuItems = Array.from(document.getElementsByClassName('active-menu-item-arr'));
            activeMenuItemIndex = menuItems.indexOf(target);
            highlightMenuItem(activeMenuItemIndex);
        }
    });
});

document.getElementById('mainContent').addEventListener('click', function(event) {
    const sidebar = document.getElementById('sidebar');
    if (sidebar.classList.contains('active') && !event.target.closest('#sidebar') && !event.target.closest('#menuToggle')) {
        sidebar.classList.remove('active');
    }
});

document.addEventListener('DOMContentLoaded', function() {
	// Делегуємо подію кліку на весь документ
	document.body.addEventListener('click', function(event) {
		// Перевірка, чи натиснутий елемент або його батько є посиланням
		if (event.target.tagName === 'A' || event.target.closest('a')) {
			sidebar.classList.remove('active');
		}
	});
});
