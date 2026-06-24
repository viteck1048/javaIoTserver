
document.addEventListener('DOMContentLoaded', function() {
	// Визначаємо мову користувача
	currentLang = detectUserLanguage();
	
	// Локалізуємо інтерфейс
	localizeUI();
	
	document.getElementById('form-title')
	const formTitle = document.getElementById('form-title');
	const submitBtn = document.getElementById('submit-btn');
	const toggleLink = document.getElementById('toggle-link');
	const inviteview = document.getElementById('divinvite');
	var key = localStorage.getItem('key');

	if (toggleLink) {
		 toggleLink.addEventListener('click', () => {
			// Використовуємо локалізовані рядки замість жорстко прописаних українських
			if (formTitle.textContent === t('login_form_title')) {
				// Перемикаємо на форму реєстрації
				formTitle.textContent = t('register_form_title');
				submitBtn.textContent = t('register_button');
				toggleLink.textContent = t('login_link');
				inviteview.style.display = '';
			} else {
				// Перемикаємо на форму входу
				formTitle.textContent = t('login_form_title');
				submitBtn.textContent = t('login_button');
				toggleLink.textContent = t('register_link');
				inviteview.style.display = 'none';
			}
		});
	}
	var ff = 0;
	if (document.getElementById('rr')) {
		document.getElementById('rr').addEventListener('click', () =>{
			console.log("sdfhg");
			if(ff == 0) {
				ff = 1;
				document.getElementById('msg-rsfl').textContent = "Россия - самая паскудная, \nдо блевоты мерзкая страна во всей мировой истории. \nМетодом селекции там вывели чудовищных моральных уродов, \nу которых само понятие Добра и Зла вывернуто на изнанку. \nВсю свою историю эта нация барахтается в дерьме \nи при этом желает потопить в нем весь мир.";
			} else {
				ff = 0;
				document.getElementById('msg-rsfl').textContent = "";
			}
				
		});
	}
	if (submitBtn) {
		submitBtn.addEventListener('click', () => {
			if(document.getElementById('email').value === '' || document.getElementById('password').value === '') {
					return;
				}
				var formData = new URLSearchParams();
				if(formTitle.textContent === t('login_form_title'))
					formData.append('reestr', 'false');
				else {
					formData.append('reestr', 'true');
					formData.append('invite', document.getElementById('invite').value);
				}
				formData.append('login', document.getElementById('email').value);
				formData.append('password', document.getElementById('password').value);
				fetch('/', {
					method: 'POST',
					headers: {
						'Content-Type': 'application/x-www-form-urlencoded'
					},
					body: formData
				})
				.then(function(response) {
					return response.json();
				})
				.then(function(data) {
					// Тут ви можете обробити отримані дані
					console.log(data);
					console.log(data.ok);
					console.log(data.res);
					if(data.ok === 'OK') {
						location.href = 'https://' + location.hostname + "/";// + data.res;
		//						console.log('https://' + location.hostname + "/" + data.res + "?key=" + data.key);
					}
					else {
						formTitle.textContent = t('register_form_title');
						submitBtn.textContent = t('register_button');
						toggleLink.textContent = '';
					}
					console.log(data);
				})
				.catch(function(error) {
					// Обробка помилок
					console.log('Виникла помилка:', error);
				});
		});
	}
	// Код для навігації
	document.getElementById('nav-home').addEventListener('click', function(e) {
		e.preventDefault();
		document.getElementById('home-container').style.display = 'block';
		document.getElementById('content-view').style.display = 'none';
		document.getElementById('auth-container').style.display = 'none';
		
		// Оновлюємо активний клас
		updateActiveNav('nav-home');
		
		// Перемикаємо підсвітку на Home в панелі Links
		const homeLink = document.querySelector('.link-home');
		if (homeLink) {
			updateActiveLink(homeLink);
		}
		const sidebar = document.getElementById('sidebar');
		updateActiveLink(null);
		setTimeout(function() {
			sidebar.classList.add('active');
		}, 300);
	});

	// Додаємо обробник кнопки авторизації
	document.getElementById('nav-auth').addEventListener('click', showAuthForm);

	// Завантажуємо головний текст та ініціалізуємо сторінку
	window.addEventListener('DOMContentLoaded', function() {
		// Завантажуємо головний вміст
		loadHomeContent();
		
		// Очищаємо список перед початком завантаження
		document.getElementById('links-list').innerHTML = '';
		
		// Завантажуємо спочатку авторизовані посилання, а потім звичайні
		loadLinks_autorization_true();
		
		// Завантажуємо звичайні посилання незалежно від результату першого запиту
		setTimeout(function() {
			loadLinks();
			
			// Додаємо посилання на головну сторінку та активуємо початкову навігацію
			setTimeout(function() {
				addHomeLinkToSidebar();
				updateActiveNav('nav-home');
			}, 200);
		}, 300); // Збільшуємо затримку, щоб авторизовані посилання завантажились першими
		setTimeout(function() {
			const sidebar = document.getElementById('sidebar');
			sidebar.classList.add('active');
		}, 500);
		// Додаємо обробники подій для кліку по основному контенту
		document.querySelector('.main-content').addEventListener('click', function(event) {
			if (window.innerWidth <= 768) {
				const sidebar = document.getElementById('sidebar');
				const menuToggle = document.getElementById('menuToggle');
				if (sidebar.classList.contains('active')) {
					sidebar.classList.remove('active');
					menuToggle.classList.remove('active');
				}
			}
		});
	});

	// Код для кнопки-перемикача бокової панелі
	document.getElementById('menuToggle').addEventListener('click', function() {
		const sidebar = document.getElementById('sidebar');
		const menuToggle = document.getElementById('menuToggle');
		sidebar.classList.toggle('active');
		this.classList.toggle('active');
		
		// Оновлюємо текст кнопки в залежності від стану
		if (sidebar.classList.contains('active')) {
			this.textContent = t('hide_sidebar');
		} else {
			this.textContent = t('show_sidebar');
		}
	});
	
	// Перенаправлення на HTTPS
	(function() {
		if (location.protocol === 'http:' && location.hostname !== 'localhost' && !location.hostname.startsWith('192.168.')) {
			// location.href = 'https://' + location.hostname + location.pathname + location.search;
		}
	})();
});



