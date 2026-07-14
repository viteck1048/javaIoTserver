
document.addEventListener('DOMContentLoaded', function() {
	// Визначаємо мову користувача
	currentLang = detectUserLanguage();
	
	// Локалізуємо інтерфейс
	localizeUI();
});

// Змінна для зберігання поточної мови
let currentLang = 'en';

// Функція для отримання перекладу за ключем
function t(key) {
	// Отримуємо переклад для поточної мови або англійський за замовчуванням
	return (translations[currentLang] && translations[currentLang][key]) || 
		   translations['en'][key] || key;
}

// Функція для визначення мови користувача за налаштуваннями браузера
function detectUserLanguage() {
	const browserLang = navigator.language || navigator.userLanguage;
	const lang = browserLang.split('-')[0]; // Отримуємо основну частину коду мови
	
	// Визначаємо мову відповідно до вимог (українська, болгарська або англійська)
	if (lang === 'uk') {
		return 'uk';
	} else if (lang === 'bg') {
		return 'bg';
	} else {
		return 'en';
	}
}

// Функція для локалізації всіх текстових елементів на сторінці
function localizeUI() {
	// Встановлюємо заголовок сторінки
	document.title = t('home_title');
	
	// Локалізуємо пункти навігації
	const navHome = document.querySelector('.logo');
	if (navHome && navHome.tagName === 'A') {
		// Для логотипу не змінюємо текст, бо це назва бренду
	}
	
	// Перевіряємо наявність елемента перед зміною
	const navAuth = document.getElementById('nav-auth');
	if (navAuth) navAuth.textContent = t('auth');
	
	// Локалізуємо заголовок сайдбару
	const sidebarTitle = document.querySelector('.sidebar-title');
	if (sidebarTitle) sidebarTitle.textContent = t('links');
	
	// Локалізуємо заголовок для AVR Relays Control
	const sidebarTitleAvr = document.querySelector('.sidebar-title-avr');
	if (sidebarTitleAvr) sidebarTitleAvr.textContent = t('device_list');
	
	// Локалізуємо посилання для додавання пристрою
	const addPrystrijList = document.querySelectorAll('.add_prystrij');
	if (addPrystrijList) {
		addPrystrijList.forEach(el => el.textContent = t('add_device'));
	}
	
	// Локалізуємо елементи для add_prystrij.html
	const searchByName = document.querySelector('.search_by_name');
	if (searchByName) {
		searchByName.textContent = t('search_by_name');
	}
	
	const searchButton = document.querySelector('form.findname button');
	if (searchButton && searchButton.textContent === 'Тьрсене') {
		searchButton.textContent = t('search_button');
	}
	
	const zaholovokslashkomentar = document.getElementById('zaholovokslashkomentar');
	if (zaholovokslashkomentar && zaholovokslashkomentar.textContent === 'проверката на ПИН се извършва, моля, изчакайте') {
		zaholovokslashkomentar.textContent = t('pin_verification');
	}
	
	// Локалізуємо заголовок привітання
	const welcomeTitle = document.querySelector('.section-title-index');
	if (welcomeTitle) welcomeTitle.textContent = t('welcome_title');
	
	// Локалізуємо елементи форми авторизації
	const authTitle = document.querySelector('.auth-section-title');
	if (authTitle) authTitle.textContent = t('auth_title');
	
	const formTitle = document.getElementById('form-title');
	if (formTitle) formTitle.textContent = t('login_form_title');
	
	const emailLabel = document.getElementById('email-label');
	if (emailLabel) emailLabel.textContent = t('email_label');
	
	const emailInput = document.getElementById('email');
	if (emailInput) emailInput.placeholder = t('email_placeholder');
	
	const passwordLabel = document.getElementById('password-label');
	if (passwordLabel) passwordLabel.textContent = t('password_label');
	
	const passwordInput = document.getElementById('password');
	if (passwordInput) passwordInput.placeholder = t('password_placeholder');
	
	const inviteLabel = document.getElementById('invite-label');
	if (inviteLabel) inviteLabel.textContent = t('invite_label');
	
	const inviteInput = document.getElementById('invite');
	if (inviteInput) inviteInput.placeholder = t('invite_placeholder');
	
	const submitBtn = document.getElementById('submit-btn');
	if (submitBtn) submitBtn.textContent = t('login_button');
	
	const toggleLink = document.getElementById('toggle-link');
	if (toggleLink) toggleLink.textContent = t('register_link');
	
	// Локалізуємо інші елементи форми авторизації, якщо вони є на сторінці
	const loginBtnOld = document.querySelector('.login-button');
	if (loginBtnOld) loginBtnOld.textContent = t('login');
	
	const usernameLabelOld = document.querySelector('label[for="username"]');
	if (usernameLabelOld) usernameLabelOld.textContent = t('username');
	
	const passwordLabelOld = document.querySelector('label[for="password"]');
	if (passwordLabelOld) passwordLabelOld.textContent = t('password');
	
	const submitBtnOld = document.querySelector('button[type="submit"]');
	if (submitBtnOld) submitBtnOld.textContent = t('submit');
	
	// Локалізуємо текст футера
	// Підпис загорнутий у посилання на гітхаб. Пишемо в сам <a>: якби ми
	// підставили текст у <p>, textContent зніс би посилання разом із href.
	const footer = document.querySelector('footer p a') || document.querySelector('footer p');
	if (footer) footer.textContent = t('footer_text');
	
	// Локалізуємо текст головної сторінки
	const homeContent = document.getElementById('home-content');
	if (homeContent) {
		const paragraph = homeContent.querySelector('p');
		if (paragraph) paragraph.textContent = t('home_content');
	}
}

// Підтримка свайпів для відкриття/закриття бокової панелі
let touchStartX = 0;
let touchEndX = 0;
let touchStartY = 0;
let touchEndY = 0;

document.addEventListener('touchstart', function(event) {
    touchStartX = event.changedTouches[0].screenX;
    touchStartY = event.changedTouches[0].screenY;
}, false);

document.addEventListener('touchend', function(event) {
    touchEndX = event.changedTouches[0].screenX;
    touchEndY = event.changedTouches[0].screenY;
    handleGesture();
}, false);

// Слухаємо повідомлення від iframe про свайпи
window.addEventListener('message', function(event) {
    // Перевіряємо, що повідомлення від надійного джерела
    if (event.origin !== window.location.origin) return;
    
    if (event.data && event.data.type === 'swipe') {
        const sidebar = document.getElementById('sidebar');
        if (event.data.direction === 'left') {
            sidebar.classList.remove('active');
        } else if (event.data.direction === 'right') {
            sidebar.classList.add('active');
        }
    }
}, false);

function handleGesture() {
    const sidebar = document.getElementById('sidebar');
    if (touchEndX < touchStartX && Math.abs(touchStartX - touchEndX) > 50 && Math.abs(touchStartX - touchEndX) > Math.abs(touchStartY - touchEndY)) { // свайп вліво
        sidebar.classList.remove('active');
    }
    if (touchEndX > touchStartX && Math.abs(touchStartX - touchEndX) > 50 && touchStartX < 200 && Math.abs(touchStartX - touchEndX) > Math.abs(touchStartY - touchEndY)) { // свайп вправо
        sidebar.classList.add('active');
    }
}
