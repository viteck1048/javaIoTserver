import java.io.BufferedOutputStream;  // Для виводу даних у потік
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.net.SocketTimeoutException;
import java.io.IOException;           // Для обробки виключень вводу/виводу
import java.net.Socket;               // Для роботи з клієнтськими сокетами
import java.net.InetAddress;          // Для роботи з IP-адресами

public class ClientHandler extends Thread {
	private Socket socket;
	private int port;
	private BufferedOutputStream out;
	private boolean isHttps;

	private boolean initialized = false;
	private PushbackInputStream inputStream;
	private HTTPRequest httpRequest;
	private int readTimeout;
	private int lastRequestTimeOut;
	private long lastRequestTime;

	public ClientHandler(Socket socket, int port, boolean isHttps) {
		this.socket = socket;
		this.port = port;
		this.isHttps = isHttps;
	}

	/**
	 * Пряме використання (new ClientHandler(...).start()): обробляє все з'єднання
	 * від першого запиту до закриття, як і раніше - keep-alive лишається всередині
	 * цього циклу, воркер (тут - власний Thread) тримається весь час. Для запуску
	 * через WorkerPool викликається handleConnection() напряму, без цього циклу -
	 * пул сам вирішує, коли повернутись за наступним запитом (через вартового),
	 * замість того щоб тримати воркера заблокованим на весь простій.
	 */
	public void run() {
		while (handleConnection()) {
			// keep-alive: продовжуємо, доки з'єднання живе. Пауза/таймаут на
			// випадок тиші вже враховані всередині handleConnection().
		}
	}

	/**
	 * Обробляє один цикл: або один запит-відповідь, або (якщо на сокеті поки
	 * нічого нема) констатує, що з'єднання ще живе. Повертає true, якщо
	 * з'єднання варто тримати відкритим далі (keep-alive чи просто ще не
	 * прийшов перший запит), false - якщо сокет вже закрито.
	 *
	 * Викликається повторно для того самого з'єднання - і напряму з run() (щойно
	 * повернуло true), і через WorkerPool (після того як вартовий побачить у
	 * pollForNextRequest() вхідні байти).
	 */
	public boolean handleConnection() {
		try {
			if (!initialized) {
				readTimeout = 30000; // Default 30 seconds
				lastRequestTimeOut = 60000; // Default 60 seconds
				if (Configs.getDefine("socket_read_timeout")) {
					readTimeout = Configs.getInt("socket_read_timeout");
				}
				if (Configs.getDefine("socket_last_request_timeout")) {
					lastRequestTimeOut = Configs.getInt("socket_last_request_timeout");
				}
				socket.setSoTimeout(readTimeout);
				out = new BufferedOutputStream(socket.getOutputStream());
				lastRequestTime = System.currentTimeMillis(); // Час останнього запиту

				inputStream = new PushbackInputStream(socket.getInputStream());
				httpRequest = new HTTPRequest(port, socket.getInetAddress(), inputStream, out, isHttps);
				initialized = true;
			}

			if (socket.isClosed()) {
				return false;
			}

			if (httpRequest.quickBan) {
				quickBanResponse(out);
				return terminate();
			}

			httpRequest.clean();
			httpRequest.readHeaders();
			if (httpRequest.quickBan) {
				quickBanResponse(out);
				return terminate();
			}

			if (httpRequest.method == null) {
				if (httpRequest.ban) {
					return terminate();
				}

				// Якщо користувач не надсилав запити довго - відключаємо
				if (System.currentTimeMillis() - lastRequestTime > lastRequestTimeOut) {
					return terminate();
				}

				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				return true;
			}

			if (!httpRequest.ban) {
				httpRequest.readBody();
				if (httpRequest.quickBan) {
					quickBanResponse(out);
					return terminate();
				}
			}

			if (!httpRequest.ban) {
				httpRequest.sessionDates();
			}

			HTTPResponse httpResponse;
			lastRequestTime = System.currentTimeMillis(); // Оновлення часу активності

			if (httpRequest.ban == false) {
				httpResponse = Router.route(httpRequest);
				if (httpResponse == null) {
					httpResponse = new HTTPResponse("HTTP/1.1 500 Internal Server Error\r\n\r\n", null);
				}
			}
			else {
				if (Configs.getBoolean("ban_response") || Configs.getBoolean("Firewall")) {
					httpRequest.revers = HTTPRequest.ReversType.BANRESPONSE;
					httpResponse = ReverseProxy.handleReverseRequest(httpRequest);
					if (httpResponse == null) {
						httpResponse = new HTTPResponse("HTTP/1.1 500 Internal Server Error\r\n\r\n", null);
					}
				}
				else {
					httpResponse = new HTTPResponse("HTTP/1.1 400 ERROR\r\n\r\n", null, "ban");
				}
			}

			httpResponse.normalizeHeaders(httpRequest);

			if (httpResponse.getHeaders() != null)
				out.write(httpResponse.getHeaders());
			if (httpResponse.getBody() != null)
				out.write(httpResponse.getBody());
			if (httpResponse.streamResponse) {
				httpResponse.streamResponseTo(out);
			}
			httpResponse.prntMsg(httpRequest);
			out.flush();

			// Закриваємо з'єднання тільки якщо клієнт просить це зробити
			if (httpResponse.close_connect_flag) {
				return terminate();
			}

			return true;

		} catch (IOException e) {
			try {
				if (out != null) {
					out.write("HTTP/1.1 500 Internal Server Error\r\n\r\n".getBytes());
					out.flush();
				}
			} catch (IOException ignored) {}
			return terminate();
		}
	}

