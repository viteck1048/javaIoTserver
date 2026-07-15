// Рушій перекладу LiraCalc. Словники — у translations.js.
//
// Сторінка приходить з сервера українською латиницею і такою лишається, якщо
// JS вимкнено. Тут ми накладаємо переклад двома шляхами:
//   * серверна розмітка (index.cpp, add.cpp) позначена атрибутами data-i18n*,
//     її перебирає applyI18n();
//   * розмітку, яку будує сам JS (edit.js, view.js, validacija.js), і
//     повідомлення консолі перекладає t() просто на місці.

let currentLang = 'en';

// Мова браузера: підтримуємо українську, болгарську та англійську.
function detectUserLanguage() {
	const browserLang = navigator.language || navigator.userLanguage || '';
	const lang = browserLang.split('-')[0];
	if(lang === 'uk') {
		return 'uk';
	}
	else if(lang === 'bg') {
		return 'bg';
	}
	return 'en';
}

// Переклад за ключем. params підставляються у плейсхолдери виду {name}.
// Якщо ключа немає в поточній мові — беремо англійський, далі сам ключ.
function t(key, params) {
	let txt = (translations[currentLang] && translations[currentLang][key]) ||
	          (translations['en'] && translations['en'][key]) ||
	          key;
	if(params) {
		Object.keys(params).forEach(function(p) {
			txt = txt.split('{' + p + '}').join(params[p]);
		});
	}
	return txt;
}

// Перебирає серверну розмітку в межах root (за замовчуванням — уся сторінка).
// Викликати повторно після кожної вставки HTML, що прийшов з сервера.
function applyI18n(root) {
	const scope = root || document;

	scope.querySelectorAll('[data-i18n]').forEach(function(el) {
		el.textContent = t(el.getAttribute('data-i18n'));
	});
	scope.querySelectorAll('[data-i18n-title]').forEach(function(el) {
		el.title = t(el.getAttribute('data-i18n-title'));
	});
	scope.querySelectorAll('[data-i18n-aria]').forEach(function(el) {
		el.setAttribute('aria-label', t(el.getAttribute('data-i18n-aria')));
	});
	scope.querySelectorAll('[data-i18n-placeholder]').forEach(function(el) {
		el.placeholder = t(el.getAttribute('data-i18n-placeholder'));
	});
	scope.querySelectorAll('[data-i18n-value]').forEach(function(el) {
		el.value = t(el.getAttribute('data-i18n-value'));
	});
}

// Перекладає повідомлення, що прийшли з сервера (status.cpp). Кожне несе
// data-msg-key і data-msg-arg на <lable>; латинський текст усередині -- fallback.
// $scope -- jQuery-набір або DOM-вузол зі щойно доданими повідомленнями.
function translateServerMsg($scope) {
	var nodes = ($scope && $scope.jquery) ? $scope.find('[data-msg-key]') : $($scope).find('[data-msg-key]');
	nodes.each(function() {
		var key = this.getAttribute('data-msg-key');
		var hasKey = (translations[currentLang] && translations[currentLang][key]) ||
		             (translations['en'] && translations['en'][key]);
		if(hasKey) {
			this.textContent = t(key, { v: this.getAttribute('data-msg-arg') || '' });
		}
	});
}

function localizeUI() {
	document.title = t('page_title');
	applyI18n(document);
}

document.addEventListener('DOMContentLoaded', function() {
	currentLang = detectUserLanguage();
	localizeUI();
});
