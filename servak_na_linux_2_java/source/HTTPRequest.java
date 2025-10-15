import java.net.InetAddress;                // Для роботи з IP-адресами
import java.net.URLDecoder;                 // Для декодування URL-даних
import java.net.UnknownHostException;       // Для обробки винятків, пов'язаних з IP-адресами
import java.util.ArrayList;                 // Для роботи з динамічними списками
// Для обробки виключень, пов'язаних з IO операціями
// Для отримання вхідного потоку
import java.io.*;
import java.util.zip.*;

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
	private boolean chunked_flag;
	private boolean gzip_flag;
	private InputStream inputStream;

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
	
	private boolean isHexDigit(char c) {
		return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
	}
	
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

	private boolean checkHost(String host) {
		if(host.compareTo(Configs.getParam("host")) == 0)
			return true;
		if(host.compareTo("localhost") == 0)
			return true;
		if(host.compareTo("127.0.0.1") == 0)
			return true;
		if(host.compareTo("::1") == 0)
			return true;
		if(isInSubnet())
			return true;
		return false;
	}

	private byte[] readBody(InputStream in, int contentLength, boolean chunked_flag, boolean gzip_flag) throws IOException {
		if(chunked_flag) {
			ByteArrayOutputStream bodyData = new ByteArrayOutputStream();

			while (true) {
				// Read chunk size line (hex number followed by CRLF)
				StringBuilder sizeLine = new StringBuilder();
				int ch;
				while ((ch = in.read()) != -1) {
					if (ch == '\r') {
						// Check for LF
						int nextCh = in.read();
						if (nextCh == '\n') {
							break;
						} else {
							// Put back the character if it's not LF
							if (nextCh != -1) {
								// This is a bit tricky with InputStream, we'll handle it differently
								throw new IOException("Invalid chunk size line format");
							}
						}
					}
					sizeLine.append((char) ch);
				}

				// Parse chunk size (ignore chunk extensions for now)
				String sizeStr = sizeLine.toString().trim().split(";")[0];
				int chunkSize = 0;
				try {
					chunkSize = Integer.parseInt(sizeStr, 16);
				} catch (NumberFormatException e) {
					throw new IOException("Invalid chunk size: " + sizeStr);
				}

				// Check if this is the last chunk (size 0)
				if (chunkSize == 0) {
					// Read final CRLF (or trailer headers + final CRLF)
					// For now, just read until we find the final CRLF
					while (true) {
						ch = in.read();
						if (ch == -1) break;
						if (ch == '\r') {
							int nextCh = in.read();
							if (nextCh == '\n') {
								break;
							}
						}
					}
					break;
				}

				// Read chunk data
				byte[] chunkBuffer = new byte[chunkSize];
				int bytesRead = 0;
				while (bytesRead < chunkSize) {
					int result = in.read(chunkBuffer, bytesRead, chunkSize - bytesRead);
					if (result == -1) {
						throw new IOException("Unexpected end of stream while reading chunk data");
					}
					bytesRead += result;
				}

				// Write chunk data to output
				bodyData.write(chunkBuffer, 0, chunkSize);

				// Read trailing CRLF after chunk data
				int cr = in.read();
				int lf = in.read();
				if (cr != '\r' || lf != '\n') {
					throw new IOException("Expected CRLF after chunk data");
				}
			}
			if(gzip_flag)
				return gzipDecompress(bodyData.toByteArray());
			else
				return bodyData.toByteArray();
		}
		else {
			byte[] bodyData = new byte[contentLength];
			int bytesRead = 0;
			while (bytesRead < contentLength) {
				int result = in.read(bodyData, bytesRead, contentLength - bytesRead);
				if (result == -1) {
					System.out.println("Unexpected end of stream while reading body data");
					break;
				}
				bytesRead += result;
			}
			//System.out.println("l=" + contentLength + "r=" + bytesRead + "bodyData = " );
			//for(int i = 0; i < bodyData.length; i++) {
			//	System.out.println((char)bodyData[i] + " " + bodyData[i]);
			//}
			//System.out.println();
			if(gzip_flag)
				return gzipDecompress(bodyData);
			else
				return bodyData;
		}
	}
	
	private static byte[] gzipDecompress(byte[] data) {
		if(data == null || data.length == 0)
			return data;
		try {
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(data));
			byte[] buffer = new byte[4096];
			int len;
			while ((len = gzipInputStream.read(buffer)) != -1) {
				byteArrayOutputStream.write(buffer, 0, len);
			}
			gzipInputStream.close();
			return byteArrayOutputStream.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private String readLineFromInputStream(InputStream inputStream) throws IOException {
		StringBuilder line = new StringBuilder();
		int ch;
		try {
			while ((ch = inputStream.read()) != -1) {
				if (ch == '\r') {
					// Перевіряємо наступний символ
					int nextCh = inputStream.read();
					if (nextCh == '\n') {
						// Знайшли \r\n, повертаємо рядок
						return line.toString();
					} else {
						// Якщо наступний символ не \n, повертаємо його назад і продовжуємо
						line.append((char) ch);
						if (nextCh != -1) {
							line.append((char) nextCh);
						}
					}
				} else {
					line.append((char) ch);
				}
			}
		} catch (java.net.SocketTimeoutException e) {
			//System.err.println("Socket timeout while reading line from input stream: " + e.getMessage());
			//throw new IOException("Socket timeout while reading HTTP request line", e);
			return null;
		}
		// Якщо досягнуто кінець потоку і є дані, повертаємо їх
		return line.length() > 0 ? line.toString() : null;
	}

	private boolean isInSubnet() {
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
			return true;

		} catch (UnknownHostException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	public HTTPRequest(InputStream inputStream, int port, InetAddress clientAddress) {
		this.clientAddress = clientAddress;
		this.inputStream = inputStream;
//		if(clientAddress == )
		this.port = port;
		this.portTrue = port;
		ban = false;
		close_connect_flag = true;
		path = "";
		Content_Type = "";
		body = "";
		bodyData = null;
		XwwwFormUrlEncodedString = "";
		X_Session_ID = 0;
		contentLength = 0;
		chunked_flag = false;
		gzip_flag = false;
		revers = ReversType.NO_REVERSE;
		userID = 0; // Початково користувач не автентифікований
		// Removed backdoor initialization
		old_servak_flag = false;
		try {
			// BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
			String line;
			StringBuilder requestBuilder = new StringBuilder();
			queryArr = new ArrayList<>();
			phpQueryArr = new ArrayList<>();
			headerArr = new ArrayList<>();
			cookieArr = new ArrayList<>();
			line = readLineFromInputStream(inputStream);
			
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
			while (!(line = readLineFromInputStream(inputStream)).isBlank()) {
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
			if(protocol.compareTo("HTTP/1.1") == 0) {
				if(host.length() == 0) {
					ban = true;
					return;
				}
				if(!checkHost(host)) {
					ban = true;
					return;
				}
			}
			else if(protocol.compareTo("HTTP/1.0") != 0) {
				ban = true;
				return;
			}
				
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
			
			String transferEncoding = getZnach("Transfer-Encoding", arrType.HEADER);
			if (transferEncoding != null && transferEncoding.toLowerCase().contains("chunked")) {
				chunked_flag = true;
			}
			
			String contentEncoding = getZnach("Content-Encoding", arrType.HEADER);
			if (contentEncoding != null && contentEncoding.toLowerCase().contains("gzip")) {
				gzip_flag = true;
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


			if((method.compareTo("PUT") == 0 || method.compareTo("POST") == 0 || method.compareTo("DELETE") == 0) && (contentLength != 0 || chunked_flag)) {
				
				bodyData = readBody(inputStream, contentLength, chunked_flag, gzip_flag);
				if(bodyData.length == 0) {
					ban = true;
					return;
				}
				try {
					char[] bodyChArr = new char[bodyData.length];
					for(int ii = 0; ii < bodyData.length; ii++) {
						bodyChArr[ii] = (char)bodyData[ii];
					}
					body = new String(bodyChArr);
				} catch (Exception e) {
					body = "hexData";
				}
				contentLength = bodyData.length;
				if (header.contains("Content-Length:")) {
					header = header.replaceFirst("Content-Length: .*\\r?\\n", "Content-Length: " + contentLength + "\r\n");
				} else {
					header = header.replaceFirst("\\r\\n", "\r\nContent-Length: " + contentLength + "\r\n");
				}
				header = header.replaceAll("Content-Encoding: .*\\r?\\n", "");
				header = header.replaceAll("Transfer-Encoding: chunked\\r?\\n", "");
				
				if(Content_Type.startsWith("application/x-www-form-urlencoded") && revers == ReversType.NO_REVERSE) {
					XwwwFormUrlEncodedString = body;
					for(String tmp2 : body.split("&")) {
						String param = convertString(tmp2.split("=")[0]);
						String value = convertString(tmp2.split("=").length == 2 ? tmp2.split("=")[1] : "");
						queryArr.add(new Query(param, value));
					}
				}
				else if(Content_Type.compareTo("application/octet-stream") == 0 && revers == ReversType.NO_REVERSE && path.startsWith(Configs.getParam("avr_path")) && user_agent.startsWith(Configs.getParam("avr_user_agent"))) {
					contentLength = bodyData.length;
					char[] bodyChArr = new char[contentLength];
					for(int ii = 0; ii < contentLength; ii++) {
						bodyChArr[ii] = (char)bodyData[ii];
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
				}
			}
		} catch (IOException e) {
			System.err.println("Error reading request body: " + e.getMessage());
			//e.printStackTrace();
			ban = true;
			return;
		}
		
		// Перевірка ключа та встановлення userID
		if (X_Session_ID != 0 && userID == 0) {
			userID = KeyManager.checkKey(X_Session_ID, clientAddress);
			if(userID != 0) {
				sn_megaList = KeyManager.getSnMegaList(X_Session_ID, clientAddress);
			}
		}

		if(revers == ReversType.PHP_FPM && phpFirewall(path, queryArr.size()) == false) {
			ban = true;
		}
		
		//prnt();
		//System.out.println("\n***\n***\n" + body);

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