// Функція для завантаження списку звичайних посилань
function loadLinks() {
	const linksList = document.getElementById('links-list');
	// Не очищаємо список, щоб зберегти авторизовані посилання, якщо вони вже є
	
	// Запит до сервера для отримання списку файлів
	fetch('/www80_scripts/scan_directory', {
		method: 'GET',
		headers: {
			'Content-Type': 'application/json'
		}
	})
	.then(response => {
		if (!response.ok) {
			throw new Error('Server request failed');
			return true;
		}
		return response.json();
	})
	.then(data => {
		// Очищаємо список
		//linksList.innerHTML = '';
		
		// Обробка отриманих файлів
		const files = data.files || [];
		const links = data.links || [];

		if (files.length === 0) {
			linksList.innerHTML = `<li><span class="no-links-message">${t('no_links_found')}</span></li>`;
			return;
		}

		if (links.length > 0) {
			links.forEach(link => {
				addLinkItem(link.title, link.url);
			});
		}
		
		files.forEach(file => {
			if (file !== 'index.html') {
				const name = file.replace('.html', '').replace(/_/g, ' ');
				addLinkItem(name, file);
			}
		});
		
		// Додаємо обробники подій для посилань
		addLinkEventListeners();
	})
	.catch(error => {
		console.error('Error loading links:', error);
		linksList.innerHTML = `<li><span class="error-message">${t('error_loading')}</span></li>`;
		return true;
	});
	return true;
}
// Функція для завантаження списку посилань для авторизованих користувачів
function loadLinks_autorization_true() {
	// Очищаємо список перед завантаженням авторизованих посилань
	const linksList = document.getElementById('links-list');
	linksList.innerHTML = '';
	// Запит до сервера для отримання посилань авторизованого користувача
	fetch('/www_scripts/get_links', {
		method: 'GET',
		headers: {
			'Content-Type': 'application/json'
		}
	})
	.then(response => {
		if (!response.ok) {
			throw new Error('Server request failed');
			return true;
		}
		return response.json();
	})
	.then(data => {
		// Перевіряємо, чи є необхідні дані в відповіді
		if (!data || !data.name || !data.links || data.links.length === 0) {
			// Якщо відповідь порожня, не показуємо лінки і ім'я користувача
			console.log('No links or user data available');
			return true; // Повертаємо true, щоб продовжити з loadLinks()
		}
		
		// Користувач авторизований, змінюємо кнопку авторизації на кнопку виходу
		const authLink = document.getElementById('nav-auth');
		authLink.textContent = t('logout');
		authLink.classList.add('logout-link');
		
		// Перевизначаємо обробник кнопки для виходу
		authLink.removeEventListener('click', showAuthForm); // Видаляємо старий обробник, якщо він був
		
		authLink.addEventListener('click', function(e) {
			e.preventDefault();
			
			// Робимо запит на сервер для виходу, а потім перезавантажуємо сторінку
			fetch('/www_scripts/logout', {
				method: 'GET',
				credentials: 'include' // Включаємо кукі в запит
			})
			.then(response => {
				// Після запиту перезавантажуємо сторінку
				window.location.reload();
			})
			.catch(error => {
				console.error('Error during logout:', error);
				// Навіть при помилці перезавантажуємо сторінку
				window.location.reload();
			});
		});
		
		
		// Додаємо ім'я користувача на сторінку
		const userNameElement = document.createElement('li');
		userNameElement.className = 'user-name';
		userNameElement.innerHTML = `<span>${t('welcome')}, ${data.name}!</span>`;
		linksList.appendChild(userNameElement);
		
		// Додаємо роздільник
		const dividerElement = document.createElement('li');
		dividerElement.className = 'divider';
		linksList.appendChild(dividerElement);
		
		// Додаємо посилання з отриманих даних
		data.links.forEach(link => {
			if(link.url.startsWith('relay_servak') && false)
				addLinkItem(link.title, link.url);
			else
				addExternalLinkItem(link.title, link.url);
		});
		
		// Додаємо обробники подій для посилань
		addLinkEventListeners();
		
		return true; // Повертаємо true, щоб не запускати loadLinks()
	})
	.catch(error => {
		console.error('Error loading authorized links:', error);
		// При помилці не показуємо ніяких повідомлень
		return true; // Повертаємо true, щоб продовжити з loadLinks()
	});
	
	return true; // Поки запит виконується, повертаємо true
}

