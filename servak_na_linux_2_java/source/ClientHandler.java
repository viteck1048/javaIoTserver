import java.io.BufferedOutputStream;  // Для виводу даних у потік
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.io.IOException;           // Для обробки виключень вводу/виводу
import java.net.Socket;               // Для роботи з клієнтськими сокетами
import java.net.InetAddress;          // Для роботи з IP-адресами

public class ClientHandler extends Thread {
	private Socket socket;
	private int port;
	private BufferedOutputStream out;
	private boolean isHttps;
	
	public ClientHandler(Socket socket, int port, boolean isHttps) {
		this.socket = socket;
		this.port = port;
		this.isHttps = isHttps;
	}

	public void run() {
		try  {
			// Таймаут читання налаштовується через конфігурацію (за замовчуванням 30 секунд)
			int readTimeout = 30000; // Default 30 seconds
			int lastRequestTimeOut = 60000; // Default 60 seconds
			if (Configs.getDefine("socket_read_timeout")) {
				readTimeout = Configs.getInt("socket_read_timeout");
			}
			if (Configs.getDefine("socket_last_request_timeout")) {
				lastRequestTimeOut = Configs.getInt("socket_last_request_timeout");
			}
			socket.setSoTimeout(readTimeout);
			out = new BufferedOutputStream(socket.getOutputStream());
			
			long lastRequestTime = System.currentTimeMillis(); // Час останнього запиту
			
			InputStream inputStream = socket.getInputStream();
			HTTPRequest httpRequest = new HTTPRequest(port, socket.getInetAddress(), inputStream, out, isHttps);
				
			while(true) {
				
				if(socket.isClosed()) {
					break;
				}
				
				if(httpRequest.quickBan) {
					quickBanResponse(out);
					break;
				}

				httpRequest.clean();
				httpRequest.readHeaders();
				if(httpRequest.quickBan) {
					quickBanResponse(out);
					break;
				}

				if (httpRequest.method == null) {
					// Якщо користувач не надсилав запити довго - відключаємо
					if (System.currentTimeMillis() - lastRequestTime > lastRequestTimeOut) {
						if (!socket.isClosed()) {
							socket.close();
						}
						break;
					}

					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					continue;
				}

				if(!httpRequest.ban) {
					httpRequest.readBody();
					if(httpRequest.quickBan) {
						quickBanResponse(out);
						break;
					}
				}

				if(!httpRequest.ban) {
					httpRequest.sessionDates();
				}
				
				HTTPResponse httpResponse;
				lastRequestTime = System.currentTimeMillis(); // Оновлення часу активності
		
				if(httpRequest.ban == false) {
					httpResponse = Router.route(httpRequest);
					if (httpResponse == null) {
						httpResponse = new HTTPResponse("HTTP/1.1 500 Internal Server Error\r\n\r\n", null);
					}
				}
				else {
					if(Configs.getBoolean("ban_response") || Configs.getBoolean("Firewall")) {
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
				
				if(httpResponse.getHeaders() != null)
					out.write(httpResponse.getHeaders());
				if(httpResponse.getBody() != null)
					out.write(httpResponse.getBody());
				httpResponse.prntMsg(httpRequest);
				out.flush();
				
				// Закриваємо з'єднання тільки якщо клієнт просить це зробити
				if(httpResponse.close_connect_flag) {
					socket.close();  // Закриваємо сокет вручну
					break;
				}
			}
			
		} catch (IOException e) {
			try {
				if (out != null) {
					out.write("HTTP/1.1 500 Internal Server Error\r\n\r\n".getBytes());
					out.flush();
				}
			} catch (IOException ignored) {}
		} finally {
			try {
				if (out != null) {
					out.close();
				}
				if (socket != null && !socket.isClosed()) {
					socket.close();
				}
			} catch (IOException ignored) {}
		}
	}

	private void quickBanResponse(BufferedOutputStream out) {
		try {
			out.write("HTTP/1.1 403 Forbidden\r\n\r\n".getBytes());
			out.flush();
		} catch (IOException ignored) {}
	}

}
