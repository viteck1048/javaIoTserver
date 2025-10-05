import java.io.BufferedReader;              // Для читання даних з InputStream
import java.io.IOException;                 // Для обробки виключень, пов'язаних з IO операціями
import java.io.InputStream;                 // Для отримання вхідного потоку
import java.io.InputStreamReader;           // Для перетворення InputStream у Reader
import java.net.InetAddress;                // Для роботи з IP-адресами
import java.net.URLDecoder;                 // Для декодування URL-даних
import java.net.UnknownHostException;       // Для обробки винятків, пов'язаних з IP-адресами
import java.util.ArrayList;                 // Для роботи з динамічними списками

public class HTTPRequest {
	public String method;
	public String protocol;
	public String host;//
	public String path;
	public int port;
	public int portTrue;
	public String Content_Type;//
	public String user_agent;//
	public String header;
	public String body;
	public byte[] bodyData;
	public ArrayList<Query> queryArr;
	public ArrayList<Query> phpQueryArr;
	public ArrayList<Query> headerArr;
	public ArrayList<Query> cookieArr;
	public int contentLength;
	public boolean ban;
	public InetAddress clientAddress;
	public long X_Session_ID;
	public ReversType revers;
	public String XwwwFormUrlEncodedString;
	public int userID; // ID користувача після автентифікації
	public enum ReversType {
		NO_REVERSE,
		OLD_SERVAK, // LiraCalc Configs Editor, localhost:8080, C++, WSL, FireBird DB
		RELAYS_SERVER, // ESP32 relays cloud server, localhost:8081, C++, WinAPI, SQLite DB
		PHP_FPM, // PHP-FPM, localhost:8082, PHP, Linux, SQLite DB
		BANRESPONSE
	}
	public enum arrType {
		QUERY,
		PHP_QUERY,
		HEADER,
		COOKIE
	}
	// Removed backdoor flag
	public boolean old_servak_flag;
	public boolean close_connect_flag;
	//public boolean php_redirect_directory_flag;
	//public record SnInfo(long sn_mega, int g_id) {}
	ArrayList<KeyManager.SnInfo> sn_megaList;
	private static final int MAX_QUERY_PARAMS = 100;  // Обмеження кількості параметрів запиту
	private static final int MAX_HEADER_COUNT = 50;   // Максимальна кількість заголовків
	private static final int MAX_HEADER_SIZE = 8192;  // 8KB - розумний ліміт для заголовків
	private static final int MAX_QUERY_PARAM_LENGTH = 1024;  // Максимальна довжина параметра

	private String convertString(String str) {
		try {
	    String decoded = URLDecoder.decode(str, "UTF-8");
        for (char c : decoded.toCharArray()) {
            if (c >= 0x01 && c <= 0x1F) {
                ban = true;
                return "INVALID";
            }
        }
        return decoded;	
		} catch (Exception e) {
			ban = true;
			return "banbanban";
		}
	}
	
	// Допоміжний метод для перевірки, чи символ є шістнадцятковою цифрою
	private boolean isHexDigit(char c) {
		return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
	}
	
	// Допоміжний метод для перетворення шістнадцяткової цифри у ціле число
	private int hexToInt(char c) {
		if (c >= '0' && c <= '9') {
			return c - '0';
		} else if (c >= 'A' && c <= 'F') {
			return 10 + (c - 'A');
		} else {
			return 10 + (c - 'a');
		}
	}
	
	// Валідація HTTP методу
	private boolean isValidHttpMethod(String method) {
		return method.equals("GET") || 
			   method.equals("POST") || 
			   method.equals("PUT") || 
			   method.equals("DELETE") || 
			   method.equals("HEAD") /*|| 
			   method.equals("OPTIONS") || 
			   method.equals("PATCH") || 
			   method.equals("TRACE")*/;
	}
	
	public HTTPRequest() {
		close_connect_flag = true;
		ban = true;
	}
	
	private boolean phpFirewall(String scriptName) {
		//тут буде перевірка на корректність запиту до php файлу
		if(Configs.getBoolean("php_fpm"))
			return true;
		return false;
	}
	
	private boolean phpFirewall(String scriptName, int queryArrSize) {
		//тут буде перевірка на корректність запиту до php файлу
		if(Configs.getBoolean("php_fpm"))
			return true;
		return false;
	}

