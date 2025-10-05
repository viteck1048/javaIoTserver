/* document.addEventListener('DOMContentLoaded', function () {
	var key = localStorage.getItem('key');
	// Перевіряємо, чи ключ існує
	if (true) {
		// Знаходимо всі посилання на сторінці
		var links = document.querySelectorAll('a');
		// Додаємо обробник кліку до кожного посилання
		links.forEach(function (link) {
			link.addEventListener('click', function (event) {
				var href = link.getAttribute('href');
				// Перевіряємо, чи посилання веде на зовнішній сайт
				if (href && !href.startsWith('http') && !href.startsWith('https') && !href.startsWith('//')) {
					// Додаємо ключ як параметр до URL
					var separator = href.includes('?') ? '&' : '?';
					link.setAttribute('href', href + separator + 'key=' + encodeURIComponent(key));
				}
			});
		});
	}
}); */

// Збереження оригінального методу fetch
/* const originalFetch = fetch;

window.fetch = async (input, init = {}) => {
	// Перевірка чи існує заголовок, якщо ні - створюємо
	init.headers = init.headers || {};

	// Додаємо свій заголовок
	init.headers['X-Session-ID'] = localStorage.getItem('key');

	// Викликаємо оригінальний fetch із доданим заголовком
	return originalFetch(input, init);
};


document.querySelectorAll('a').forEach(link => {
	link.addEventListener('click', function(event) {
		event.preventDefault(); // Запобігає переходу за посиланням

		const url = this.href;

		fetch(url, {
			method: 'GET',
		})
		.then(response => response.text())
		.then(data => {
			document.body.innerHTML = data; // Оновлення контенту сторінки
			history.pushState(null, '', url); // Оновлення URL в адресному рядку
		});
	});
});
 */