	/**
	 * Використовується вартовим WorkerPool (guardLoop()): короткий "підгляд" у
	 * сокет, що не забирає воркера на весь час очікування наступного запиту.
	 * Прочитаний байт одразу повертається назад у потік (unread) - readHeaders()
	 * у наступному handleConnection() побачить його як звичайний перший байт
	 * запиту, ніби нічого й не підглядали.
	 *
	 * Навмисно читає байт напряму з сокета (а не available()): для SSLSocket
	 * available() не бачить непрочитаний TLS-рекорд, що ще не розшифрований, і
	 * мовчки показував би "нема даних" навіть коли клієнт уже надіслав наступний
	 * запит.
	 *
	 * @return true, якщо клієнт щось надіслав (з'єднання готове до
	 *         handleConnection()); false, якщо за pollTimeoutMs нічого не
	 *         прийшло - й досі просто тиша, можна перевірити пізніше.
	 * @throws IOException якщо з'єднання розірване (EOF) чи сталась помилка
	 *         читання - сокет при цьому вже закритий, вартовий має просто
	 *         відкинути задачу, не повертаючи її в чергу.
	 */
	public boolean pollForNextRequest(int pollTimeoutMs) throws IOException {
		try {
			socket.setSoTimeout(pollTimeoutMs);
			int b;
			try {
				b = inputStream.read();
			} catch (SocketTimeoutException e) {
				return false;
			}
			if (b == -1) {
				throw new IOException("Client closed connection while waiting for next request");
			}
			inputStream.unread(b);
			return true;
		} catch (IOException e) {
			closeQuietly();
			throw e;
		} finally {
			if (!socket.isClosed()) {
				socket.setSoTimeout(readTimeout);
			}
		}
	}

	private boolean terminate() {
		closeQuietly();
		return false;
	}

	private void closeQuietly() {
		try {
			if (out != null) {
				out.close();
			}
			if (socket != null && !socket.isClosed()) {
				socket.close();
			}
		} catch (IOException ignored) {}
	}

	private void quickBanResponse(BufferedOutputStream out) {
		try {
			out.write("HTTP/1.1 403 Forbidden\r\n\r\n".getBytes());
			out.flush();
		} catch (IOException ignored) {}
	}

}