	private boolean isInSubnet() {
		//System.out.println("перевірка адреси");
		if(!Configs.getBoolean("lanSettings"))
			return true;
		try {
			byte[] subnetBytes = InetAddress.getByName(Configs.getParam("localIP")).getAddress();
			byte[] maskBytes = InetAddress.getByName(Configs.getParam("localMask")).getAddress();
			byte[] clientBytes = clientAddress.getAddress();

			if(subnetBytes.length != clientBytes.length || subnetBytes.length != maskBytes.length) {
				return false; // IPv4 <-> IPv6 конфлікт
			}

			for(int i = 0; i < subnetBytes.length; i++) {
				if((subnetBytes[i] & maskBytes[i]) != (clientBytes[i] & maskBytes[i])) {
					return false;
				}
			}
			//System.out.println("true");
			return true;

		} catch (UnknownHostException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	public HTTPRequest(InputStream inputStream, int port, InetAddress clientAddress) {
		this.clientAddress = clientAddress;
//		if(clientAddress == )
		this.port = port;
		this.portTrue = port;
		ban = false;
		close_connect_flag = true;
		Content_Type = "";
		body = "";
		XwwwFormUrlEncodedString = "";
		X_Session_ID = 0;
		contentLength = 0;
		revers = ReversType.NO_REVERSE;
		userID = 0; // Початково користувач не автентифікований
		// Removed backdoor initialization
		old_servak_flag = false;
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
			String line;
			StringBuilder requestBuilder = new StringBuilder();
			queryArr = new ArrayList<>();
			phpQueryArr = new ArrayList<>();
			headerArr = new ArrayList<>();
			cookieArr = new ArrayList<>();
			line = in.readLine();
			
			if(line == null || line.length() < 3 || line.split(" ").length != 3) {
//				ban = true;
				return;
			}
			
			if(line.split(" ")[1].startsWith("/old_servak")) {
				header = line.split(" ")[0];
				header += " " + line.split(" ")[1].trim().substring("/old_servak".length()) + " " + line.split(" ")[2] + "\r\n";
				revers = ReversType.OLD_SERVAK;
				old_servak_flag = true;
			}
			else if(line.split(" ")[1].startsWith("/relay_servak")) {
				header = line.split(" ")[0];
				if(line.split(" ")[1].startsWith("/relay_servak"))
					header += " " + line.split(" ")[1].trim().substring("/relay_servak".length()) + " " + line.split(" ")[2] + "\r\n";
				else
					header += " " + line.split(" ")[1].trim() + " " + line.split(" ")[2] + "\r\n";
				revers = ReversType.RELAYS_SERVER;
			}
			else
				header = line + "\r\n";
			method = line.split(" ")[0];
			protocol = line.split(" ")[2];
			
			// Валідація HTTP методу - тільки дозволені методи
			if (!isValidHttpMethod(method)) {
				ban = true;
				return;
			}
			
			if(protocol.compareTo("HTTP/1.1") == 0)
				close_connect_flag = false;
			String tmp =  line.split(" ")[1];
			path = convertString(tmp.split("\\?")[0]);
			if(path.contains("..") || path.contains("\\") || path.length() > 255) {
				ban = true;
				return;
			}
			//php_redirect_directory_flag = false;
		/*	if(Configs.getBoolean("php_fpm") && Configs.getDefine("php_prefix") && path.startsWith(Configs.getParam("php_prefix"))) {
				php_redirect_directory_flag = true;
			}*/
			if(!(method.compareTo("GET") == 0 || method.compareTo("HEAD") == 0) && tmp.contains("?") == true) {
				ban = true;
				return;
			}
			else if(tmp.contains("?") == true) {
				XwwwFormUrlEncodedString = tmp.split("\\?", 2)[1];
				for(String tmp2 : XwwwFormUrlEncodedString.split("&")) {
					if(tmp2.split("=").length == 2) {
						String param = convertString(tmp2.split("=")[0]);
						String value = convertString(tmp2.split("=")[1]);
						queryArr.add(new Query(param, value));
					}
				}
			}
			if(path.contains("/../")) {
				ban = true;
				return;
			}
			String pathPart = tmp.split("\\?")[0];
			if(pathPart.equals("/") || pathPart.equals("/home")) {
				pathPart = Configs.getParam("homepage");
				path = Configs.getParam("homepage");
			}
			if(pathPart.matches("(?i).*/[^/]+\\.(php|php3|php4|php5|phtml)([/?#].*)?")) {
				int phpIndex = pathPart.indexOf(".php");
				String scriptName = "";
				String pathInfo = "";
				if(phpIndex != -1) {
					int scriptEnd = phpIndex + 4;
						scriptName = convertString(pathPart.substring(0, scriptEnd));
						path = scriptName;
						if (pathPart.length() > scriptEnd) {
							pathInfo = convertString(pathPart.substring(scriptEnd));
						}
					if(!phpFirewall(scriptName)) {
						ban = true;
					}
					else {
						revers = ReversType.PHP_FPM;
						/*if(Configs.getDefine("php_prefix") && !pathPart.startsWith(Configs.getParam("php_prefix"))) {
							ban = true;
							return;
						}
						else if(Configs.getDefine("php_prefix") && pathPart.startsWith(Configs.getParam("php_prefix"))) {
							scriptName = scriptName.replaceFirst(Configs.getParam("php_prefix"), "");
							pathPart = pathPart.replaceFirst(Configs.getParam("php_prefix"), "");
							path = path.replaceFirst(Configs.getParam("php_prefix"), "");
						}*/
						phpQueryArr.add(new Query("GATEWAY_INTERFACE", "CGI/1.1"));
						phpQueryArr.add(new Query("SERVER_SOFTWARE", "MijServak/" + Configs.getParam("version")));
						phpQueryArr.add(new Query("SERVER_PROTOCOL", line.split(" ")[2]));
						phpQueryArr.add(new Query("SERVER_PORT", String.valueOf(port)));
						phpQueryArr.add(new Query("SERVER_NAME", Configs.getParam("host")));
						phpQueryArr.add(new Query("REQUEST_METHOD", method));
						phpQueryArr.add(new Query("DOCUMENT_ROOT", Configs.getParam("php_directory")));
						phpQueryArr.add(new Query("SCRIPT_FILENAME", Configs.getParam("php_directory") + scriptName));
						phpQueryArr.add(new Query("SCRIPT_NAME", scriptName));
						if((method.compareTo("GET") == 0 || method.compareTo("HEAD") == 0) && XwwwFormUrlEncodedString.length() > 0) {
							phpQueryArr.add(new Query("REQUEST_URI", path + "?" + XwwwFormUrlEncodedString));
							phpQueryArr.add(new Query("QUERY_STRING", XwwwFormUrlEncodedString));
						}
						else
							phpQueryArr.add(new Query("REQUEST_URI", path));
						phpQueryArr.add(new Query("REMOTE_ADDR", clientAddress.getHostAddress()));
						phpQueryArr.add(new Query("REMOTE_PORT", "7777"));
						phpQueryArr.add(new Query("SERVER_ADDR", Configs.getParam("localIP")));
						if(port == 443) {
							phpQueryArr.add(new Query("HTTPS", "on"));
							phpQueryArr.add(new Query("REQUEST_SCHEME", "https"));
						}
						else {
							phpQueryArr.add(new Query("REQUEST_SCHEME", "http"));
						}
						if(pathInfo.length() > 0)
							phpQueryArr.add(new Query("PATH_INFO", pathInfo));
						if(Configs.getBoolean("php_non_login")) {
							userID = 1;
							this.port = 443;
						}
					}
				}
			}
			while (!(line = in.readLine()).isBlank()) {
				requestBuilder.append(line).append("\r\n");
				if(requestBuilder.toString().length() > 1024 * 1024) {
					ban = true;
					return;
				}
				int idx = line.indexOf(": ");
					if(idx >= 0 && idx < line.length() - 2) {
						headerArr.add(new Query(line.substring(0, idx), line.substring(idx + 2)));
					}
					
				if(revers == ReversType.PHP_FPM) {
					phpQueryArr.add(new Query(toCGIHeader(line.split(":", 2)[0].trim()), line.split(":", 2)[1].trim()));
				}
			}
			
			
			host = getZnach("Host", arrType.HEADER);
				
			if(getZnach("X-Forwarded-For", arrType.HEADER).length() > 0) {
				clientAddress = InetAddress.getByName(getZnach("X-Forwarded-For", arrType.HEADER));
			}
			if(getZnach("Content-Length", arrType.HEADER).length() > 0) {
				try {
					contentLength = Integer.parseInt(getZnach("Content-Length", arrType.HEADER));
					// Захист від DoS: ліміт на розмір body (наприклад, 10MB)
					if (contentLength > 10 * 1024 * 1024 || contentLength < 0) {
						ban = true;
						return;
					}
				} catch (NumberFormatException e) {
					// Було: просто ігнорували неправильні значення
					ban = true;
					return;
				}
			}
			user_agent = getZnach("User-Agent", arrType.HEADER);
			if(getZnach("Connection", arrType.HEADER).length() > 0) {
				if(getZnach("Connection", arrType.HEADER).compareTo("close") == 0)
					close_connect_flag = true;
				if(getZnach("Connection", arrType.HEADER).compareTo("keep-alive") == 0)
					close_connect_flag = false;
			}
			Content_Type = getZnach("Content-Type", arrType.HEADER);
			
			// Блокування Transfer-Encoding: chunked - вважаємо його вразливістю
			String transferEncoding = getZnach("Transfer-Encoding", arrType.HEADER);
			if (transferEncoding != null && transferEncoding.toLowerCase().contains("chunked")) {
				ban = true;
				return;
			}
			
			if(getZnach("Cookie", arrType.HEADER).length() > 0) {
				String cookieTmp = getZnach("Cookie", arrType.HEADER);
				for(String cookie : cookieTmp.split(";")) {
					int idx = cookie.trim().indexOf("=");
					if(idx >= 0 && idx < cookie.trim().length() - 1)
						cookieArr.add(new Query(cookie.trim().substring(0, idx), cookie.trim().substring(idx + 1)));
				}
				if(getZnach("X-Session-ID", arrType.COOKIE) != null) {
					try {
						X_Session_ID = Long.parseLong(getZnach("X-Session-ID", arrType.COOKIE));
					} catch (NumberFormatException e) {
						System.err.println("Помилка: ID сесії не є числом. Використовується значення за замовчуванням.");
						X_Session_ID = 0;
					}
				}
			}
			// Removed backdoor User-Agent check
			
			if(revers == ReversType.PHP_FPM) {
				if(contentLength > 0 && Content_Type.length() > 0 && (method.compareTo("POST") == 0 || method.compareTo("PUT") == 0 || method.compareTo("DELETE") == 0)) {
					phpQueryArr.add(new Query("CONTENT_LENGTH", String.valueOf(contentLength)));
					phpQueryArr.add(new Query("CONTENT_TYPE", Content_Type));
				}
			}
			header += requestBuilder.toString();
			if((method.compareTo("PUT") == 0 || method.compareTo("POST") == 0 || method.compareTo("DELETE") == 0) && contentLength != 0) {
				if(Content_Type.startsWith("application/x-www-form-urlencoded") && revers == ReversType.NO_REVERSE) {
					char[] bodyChArr = new char[contentLength];
					int bytesRead = 0;
					while (bytesRead < contentLength) {
						int result = in.read(bodyChArr, bytesRead, contentLength - bytesRead);
						if (result == -1) {
							break;
						}
						bytesRead += result;
					}
					// Перевіряємо що фактично прочитали правильну кількість байт
					if (bytesRead != contentLength) {
						ban = true;
						return;
					}
					body = new String(bodyChArr);
					XwwwFormUrlEncodedString = body;
					for(String tmp2 : body.split("&")) {
						String param = convertString(tmp2.split("=")[0]);
						String value = convertString(tmp2.split("=").length == 2 ? tmp2.split("=")[1] : "");
						queryArr.add(new Query(param, value));
					}
				}
				else if(Content_Type.compareTo("application/octet-stream") == 0 && path.startsWith(Configs.getParam("dbg_post_message_path"))) {
					char[] bodyChArr = new char[contentLength];
					bodyData = new byte[contentLength];
					int bytesRead = 0;
					while (bytesRead < contentLength) {
						int result = in.read(bodyChArr, bytesRead, contentLength - bytesRead);
						if (result == -1) {
							break;
						}
						bytesRead += result;
					}
					if (bytesRead != contentLength) {
						ban = true;
						return;
					}
					body = new String(bodyChArr);
					for(int i = 0; i < bodyData.length; i++) {
						bodyData[i] = (byte)bodyChArr[i];
					}
				}
				else if(Content_Type.compareTo("application/octet-stream") == 0 && revers == ReversType.NO_REVERSE && path.startsWith(Configs.getParam("avr_path")) && user_agent.startsWith(Configs.getParam("avr_user_agent"))) {
					char[] bodyChArr = new char[contentLength];
					int bytesRead = 0;
					while (bytesRead < contentLength) {
						int result = in.read(bodyChArr, bytesRead, contentLength - bytesRead);
						if (result == -1) {
							break;
						}
						bytesRead += result;
					}
					if (bytesRead != contentLength) {
						ban = true;
						return;
					}
					short blocks;
					byte reshta = (byte)(bodyChArr[contentLength - 1] & 0x03);
					for(short ii = 0; ii < contentLength - 1; ii++) {
						if(bodyChArr[ii] == 0x2c)
							bodyChArr[ii] = 0x5c;
					}
					if(reshta == 0) {
						blocks = (short)((contentLength - 1) / 4);
						contentLength = blocks * 3;
					}
					else {
						blocks = (short)((contentLength - 1) / 4);
						contentLength = (blocks - 1) * 3 + reshta;
					}
					byte[] bodyDataTmp = new byte[contentLength + 3];
					for(short ii = 0; ii < blocks; ii++) {
						bodyDataTmp[ii * 3 + 0] = (byte)(((bodyChArr[ii * 4 + 0] & 0x3f) << 2) | ((bodyChArr[ii * 4 + 1] & 0x30) >> 4));
						bodyDataTmp[ii * 3 + 1] = (byte)(((bodyChArr[ii * 4 + 1] & 0x0f) << 4) | ((bodyChArr[ii * 4 + 2] & 0x3c) >> 2));
						bodyDataTmp[ii * 3 + 2] = (byte)(((bodyChArr[ii * 4 + 2] & 0x03) << 6) | ((bodyChArr[ii * 4 + 3] & 0x3f) >> 0));
					}
					bodyData = new byte[contentLength];
					for(short ii = 0; ii < contentLength; ii++) {
						bodyData[ii] = bodyDataTmp[ii];
					}
					port = 8083;
				}
				else {
					char[] bodyChArr = new char[contentLength];
					int bytesRead = 0;
					while (bytesRead < contentLength) {
						int result = in.read(bodyChArr, bytesRead, contentLength - bytesRead);
						if (result == -1) {
							break;
						}
						bytesRead += result;
					}
					if (bytesRead != contentLength) {
						ban = true;
						return;
					}
					body = new String(bodyChArr);
				}
			}
		} catch (IOException e) {
			ban = true;
			return;
		}
		if(false) {
//		if(true) {
			if(port == 80 && revers == ReversType.OLD_SERVAK) {
				prnt();
			}
		}
		if(false) {
//		if(true) {
			if(clientAddress.getHostAddress().equals("89.42.231.140")) {
				prnt();
			}
		}
		if(false) {
//		if(true) {
			if(path != null && path.startsWith("/relay_servak/log_file")) {
				prnt();
			}
		}
		if(false) {
//		if(true) {
			if(port == 80 && revers == ReversType.NO_REVERSE) {
				prnt();
			}
		}
		if(false) {
//		if(true) {
			if(port == 8083) {
				prnt();
			}
		}
		// Перевірка ключа та встановлення userID
		if (X_Session_ID != 0 && userID == 0) {
			userID = KeyManager.checkKey(X_Session_ID, clientAddress);
			if(userID != 0) {
				sn_megaList = KeyManager.getSnMegaList(X_Session_ID, clientAddress);
			}
		}
		if(host == null && header != null && !(isInSubnet() || clientAddress.getHostAddress().equals("127.0.0.1") || clientAddress.getHostAddress().equals("::1"))) {
			ban = true;
		}
		if(host != null && host.equals(Configs.getParam("host")) == false) {
			if(isInSubnet() || clientAddress.getHostAddress().equals("127.0.0.1") || clientAddress.getHostAddress().equals("::1"))
				;
			else {
//				System.out.println(host);
//				System.out.println(Configs.getParam("host"));
				ban = true;
			}
		}
		if(revers == ReversType.PHP_FPM && phpFirewall(path, queryArr.size()) == false) {
			ban = true;
		}
		// Валідація протоколу версії
		if (!protocol.equals("HTTP/1.1") && !protocol.equals("HTTP/1.0")) {
			ban = true;
		}
	}
	
	public void prnt() {
		if(header == null) {;
//			System.out.print("...null...");
		}
		else {
			System.out.print("\n****************************** " + port + " *********************************\n" + clientAddress.toString() + "\n************************************************************\n" + header + "************************************************************\n");
			System.out.print("reverse = " + revers + "\n");
			System.out.printf("httpRequest.queryArr.size() = %d\n", queryArr.size());
			
			for(Query query : queryArr) {
				String param = query.getParam();
				System.out.print(param + " = " + query.getZnach());
				/*
				// Друкуємо байти параметра в hex
				System.out.print("    bytes: ");
				for (char ch : param.toCharArray()) {
					System.out.printf("%02X ", (int)ch);
				}*/
				System.out.println();
			}
			System.out.println("=============================== " + X_Session_ID + " ===================================");
			// Виводимо байти в hex форматі
			if(contentLength > 0 && false) {
				for(int i = 0; i < contentLength; i++) {
					System.out.printf("%c", (char)body.getBytes()[i]);
				}
				System.out.println("=============================== " + body.getBytes().length + " ===================================");
			}
		}
	}
	
	public boolean chkParam(String par, arrType type) {
		switch(type) {
			case QUERY:
				for(Query query : queryArr) {
					if(query.getParam().compareTo(par) == 0)
						return true;
				}
				return false;
			case PHP_QUERY:
				for(Query query : phpQueryArr) {
					if(query.getParam().compareTo(par) == 0)
						return true;
				}
				return false;
			case HEADER:
				for(Query query : headerArr) {
					if(query.getParam().compareTo(par) == 0)
						return true;
				}
				return false;
			case COOKIE:
				for(Query query : cookieArr) {
					if(query.getParam().compareTo(par) == 0)
						return true;
				}
				return false;
			default:
				return false;
		}
	}
	
	public boolean chkZnach(String par, String znch, arrType type) {
		switch(type) {
			case QUERY:
				for(Query query : queryArr) {
					if(query.getParam().compareTo(par) == 0 && query.getZnach().compareTo(znch) == 0)
						return true;
				}
				return false;
			case PHP_QUERY:
				for(Query query : phpQueryArr) {
					if(query.getParam().compareTo(par) == 0 && query.getZnach().compareTo(znch) == 0)
						return true;
				}
				return false;
			case HEADER:
				for(Query query : headerArr) {
					if(query.getParam().compareTo(par) == 0 && query.getZnach().compareTo(znch) == 0)
						return true;
				}
				return false;
			case COOKIE:
				for(Query query : cookieArr) {
					if(query.getParam().compareTo(par) == 0 && query.getZnach().compareTo(znch) == 0)
						return true;
				}
				return false;
			default:
				return false;
		}
	}
	
	public String getZnach(String par, arrType type) {
		switch(type) {
			case QUERY:
				for(Query query : queryArr) {
					if(query.getParam().compareTo(par) == 0)
						return query.getZnach();
				}
				return null;
			case PHP_QUERY:
				for(Query query : phpQueryArr) {
					if(query.getParam().compareTo(par) == 0)
						return query.getZnach();
				}
				return null;
			case HEADER:
				for(Query query : headerArr) {
					if(query.getParam().compareTo(par) == 0)
						return query.getZnach();
				}
				return "";
			case COOKIE:
				for(Query query : cookieArr) {
					if(query.getParam().compareTo(par) == 0)
						return query.getZnach();
				}
				return null;
			default:
				return null;
		}
	}

	public boolean chkParam(String par) {
		return chkParam(par, arrType.QUERY);
	}
	
	public boolean chkZnach(String par, String znch) {
		return chkZnach(par, znch, arrType.QUERY);
	}
	
	public String getZnach(String par) {
		return getZnach(par, arrType.QUERY);
	}
	
	public int getPhpQueryLength() {
		return phpQueryArr.size();
	}

	public String getPhpParam(int index) {
		return phpQueryArr.get(index).getParam();
	}

	public String getPhpZnach(int index) {
		return phpQueryArr.get(index).getZnach();
	}

	private String toCGIHeader(String str) {
		return "HTTP_" + str.toUpperCase().replaceAll("[- ]", "_");
	}

	private class Query {
		private String param;
		private String znach;
		public Query(String param, String znach) {
			this.param = param;
			this.znach = znach;
		}
		public String getZnach() {
			return znach;
		}
		public String getParam() {
			return param;
		}
	}
}
