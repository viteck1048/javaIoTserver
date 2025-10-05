const rb_const = 20;//10
var rb = rb_const;
var currentGadget = null;
var activeMenuItem = null;
let currentDeviceSn = '';

// Device addition HTML template
const deviceAdditionHTML = `
    <div class="container">
        <h1>Додати новий пристрій</h1>
        <div class="form-group">
            <label for="serialNumber">Серійний номер пристрою:</label>
            <input type="text" id="serialNumber" placeholder="Введіть серійний номер">
        </div>
        <button onclick="searchDevice()">Знайти пристрій</button>
        
        <div id="searchResults" style="display:none; margin-top: 20px;">
            <div class="device-info">
                <h3>Знайдено пристрій</h3>
                <p>Серійний номер: <span id="foundSn"></span></p>
                <p>Статус: <span id="deviceStatus">Онлайн</span></p>
                
                <div class="form-group" style="margin-top: 20px;">
                    <label for="pinCode">Введіть PIN-код (4 цифри):</label>
                    <input type="number" id="pinCode" placeholder="0000" min="0" max="9999" maxlength="4" pattern="\\d{4}">
                </div>
                
                <button onclick="registerDevice()">Зареєструвати пристрій</button>
                <div id="message" class="error"></div>
            </div>
        </div>
    </div>
`;

function showMessage(message, type) {
    const messageElement = document.getElementById('message');
    if (messageElement) {
        messageElement.textContent = message;
        messageElement.className = type || '';
    }
}

function isValidSerialNumber(sn) {
    return /^\d{1,10}$/.test(sn) && parseInt(sn) > 0;
}

function searchDevice() {
    const snInput = document.getElementById('serialNumber').value.trim();
    if (!snInput) {
        showMessage('Будь ласка, введіть серійний номер', 'error');
        return;
    }
    
    if (!isValidSerialNumber(snInput)) {
        showMessage('Серійний номер має бути додатнім числом', 'error');
        return;
    }
    
    const sn = parseInt(snInput);
    const params = new URLSearchParams();
    params.append('sn', sn);
    
    fetch('device_status', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: params.toString()
    })
    .then(response => response.json())
    .then(data => {
        if (data.found && data.online) {
            currentDeviceSn = sn;
            document.getElementById('foundSn').textContent = sn;
            document.getElementById('searchResults').style.display = 'block';
            showMessage('', '');
        } else {
            showMessage('Пристрій не знайдено або офлайн', 'error');
            document.getElementById('searchResults').style.display = 'none';
        }
    })
    .catch(error => {
        console.error('Error:', error);
        showMessage('Помилка пошуку пристрою', 'error');
    });
}

function registerDevice() {
    const pin = document.getElementById('pinCode').value.trim();
    
    if (!pin) {
        showMessage('Будь ласка, введіть PIN-код', 'error');
        return;
    }
    
    if (pin.length !== 4 || !/^\d{4}$/.test(pin)) {
        showMessage('PIN-код має містити рівно 4 цифри', 'error');
        return;
    }
    
    if (pin === '0000') {
        showMessage('PIN-код не може бути 0000', 'error');
        return;
    }

    const params = new URLSearchParams();
    params.append('sn', currentDeviceSn);
    params.append('pin', pin);
    params.append('reestr', 'gadget');

    fetch('register_device', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: params.toString()
    })
    .then(response => response.json())
    .then(data => {
        if (data.success) {
            showMessage(data.message || 'Пристрій успішно зареєстровано!', 'success');
            // Reload devices after successful registration
            setTimeout(() => {
                loadKnopkyAndGadgets();
                // Switch to the newly added device
                showGadgetControls(currentDeviceSn);
            }, 2000);
        } else {
            showMessage(data.message || 'Помилка реєстрації пристрою', 'error');
        }
    })
    .catch(error => {
        console.error('Error:', error);
        showMessage('Помилка реєстрації пристрою', 'error');
    });
}

// Function to show device addition interface
function showAddDevice() {
    const field2 = document.getElementById('field2');
    if (field2) {
        field2.innerHTML = deviceAdditionHTML;
        // Update active menu item
        if (activeMenuItem) {
            activeMenuItem.classList.remove('active');
        }
        activeMenuItem = document.querySelector('.add-device-link');
        if (activeMenuItem) {
            activeMenuItem.classList.add('active');
        }
    }
}