// Функція для показу форми авторизації
function showAuthForm(e) {
	e.preventDefault();
	document.getElementById('home-container').style.display = 'none';
	document.getElementById('content-view').style.display = 'none';
	document.getElementById('auth-container').style.display = 'block';
	
	// Оновлюємо активний клас
	updateActiveNav('nav-auth');
	const sidebar = document.getElementById('sidebar');
	sidebar.classList.remove('active');
	updateActiveLink(null);
}

// Додаємо обробники подій для посилань
function addLinkEventListeners() {
	const links = document.querySelectorAll('.link-item:not(.external-link)');
	links.forEach(link => {
		link.addEventListener('click', function(e) {
			e.preventDefault();
			const path = this.getAttribute('data-path');
			const name = this.textContent;
			
			// Викликаємо clearLogFlag якщо він існує
			if (typeof clearLogFlag === 'function') {
				clearLogFlag();
			}
			
			if (typeof rb !== 'undefined')
				rb = 0;

			// Відображаємо вміст у iframe
			showContent(name, path);
			
			// Приховуємо бокову панель на мобільних пристроях
			if (window.innerWidth <= 768) {
				const sidebar = document.getElementById('sidebar');
				const menuToggle = document.getElementById('menuToggle');
				sidebar.classList.remove('active');
				menuToggle.classList.remove('active');
			}
			
			// Оновлюємо активне посилання
			updateActiveLink(this);
		});
	});
}

// Додаємо посилання на головну сторінку в список посилань
function addHomeLinkToSidebar() {
	const linksList = document.getElementById('links-list');
	const listItem = document.createElement('li');
	listItem.innerHTML = '';//`<a href="#" data-path="home" class="link-item link-home">${t('home_link')}</a>`;
	linksList.insertBefore(listItem, linksList.firstChild);
	
	// Додаємо обробник для кліку на головну сторінку
	const homeLink = document.querySelector('.link-home');
	if (homeLink) {
		homeLink.addEventListener('click', function(e) {
			e.preventDefault();
			
			// Відображаємо головну сторінку
			document.getElementById('home-container').style.display = 'block';
			document.getElementById('content-view').style.display = 'none';
			document.getElementById('auth-container').style.display = 'none';
			
			// Приховуємо бокову панель на мобільних пристроях
			if (window.innerWidth <= 768) {
				const sidebar = document.getElementById('sidebar');
				const menuToggle = document.getElementById('menuToggle');
				sidebar.classList.remove('active');
				menuToggle.classList.remove('active');
			}
			
			// Оновлюємо активне посилання
			updateActiveLink(this);
			
			// Оновлюємо активний елемент навігації
			updateActiveNav('nav-home');
		});
	}
	return true;
}

// Завантажуємо головний текст при завантаженні сторінки
function loadHomeContent() {
	const homeContent = document.getElementById('home-content');
	homeContent.innerHTML = `<p>${t('home_content')}</p>`;
	
	// Закоментоване завантаження тексту з файлу
	/*
	fetch('/www80/text.txt')
		.then(response => response.text())
		.then(data => {
			// Відображаємо текст
			homeContent.innerHTML = `<p>${data}</p>`;
		})
		.catch(error => {
			console.error('Error loading home content:', error);
			homeContent.innerHTML = `<p>Error loading content: ${error.message}</p>`;
		});
	*/
}

