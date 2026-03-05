import java.io.BufferedOutputStream;  // Для виводу даних у потік
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.io.IOException;           // Для обробки виключень вводу/виводу
import java.net.Socket;               // Для роботи з клієнтськими сокетами
import java.net.InetAddress;          // Для роботи з IP-адресами
//import java.util.Random;


public class ClientHandler extends Thread {
	private Socket socket;
	private int port;
	private BufferedOutputStream out;
	
	public ClientHandler(Socket socket, int port) {
		this.socket = socket;
		this.port = port;
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
			while(true) {
				
				if(socket.isClosed()) {
					break;
				}
				
				InputStream inputStream = socket.getInputStream();
				HTTPRequest httpRequest = new HTTPRequest(inputStream, port, socket.getInetAddress());
				if(httpRequest.quickBan) {
					if (out != null) {
						out.write("HTTP/1.1 500 Internal Server quickBan\r\n\r\n".getBytes());
						out.flush();
					}
					break;
				}
				if (httpRequest.header == null) {
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
				
				HTTPResponse httpResponse = new HTTPResponse(503);
				lastRequestTime = System.currentTimeMillis(); // Оновлення часу активності
				
				if(httpRequest.path.compareTo(Configs.getParam("dbg_post_message_path")) == 0) {
					System.out.println("+++++++++++++++++++++++++++++++++++++");
					System.out.println(httpRequest.user_agent + " DBG MSG, length: " + httpRequest.contentLength + ":");
					System.out.println(new String(httpRequest.bodyData));
					System.out.println("+++++++++++++++++++++++++++++++++++++");
					for(byte b : httpRequest.bodyData)
						System.out.printf("%02X ", b);
					System.out.println();
					System.out.println("+++++++++++++++++++++++++++++++++++++");/**/
					httpRequest.ban = true;
				}

				/*if(httpRequest.clientAddress.toString().compareTo("/185.40.4.51") == 0) {
					httpResponse = new HTTPResponse("HTTP/1.1 400 ujmis, djatel\r\n\r\n", null);
				}
				else if(httpRequest.clientAddress.toString().compareTo("/185.177.72.7") == 0) {
					httpResponse = new HTTPResponse("HTTP/1.1 400 ujmis, djatel\r\n\r\n", null);
				}*/
				else if(httpRequest.ban == false) {
					port = httpRequest.port;
					if(!Configs.getBoolean("https_run") && Configs.getBoolean("test_all_services") && port == 80) {
						port = 443;
						httpRequest.port = 443;
						httpRequest.userID = 4;
					}
					try {
						if (httpRequest.path.startsWith("/www80_scripts") && (httpRequest.method.compareTo("GET") == 0 || httpRequest.method.compareTo("HEAD") == 0)) {
							httpResponse = handleWWW80Scripts(httpRequest);
						} 
						else if (httpRequest.path.startsWith("/www_scripts") && (httpRequest.method.compareTo("GET") == 0 || httpRequest.method.compareTo("HEAD") == 0)) {
							httpResponse = handleWWWScripts(httpRequest);
						}
						// Розділення обробки запитів за портами та статусом автентифікації
						else {
							switch (port) {
								case 8083:
									// Порт 8083 має своє шифрування
									if(httpRequest.revers != HTTPRequest.ReversType.NO_REVERSE) {
										// Якщо це запит з реверсом, обробляємо як звичайний запит
									} else if(((httpRequest.method.compareTo("POST") == 0 && httpRequest.contentLength == 119) || 
												(httpRequest.method.compareTo("PUT") == 0 && httpRequest.contentLength == 959)) && 
												httpRequest.Content_Type.compareTo("application/octet-stream") != 0) {
										// Обробка текстових запитів на порт 8083
										httpResponse = handlePort8083(httpRequest.body, 101);
									} else if(((httpRequest.method.compareTo("POST") == 0 && httpRequest.contentLength == 40) || 
												(httpRequest.method.compareTo("PUT") == 0 && httpRequest.contentLength == 320)) && 
												httpRequest.Content_Type.compareTo("application/octet-stream") == 0) {
										// Обробка бінарних запитів на порт 8083
										httpResponse = handlePort8083(httpRequest.bodyData, 101);
									}
									break;
								case 80:
									// Порт 80 - завжди неавтентифіковані запити
									httpResponse = handlePort80(httpRequest);
									break;
								case 443:
									// Порт 443 - перевіряємо параметр reestr для RegistrUsers
									if(httpRequest.chkZnach("authorization", "check")) {
										if(httpRequest.userID == 0) {
											httpResponse = new HTTPResponse(400);
											httpResponse.set_fl_err_prnt_hdr(false);
											httpResponse.setMsg("Client " + httpRequest.clientAddress + ". Authorization check failed for Session ID: " + httpRequest.X_Session_ID);
										}
										else {
											httpResponse = new HTTPResponse(200);
											httpResponse.set_fl_err_prnt_hdr(false);
											httpResponse.setMsg("Client " + httpRequest.clientAddress + ". Authorization check passed for user ID: " + httpRequest.userID);
										}
									} else if (httpRequest.chkParam("reestr")) {
										// Запити до RegistrUsers
										httpResponse = RegistrUsers.reestr(httpRequest);
									} else if (httpRequest.userID == 0) {
										// Неавтентифіковані запити на порт 443
										httpResponse = handlePort80(httpRequest);
									} else {
										// Автентифіковані запити на порт 443
										httpResponse = handlePort443(httpRequest);
									}
									break;
								default:
									httpResponse = new HTTPResponse("HTTP/1.1 400 ERROR\r\n\r\n", null);
									break;
							}
						}
					} catch (NullPointerException e) {
						System.out.println("Помилка обробки запиту: " + e.getMessage());
						e.printStackTrace(); // Виводимо стек помилки для дебагу
						httpResponse = new HTTPResponse("HTTP/1.1 500 Internal Server Error\r\n\r\n", null);
					}
				}
				else {
					//httpRequest.prnt();
					if(Configs.getBoolean("ban_response") || Configs.getBoolean("Firewall")) {
						httpRequest.revers = HTTPRequest.ReversType.BANRESPONSE;
						httpResponse = ReverseProxy.handleReverseRequest(httpRequest);
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
				if(httpRequest.close_connect_flag) {
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

	private HTTPResponse handlePort8083(String requestBody, int marker) {
		DBClass dBClass = DBClass.getInstance();
		String vidp = dBClass.handleRequest(requestBody, marker);
		return new HTTPResponse("HTTP/1.1 200 OK\r\n" + "Content-Length: " + vidp.length() + "\r\n" + "\r\n", vidp.getBytes(), "\t\trequest on port 8083: " + ((vidp.length() + 1) / 3) + " bytes (txt)");
	}

	private HTTPResponse handlePort8083(byte[] requestBody, int marker) {
		DBClass dBClass = DBClass.getInstance();
		String vidp = dBClass.handleRequest(requestBody, marker);
		
		byte[] bodyTmp = new byte[11];
		byte reshta = 0;
		short blocks = 0;
		try {
			String[] hexArray = vidp.split("\\+");
			bodyTmp = new byte[hexArray.length + 3];
			for (int i = 0; i < hexArray.length; i++) {
				bodyTmp[i] = (byte)Integer.parseInt(hexArray[i], 16);
			}
			reshta = (byte)(hexArray.length % 3);
			blocks = (short)(hexArray.length / 3 + (reshta == 0 ? 0 : 1));
		} catch(NumberFormatException e) {
			bodyTmp = new byte[vidp.length() + 3];
			byte[] bodyTmp2 = vidp.getBytes();
			for (int i = 0; i < vidp.length(); i++) {
				bodyTmp[i] = bodyTmp2[i];
			}
			reshta = (byte)(vidp.length() % 3);
			blocks = (short)(vidp.length() / 3 + (reshta == 0 ? 0 : 1));
		} catch(Exception e) {
			e.printStackTrace();
		}
		/*for(byte b : bodyTmp)
			System.out.printf("%02X ", b);
		System.out.println();
		*/
		if(blocks == 0) {
			System.out.println("ERROR 371");
			return new HTTPResponse("HTTP/1.1 500 NOT OK\r\n\r\n", null);
		}
		short length2 = (short)(blocks * 4 + 1);
		byte[] vidp2 = new byte[length2];
		for(short ii = 0; ii < blocks; ii++) {
			vidp2[ii * 4 + 0] = (byte)(0x40 | ((bodyTmp[ii * 3 + 0] & 0b11111100) >> 2));
			vidp2[ii * 4 + 1] = (byte)(0x40 | ((bodyTmp[ii * 3 + 0] & 0b00000011) << 4) | ((bodyTmp[ii * 3 + 1] & 0b11110000) >> 4));
			vidp2[ii * 4 + 2] = (byte)(0x40 | ((bodyTmp[ii * 3 + 1] & 0b00001111) << 2) | ((bodyTmp[ii * 3 + 2] & 0b11000000) >> 6));
			vidp2[ii * 4 + 3] = (byte)(0x40 | ((bodyTmp[ii * 3 + 2] & 0b00111111) >> 0));
		}
		for(short ii = 0; ii < length2; ii++) {
			if(vidp2[ii] == (byte)0x5c)
				vidp2[ii] = (byte)0x2c;
		}
		vidp2[length2 - 1] = (byte)(0x20 | reshta);
		//printHex(vidp2);
		return new HTTPResponse("HTTP/1.1 200 OK\r\n" + "Content-Length: " + vidp2.length + "\r\n" + "\r\n", vidp2, "request on port 8083: " + ((vidp.length() + 1) / 3) + " bytes (hex)");
	}
	
	/**
	 * Обробка неавтентифікованих запитів (порт 80 або порт 443 з userID = 0)
	 * Доступ тільки до файлів у директорії www80
	 */
	private HTTPResponse handlePort80(HTTPRequest httpRequest) {
		// Для GET і HEAD запитів використовуємо GetRes80
		if (httpRequest.method.compareTo("GET") == 0 || httpRequest.method.compareTo("HEAD") == 0) {
			return GetRes80.getRes80(httpRequest);
		}
		if(Configs.getBoolean("ban_response")) {
			httpRequest.revers = HTTPRequest.ReversType.BANRESPONSE;
			return ReverseProxy.handleReverseRequest(httpRequest);
		}
		return new HTTPResponse(400);
	}

	private void printHex(byte[] bytes) {
		char[] bodyChArr = new char[bytes.length];
		int bytesRead = 0, contentLength;
		while (bytesRead < bytes.length) {
			int result = bytes.length - bytesRead;
			if (result == -1) {
				break;
			}
			bytesRead += result;
		}
		short blocks;
		byte reshta = (byte)(bytes[bytes.length - 1] & 0x03);
		for(short ii = 0; ii < bytes.length - 1; ii++) {
			if(bytes[ii] == 0x2c)
				bytes[ii] = 0x5c;
		}
		if(reshta == 0) {
			blocks = (short)((bytes.length - 1) / 4);
			contentLength = blocks * 3;
		}
		else {
			blocks = (short)((bytes.length - 1) / 4);
			contentLength = (blocks - 1) * 3 + reshta;
		}
		byte[] bodyDataTmp = new byte[contentLength + 3];
		for(short ii = 0; ii < blocks; ii++) {
			bodyDataTmp[ii * 3 + 0] = (byte)(((bytes[ii * 4 + 0] & 0x3f) << 2) | ((bytes[ii * 4 + 1] & 0x30) >> 4));
			bodyDataTmp[ii * 3 + 1] = (byte)(((bytes[ii * 4 + 1] & 0x0f) << 4) | ((bytes[ii * 4 + 2] & 0x3c) >> 2));
			bodyDataTmp[ii * 3 + 2] = (byte)(((bytes[ii * 4 + 2] & 0x03) << 6) | ((bytes[ii * 4 + 3] & 0x3f) >> 0));
		}
		byte[] bodyData = new byte[contentLength];
		for(short ii = 0; ii < contentLength; ii++) {
			bodyData[ii] = bodyDataTmp[ii];
		}
		System.out.println("\rreceived");
		for(int ii = 0; ii < bodyData.length; ii++) {
			System.out.printf("%02X ", bodyData[ii]);
		}
		System.out.println("\t" + bodyData.length);
	}
		
	/**
	 * Обробка автентифікованих запитів на порт 443 (userID != 0)
	 * Повний доступ до функціоналу
	 */
	private HTTPResponse handlePort443(HTTPRequest httpRequest) {
		// Перевіряємо, чи це реверс-запит і обробляємо його через ReverseProxy
		if(httpRequest.revers != HTTPRequest.ReversType.NO_REVERSE) {
			HTTPResponse reverseResponse = ReverseProxy.handleReverseRequest(httpRequest);
			if (reverseResponse != null) {
				// Якщо отримали відповідь від ReverseProxy, повертаємо її
				return reverseResponse;
			}
			// Якщо ReverseProxy повернув null, продовжуємо обробку як звичайний запит
		}
		
		// Обробка GET і HEAD запитів
		if (httpRequest.method.compareTo("GET") == 0 || httpRequest.method.compareTo("HEAD") == 0) {
			return GetRes.getRes(httpRequest);
		}
		
		// Обробка POST запитів
		else if (httpRequest.method.compareTo("POST") == 0) {
			return PostRes.postRes(httpRequest);
		}
		
/* 		// Обробка PUT запитів
		else if (httpRequest.method.compareTo("PUT") == 0) {
			return PutRes.putRes(httpRequest);
		}
		
		// Обробка DELETE запитів
		else if (httpRequest.method.compareTo("DELETE") == 0) {
			return DeleteRes.deleteRes(httpRequest);
		}
 */		
		if(Configs.getBoolean("ban_response")) {
			httpRequest.revers = HTTPRequest.ReversType.BANRESPONSE;
			return ReverseProxy.handleReverseRequest(httpRequest);
		}
		return new HTTPResponse(400);
	}
	
	private HTTPResponse handleWWW80Scripts(HTTPRequest httpRequest) {
		if(httpRequest.path.compareTo("/www80_scripts/scan_directory") == 0) {
			return GetRes80.scanDirectory(httpRequest); 
		}
		if(Configs.getBoolean("ban_response")) {
			httpRequest.revers = HTTPRequest.ReversType.BANRESPONSE;
			return ReverseProxy.handleReverseRequest(httpRequest);
		}
		return new HTTPResponse(400);
	}
	
	private HTTPResponse handleWWWScripts(HTTPRequest httpRequest) {
		if(httpRequest.userID != 0) {
			if(httpRequest.path.compareTo("/www_scripts/get_links") == 0) {
				return GetRes.getLinks(httpRequest);
			}
			else if(httpRequest.path.compareTo("/www_scripts/scan_dwnld_directory") == 0 && Configs.getBoolean("download")) {
				return GetRes.scanDwnldDirectory(httpRequest);
			}
			else if(httpRequest.path.compareTo("/www_scripts/logout") == 0) {
				KeyManager.logout(httpRequest.X_Session_ID, httpRequest.clientAddress);
				HTTPResponse httpResponse = new HTTPResponse(200);
				httpResponse.set_fl_err_prnt_hdr(false);
				httpResponse.setMsg("Client " + httpRequest.clientAddress + ". Logout passed for user ID: " + httpRequest.userID);
				return httpResponse;
			}
		}
		else {
			HTTPResponse httpResponse = new HTTPResponse(403);
			httpResponse.set_fl_err_prnt_hdr(false);
			httpResponse.setMsg("www_scripts: " + httpRequest.clientAddress + ". Not authorized");
			return httpResponse;
		}
		if(Configs.getBoolean("ban_response")) {
			httpRequest.revers = HTTPRequest.ReversType.BANRESPONSE;
			return ReverseProxy.handleReverseRequest(httpRequest);
		}
		return new HTTPResponse(400);
	}
}