// Function to add a menu item for adding a device
function addDeviceMenuItem() {
    const container = document.getElementById('perelik_reley');
    if (!container) return;

    const listItem = document.createElement('li');
    listItem.className = 'links-list-item';
    
    const link = document.createElement('a');
    link.href = '#';
    link.className = 'link-item add-device-link';
    link.textContent = 'Додати пристрій';
    link.onclick = (e) => {
        e.preventDefault();
        showAddDevice();
    };
    
    listItem.appendChild(link);
    container.appendChild(listItem);
}

// Функція для додавання елемента меню
function addLinkItem(name, path) {
	const container = document.getElementById('perelik_reley');
	if (!container) return;

	const listItem = document.createElement('li');
	listItem.className = 'links-list-item';
	
	// Створюємо посилання
	const link = document.createElement('a');
	link.href = path;
	link.className = 'link-item';
	link.textContent = name;
	
	// Додаємо обробник кліку для пристроїв
	if (path.includes('sn=')) {
		const sn = path.split('sn=')[1];
		link.onclick = (e) => {
			e.preventDefault();
			showGadgetControls(sn);
			// Підсвічування активного пункту
			if (activeMenuItem) {
				activeMenuItem.classList.remove('active');
			}
			link.classList.add('active');
			activeMenuItem = link;
		};
		link.id = `gadget_${sn}`;
	} else {
		link.onclick = (e) => {
			e.preventDefault();
			window.location.href = path;
			// Підсвічування активного пункту
			if (activeMenuItem) {
				activeMenuItem.classList.remove('active');
			}
			link.classList.add('active');
			activeMenuItem = link;
		};
	}
	
	listItem.appendChild(link);
	container.appendChild(listItem);
}

function loadGadgets() {
	fetch('get_gadgets')
		.then(response => response.text())
		.then(html => {
			console.log('Отримано HTML:', html); 
			const container = document.getElementById('perelik_reley');
			container.innerHTML = '';

			// Додаємо заголовок
			const title = document.createElement('h3');
			title.className = 'sidebar-title-esp sidebar-title';
			title.textContent = 'Список пристроїв';
			title.id = 'sidebar-title';
			container.appendChild(title);

			// Парсимо HTML
			const parser = new DOMParser();
			const doc = parser.parseFromString(html, 'text/html');
			const gadgets = doc.querySelectorAll('.knopka');
			
			const knopkyContainer = document.getElementById('field2');
			knopkyContainer.innerHTML = '';

			console.log('Знайдено пристроїв:', gadgets.length); 
			
			// Додаємо посилання на головну сторінку
			//addLinkItem('Головна', '/');
			
			// Додаємо посилання на перегляд логу
			//addLinkItem('Перегляд логу', '/view_log_order.html');
			
			// Додаємо посилання на редагування пристроїв
			gadgets.forEach(gadget => {
				if (gadget.tagName === 'A') {
					addLinkItem(gadget.textContent, gadget.getAttribute('href'));
					
					const gadgetDiv = document.createElement('div');
					gadgetDiv.className = 'gadget-container';
					
					// Створюємо заголовок
					const header = document.createElement('div');
					header.className = 'gadget-header';
					
					// Додаємо заголовок з назвою
					const title = document.createElement('h2');
					title.textContent = gadget.textContent;
					header.appendChild(title);
					
					// Додаємо посилання на редагування
					const editLink = document.createElement('a');
					editLink.textContent = 'Edit Device';
					editLink.className = 'edit-link';
					editLink.href = gadget.getAttribute('href');
					//editLink.target = '_blank';
					header.appendChild(editLink);
					
					gadgetDiv.appendChild(header);

					// Витягуємо sn з посилання
					const sn = gadget.getAttribute('href').split('?sn=')[1];
					gadgetDiv.id = `knopky_gadget_${sn}`;

					// Додаємо контейнер для кнопок
					const buttonsDiv = document.createElement('div');
					buttonsDiv.className = 'buttons-container';
					gadgetDiv.appendChild(buttonsDiv);
					gadgetDiv.style.display = 'none';

					knopkyContainer.appendChild(gadgetDiv);
				}
			});
			// Додаємо кнопку для додавання пристрою
			addDeviceMenuItem();

			// Додаємо клас для списку
			container.className = 'links-list';

			// Тепер завантажуємо кнопки
			loadKnopky();
		})
		.catch(e => {
			console.error('Помилка завантаження пристроїв:', e);
			const container = document.getElementById('perelik_reley');
			container.innerHTML = '<div style="color:red">Помилка завантаження пристроїв</div>';
		});
}