// Функція для відображення вмісту в iframe
function showContent(name, path) {
	// Оновлюємо заголовок
	document.getElementById('content-title').textContent = name;
	
	// Завантажуємо вміст в iframe
	const iframe = document.getElementById('content-iframe');
	iframe.src = path;
	
	// Показуємо контейнер вмісту і приховуємо інші
	document.getElementById('home-container').style.display = 'none';
	document.getElementById('content-view').style.display = 'block';
	document.getElementById('auth-container').style.display = 'none';
	
	if (typeof clearLogFlag === 'function') {
		clearLogFlag();
	}
	if (typeof rb !== 'undefined')
		rb = 0;

	// Додаємо обробник події load для iframe
	iframe.onload = function() {
		try {
			// Додаємо скрипт обробки свайпів до документа всередині iframe
			const iframeDoc = iframe.contentDocument || iframe.contentWindow.document;
			if (iframeDoc) {
				// Створюємо елемент скрипту
				const script = iframeDoc.createElement('script');
				script.textContent = `
					// Скрипт для передачі свайпів з iframe до батьківського вікна
					(function() {
						// Перевіряємо, що ми в iframe
						if (window.self === window.top) return;
						
						let touchStartX = 0;
						let touchEndX = 0;
						let touchStartY = 0;
						let touchEndY = 0;
						let touchStartTime = 0;
						
						document.addEventListener('touchstart', function(event) {
							touchStartX = event.changedTouches[0].screenX;
							touchStartY = event.changedTouches[0].screenY;
							touchStartTime = Date.now();
						}, false);
						
						document.addEventListener('touchend', function(event) {
							touchEndX = event.changedTouches[0].screenX;
							touchEndY = event.changedTouches[0].screenY;
							handleSwipeGesture();
						}, false);
						
						function handleSwipeGesture() {
							const deltaX = Math.abs(touchStartX - touchEndX);
							const deltaY = Math.abs(touchStartY - touchEndY);
							const deltaTime = Date.now() - touchStartTime;
							
							// Перевіряємо, що це горизонтальний свайп достатньої довжини
							// І що дія тривала менше 0.3 секунди (300 мс)
							if (deltaX > 50 && deltaX > deltaY && deltaTime < 300) {
								let direction = '';
								
								if (touchEndX < touchStartX) {
									direction = 'left'; // свайп вліво
								} else if (touchEndX > touchStartX && touchStartX < 200) {
									direction = 'right'; // свайп вправо з лівого краю
								}
								
								if (direction) {
									// Відправляємо повідомлення до батьківського вікна
									window.parent.postMessage({
										type: 'swipe',
										direction: direction
									}, window.location.origin);
								}
							}
						}
					})();
				`;
				iframeDoc.head.appendChild(script);
			}
		} catch (e) {
			// Ігноруємо помилки CORS - це нормально для iframe з інших джерел
			console.log('Не вдалося додати обробник свайпів до iframe:', e.message);
		}
		
		// Перевіряємо вміст iframe на редірект
		/*
		const iframeDoc = iframe.contentDocument || iframe.contentWindow.document;
		if (iframeDoc.documentElement.innerHTML.indexOf('<!--redirect_log_in.html-->') !== -1) {
			window.location.href = '/';
		}
		*/
	};
}

// Функція для оновлення активного елемента навігації
function updateActiveNav(activeId) {
	// Викликаємо clearLogFlag якщо він існує
	if (typeof clearLogFlag === 'function') {
		clearLogFlag();
	}
	if (typeof rb !== 'undefined')
		rb = 0;
	const navItems = document.querySelectorAll('nav a');
	navItems.forEach(item => {
		item.classList.remove('active');
	});
	document.getElementById(activeId).classList.add('active');
}

// Функція для додавання посилання до списку
function addLinkItem(name, path) {
	const linksList = document.getElementById('links-list');
	const listItem = document.createElement('li');
	const linkElement = document.createElement('a');
	
	linkElement.className = 'link-item';
	linkElement.textContent = name;
	linkElement.setAttribute('data-path', path);
	linkElement.href = '#';
	
	listItem.appendChild(linkElement);
	linksList.appendChild(listItem);
}

// Функція для додавання зовнішнього посилання до списку
function addExternalLinkItem(name, url) {
	const linksList = document.getElementById('links-list');
	const listItem = document.createElement('li');
	const linkElement = document.createElement('a');
	
	linkElement.className = 'link-item external-link';
	// без external-link - відкривається в iframe
	linkElement.textContent = name;
	linkElement.href = url;
	//linkElement.target = '_blank'; // Відкриваємо в новій вкладці
	
	listItem.appendChild(linkElement);
	linksList.appendChild(listItem);
}

// Функція для оновлення активного посилання
function updateActiveLink(activeLink) {
	const links = document.querySelectorAll('.link-item');
	links.forEach(link => {
		link.classList.remove('active');
	});
	if(activeLink)
		activeLink.classList.add('active');
}