function showGadgetControls(sn) {
	// Знаходимо контейнер для пристрою
	const gadgetDiv = document.getElementById(`knopky_gadget_${sn}`);

	// Показуємо контейнер
	if(currentGadget !== gadgetDiv) {
		if(currentGadget) {
			currentGadget.style.display = 'none';
		}
		currentGadget = gadgetDiv;
		gadgetDiv.style.display = 'block';
		sidebar.classList.remove('active');
	}
}

// Додаємо обробник для оновлення сторінки при поверненні зі сторінки редагування
window.addEventListener('pageshow', function(event) {
    if (event.persisted) {
        // Якщо сторінка повертається з кешу, оновлюємо її
        loadKnopkyAndGadgets();
    }
});

function loadKnopky() {
	fetch('get_knopky')
		.then(response => response.text())
		.then(html => {
			const parser = new DOMParser();
			const doc = parser.parseFromString(html, 'text/html');
			const buttons = doc.querySelectorAll('.button');

			// Розміщуємо кнопки в відповідні контейнери
			buttons.forEach(button => {
				const sn = button.getAttribute('sn');
				if (sn) {
					const gadgetDiv = document.getElementById(`knopky_gadget_${sn}`);
					if (gadgetDiv) {
						const buttonsDiv = gadgetDiv.querySelector('.buttons-container');
						if (buttonsDiv) {
							const buttonElement = button.cloneNode(true);
							buttonsDiv.appendChild(buttonElement);
						}
					}
				}
			});

			attachButtonHandlers();
			// Локалізуємо інтерфейс
			localizeUI();
			sidebar.classList.add('active');
		})
		.catch(e => {
			console.error('Помилка завантаження кнопок:', e);
			const container = document.getElementById('mainContent');
			container.innerHTML = '<div style="color:red">Помилка завантаження кнопок</div>';
		});
}

function loadKnopkyAndGadgets() {
    //loadKnopky();
    loadGadgets();
    
    // Check if we should show the add device form
    if (window.location.hash === '#add-device') {
        showAddDevice();
    }
}

function attachButtonHandlers() {
	rb = rb_const;  
	var buttons = document.querySelectorAll('.button');
	buttons.forEach(function(button) {
		button.addEventListener('click', function(event) {
			rb = rb_const;
			let b_id = button.getAttribute('b_id');
			let sn = button.getAttribute('sn');
			if(button.style.backgroundColor === 'grey') {
				return;
			}
			button.style.backgroundColor = 'grey';
			var formData = new URLSearchParams();
			formData.append('button', b_id);
			formData.append('sn', sn);

			fetch('put_status', {
				method: 'PUT',
				headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
				body: formData
			})
			.finally(function() {
				setTimeout(fetchData, 800);
				setTimeout(fetchData, 1300);
			});
		});
	});
}

function fetchData() {
	if(rb != 0) {
		rb -= 1;
		fetch('get_status')
			.then(function(response) { return response.json(); })
			.then(function(data) {
				updateButtons(data.buttons);
				console.log(data);
			})
			.catch(function(error) { console.log('Виникла помилка:', error); });
	}
}

function updateButtons(buttonState) {
	const buttons = document.querySelectorAll('.button');
	for (let i = 0; i < buttons.length; i++) {
		if (buttonState[i] === '0') {
			buttons[i].style.backgroundColor = 'red';
		} else if (buttonState[i] === '1') {
			buttons[i].style.backgroundColor = 'green';
		} else {
			buttons[i].style.backgroundColor = 'grey';
		}
	}
}

// Initialize the application
window.onload = function() {
    loadKnopkyAndGadgets();
    fetchData();
    setTimeout(fetchData, 800);
    setInterval(fetchData, 5000);
    
    // Add device menu item after loading gadgets
    addDeviceMenuItem();
};
