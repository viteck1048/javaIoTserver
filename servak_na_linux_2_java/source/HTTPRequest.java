import java.net.InetAddress;                // Для роботи з IP-адресами
import java.net.URLDecoder;                 // Для декодування URL-даних
import java.net.UnknownHostException;       // Для обробки винятків, пов'язаних з IP-адресами
import java.util.ArrayList;                 // Для роботи з динамічними списками
import java.util.HashMap;
import java.util.List;
import java.util.Map;
// Для обробки виключень, пов'язаних з IO операціями
// Для отримання вхідного потоку
import java.io.*;
import java.util.zip.*;



public class HTTPRequest {
	public String method;
	public String protocol;
	public String path;
	public int port;
	public boolean isHttps;
	public Map<String, String> headers;
	public Map<String, String> cookies;
	public Map<String, String> wwwForm;
	public byte[] body;
	public boolean ban;
	public boolean quickBan;
	public InetAddress clientAddress;
	public long X_Session_ID;
	public ReversType revers;
	public String urlQueryString;
	public int userID; // ID користувача після автентифікації
	public enum ReversType {
		NO_REVERSE,
		OLD_SERVAK, // LiraCalc Configs Editor, localhost:8080, C++, WSL, FireBird DB
		RELAYS_SERVER, // ESP32 relays cloud server, localhost:8081, C++, WinAPI, SQLite DB
		PHP_FPM, // PHP-FPM, localhost:8082, PHP, Linux, SQLite DB
		BANRESPONSE,
		AI_CHAT, // AI assistant via OpenAI-compatible API
		UNI_PRXY,
		MACHINE_TIME
	}
	public enum arrType {
		QUERY,
		HEADER,
		COOKIE
	}
	public InputStream inStream;
	public BufferedOutputStream outStream;
	ArrayList<KeyManager.SnInfo> sn_megaList;
	//private static final int MAX_QUERY_PARAMS = 100;  // Обмеження кількості параметрів запиту
	private static final int MAX_HEADERS_COUNT = 50;   // Максимальна кількість заголовків
	private static final int MAX_HEADER_SIZE = 1024;  // 1KB - розумний ліміт для заголовка (одного)
	private static final int MAX_QUERY_PARAM_LENGTH = 1024;  // Максимальна довжина параметра
	private static final int MAX_BODY_LENGTH = 50 * 1024 * 1024;  // Максимальна довжина тіла запиту (50MB)
	private static final int MAX_PATH_LENGTH = 256;
	String errorMsg;
	
	public HTTPRequest(int port, InetAddress clientAddress, InputStream inStream, BufferedOutputStream outStream, boolean isHttps) {
		quickBan = false;
		this.clientAddress = clientAddress; // до isInSubnet() — воно читає це поле
		if(!isInSubnet()) {
			if(FirewallIP.checkBlackList(clientAddress)) {
				quickBan = true;
				return;
			}
		}
		this.port = port;
		this.inStream = inStream;
		this.outStream = outStream;
		this.isHttps = isHttps;
		headers = new HashMap<>();
		wwwForm = new HashMap<>();
		cookies = new HashMap<>();
		sn_megaList = new ArrayList<>();
		clean();
	}
	
	public void clean() {
		ban = false;
		quickBan = false;
		method = null;
		protocol = null;
		path = null;
		body = null;
		urlQueryString = "";
		X_Session_ID = 0;
		revers = ReversType.NO_REVERSE;
		userID = 0;
		headers.clear();
		cookies.clear();
		wwwForm.clear();
		sn_megaList.clear();
	}
	
	public boolean readHeaders() {
		String line;
		try{
			line = readLineFromInputStream(true);
			if(line == null || line.isEmpty() || line.length() < 3) {
				return false;
			}
			if(!firstLineHeaderCheck(line)) {
				quickBan = true;
				return false;
			}
			
			String[] parts = line.split(" +", 3);
			if(parts.length != 3) {
				quickBan = true;
				ban = true;
				return false;
			}
			
			protocol = parts[2].trim();
			if(!protocol.startsWith("HTTP")) {
				quickBan = true;
				ban = true;
				return false;
			}

			method = parts[0].trim();
			String tmp = parts[1].trim();
			int index = tmp.indexOf('?');
			if(index != -1) {
				path = convertString(tmp.substring(0, index).trim());
				urlQueryString = tmp.substring(index + 1).trim();
				if(urlQueryString.length() > MAX_QUERY_PARAM_LENGTH) {
					ban = true;
					errorMsg = "Query string too long: " + urlQueryString.length();
				}
			}
			else {
				path = convertString(tmp);
				urlQueryString = "";
			}
			
			if(!isValidHttpMethod(method)) {
				ban = true;
				errorMsg = "Invalid HTTP method: " + method;
			}
			if(path == null || path.contains("..") || path.contains("\\") || path.length() > MAX_PATH_LENGTH) {
				ban = true;
				quickBan = true;
				errorMsg = "Path too long or invalid: " + path;
			}
			int headerCount = 0;
			while((line = readLineFromInputStream(false)) != null && !line.isBlank()) {
				headerCount++;
				if(line.length() > MAX_HEADER_SIZE) {
					errorMsg = "Header too long: " + line.length();
					ban = true;
					return false;
				}
				int colonIndex = line.indexOf(':');
				if (colonIndex > 0) {
					String headerName = line.substring(0, colonIndex).trim();
					String headerValue = line.substring(colonIndex + 1).trim();
					headers.put(headerName.toLowerCase(), headerValue);
				}
				else {
					ban = true;
					headers.put("invalid-header", line);
					errorMsg = "Invalid header: " + line;
					//System.out.println("Invalid header: " + line);
				}
				if(headerCount > MAX_HEADERS_COUNT) {
					ban = true;
					errorMsg = "Too many headers: " + headerCount;
					//System.out.println("Too many headers: " + headerCount);
					return false;
				}
			}
			
			if(!protocol.equals("HTTP/1.0")) {
				if(!checkHost(headers.get("host"))) {
					ban = true;
					errorMsg = "Invalid host";
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			ban = true;
			return false;
		}
		return true;
	}

	public boolean readBody() throws IOException {
		String contentLengthStr = headers.get("content-length");
		int contentLength = 0;
		try {
			contentLength = Integer.parseInt(contentLengthStr);
			if (contentLength > MAX_BODY_LENGTH || contentLength < 0) {
				ban = true;
				return false;
			}
		} catch (NumberFormatException e) {
			contentLength = 0;
		}
		
		String encoding = headers.get("content-encoding");
		boolean gzip_flag = false;
		if(encoding != null && !encoding.isBlank()) {
			gzip_flag = encoding.contains("gzip");
		}

		String transferEncoding = headers.get("transfer-encoding");
		boolean chunked_flag = false;
		if(transferEncoding != null && !transferEncoding.isBlank()) {
			chunked_flag = transferEncoding.contains("chunked");
		}

		if(chunked_flag) {
			ByteArrayOutputStream bodyData = new ByteArrayOutputStream();

			while (true) {
				// Read chunk size line (hex number followed by CRLF)
				StringBuilder sizeLine = new StringBuilder();
				int ch;
				while ((ch = inStream.read()) != -1) {
					if (ch == '\n') {
						throw new IOException("Invalid chunk size line (found LF without preceding CR)");
					}
					if (ch == '\r') {
						// Check for LF
						int nextCh = inStream.read();
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
					if(ch >= 48 && ch <= 57 || ch >= 65 && ch <= 70 || ch >= 97 && ch <= 102 || ch == 59 || ch == 32) {
						sizeLine.append((char)(ch & 0xFF));
						if (sizeLine.length() > 64) {
							throw new IOException("Chunk size line too long");
						}
					}
					else {
						throw new IOException("Invalid chunk size line format, char isn't hex digit or space or semicolon");
					}
				}

				// Parse chunk size (ignore chunk extensions for now)
				String sizeStr = sizeLine.toString().trim().split(";", 2)[0];
				int chunkSize = 0;
				try {
					chunkSize = Integer.parseInt(sizeStr, 16);
				} catch (NumberFormatException e) {
					throw new IOException("Invalid chunk size: " + sizeStr);
				}

				// Check if this is the last chunk (size 0)
				if (chunkSize == 0) {
					// Read trailers (until blank line)
					StringBuilder trailerLine = new StringBuilder();
					boolean lastCR = false;

					while ((ch = inStream.read()) != -1) {
						if (ch == '\r') {
							lastCR = true;
							continue;
						}
						if (ch == '\n') {
							if (lastCR) {
								// End of line
								if (trailerLine.length() == 0)
									break; // empty line => end of trailers
								// можна логувати або ігнорувати трейлер:
								System.out.println("Trailer: " + trailerLine);
								trailerLine.setLength(0);
								lastCR = false;
								continue;
							}
						}
						// звичайний символ трейлера
						trailerLine.append((char) ch);
						lastCR = false;
					}
					break;
				}

				// Read chunk data
				byte[] chunkBuffer = new byte[chunkSize];
				int bytesRead = 0;
				while (bytesRead < chunkSize) {
					int result = inStream.read(chunkBuffer, bytesRead, chunkSize - bytesRead);
					if (result == -1) {
						throw new IOException("Unexpected end of stream while reading chunk data");
					}
					bytesRead += result;
				}

				// Write chunk data to output
				bodyData.write(chunkBuffer, 0, chunkSize);

				// Read trailing CRLF after chunk data
				int cr = inStream.read();
				int lf = inStream.read();
				if (cr != '\r' || lf != '\n') {
					throw new IOException("Expected CRLF after chunk data");
				}
			}
			if(gzip_flag) {
				body = gzipDecompress(bodyData.toByteArray());
				headers.remove("content-encoding");
			}
			else {
				body = bodyData.toByteArray();
			}
		}
		else if (path.compareTo("/upload") == 0) {
			byte[] bodyData = new byte[1024];
			int bytesRead = 0;
			while (bytesRead < contentLength) {
				int result = inStream.read(bodyData, 0, ((contentLength - bytesRead) > 1024) ? 1024 : (contentLength - bytesRead));
				if (result == -1) {
					break;
				}
				bytesRead += result;
			}
			body = bodyData;
		}
		else {
			byte[] bodyData = new byte[contentLength];
			int bytesRead = 0;
			while (bytesRead < contentLength) {
				int result = inStream.read(bodyData, bytesRead, contentLength - bytesRead);
				if (result == -1) {
					System.out.println("Unexpected end of stream while reading body data");
					break;
				}
				bytesRead += result;
			}
			if(gzip_flag) {
				headers.remove("content-encoding");
				body = gzipDecompress(bodyData);
			}
			else {
				body = bodyData;
			}
		}
		headers.put("content-length", String.valueOf(body.length));
		return true;
	}
	
	public boolean sessionDates() {
		String cookieStr = headers.get("cookie");
		
		if(cookieStr == null || cookieStr.isEmpty()) {
			return false;
		}

		if(!cookieStr.contains("X-Session-ID=")) {
			//ці кукі мені не цікаві
			return false;
		}

		parsCookieAndWwwForm(cookieStr, cookies, "; ");

		String xSessionIdStr = cookies.get("X-Session-ID");
		if(xSessionIdStr == null) {
			return false;
		}
		try {
			X_Session_ID = Long.parseLong(xSessionIdStr);
		} catch (NumberFormatException e) {
			return false;
		}

		// Перевірка ключа та встановлення userID
		if (X_Session_ID != 0) {
			userID = KeyManager.checkKey(X_Session_ID, clientAddress);
			if(userID != 0) {
				sn_megaList = KeyManager.getSnMegaList(X_Session_ID, clientAddress);
			}
		}

		return true;
	}

	public String getHeaders() {
		StringBuilder requestBuilder = new StringBuilder();
		requestBuilder.append(method).append(" ").append(path);
		if(!urlQueryString.isEmpty()) {
			requestBuilder.append("?").append(urlQueryString);
		}
		requestBuilder.append(" ").append(protocol).append("\r\n");

		for(Map.Entry<String, String> entry : headers.entrySet()) {
			requestBuilder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
		}
		return requestBuilder.toString();
	}


	private String convertString(String str) {
		try {
		String decoded = URLDecoder.decode(str, "UTF-8");
		for (char c : decoded.toCharArray()) {
			if (c >= 0x01 && c <= 0x1f) {
				ban = true;
				return null;
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
	
	private boolean checkHost(String host) {
		if(host == null || host.isBlank()) {
			return false;
		}
		int idx = 0, i = 0;
		boolean ipv6flag = false;
		for(; i < host.length(); i++) {
			char ch = host.charAt(i);
			if(!ipv6flag && ch == ':') {
				if(idx != 0) {
					idx = host.length();
					break;
				}
				idx = i;
			}
			else if(ch == '[') {
				ipv6flag = true;
			}
			else if(ch == ']') {
				ipv6flag = false;
			}
		}
		if(i == host.length()) {
			idx = host.length();
		}
		String host_;
		if(host.charAt(0) == '[') {
			host_ = host.substring(1, idx - 1);
		}
		else {
			host_ = host.substring(0, idx);
		}
		if(host_.isEmpty()) {
			return false;
		}
		if(host_.compareTo(Configs.getParam("host")) == 0)
			return true;
		if(host_.compareTo("localhost") == 0)
			return true;
		if(host_.compareTo("127.0.0.1") == 0)
			return true;
		if(host_.compareTo("::1") == 0)
			return true;
		if(Configs.getBoolean("lanSettings")) {
			if(Configs.getParam("localIP").compareTo(host_) == 0)
				return true;
		}
		return false;
	}
	
	private static byte[] gzipDecompress(byte[] data) {
		if(data == null || data.length == 0)
			return data;
		try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			 GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(data)))
			{byte[] buffer = new byte[4096];
			int len;
			while ((len = gzipInputStream.read(buffer)) != -1) {
				byteArrayOutputStream.write(buffer, 0, len);
			}
			return byteArrayOutputStream.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	private String readLineFromInputStream(boolean firstLine) throws IOException {
		StringBuilder line = new StringBuilder();
		int ch;
		boolean colonFound = false;
		try {
			while ((ch = inStream.read()) != -1) {
				ch &= 0xFF;
				if(!firstLine && !colonFound && ch == ':') {
					colonFound = true;
				}
				if (ch == '\r') {
					// Перевіряємо наступний символ
					int nextCh = inStream.read();
					if (nextCh == '\n') {
						// Знайшли \r\n, повертаємо рядок
						return line.toString();
					} else {
						throw new IOException("Invalid line format");
					}
				}
				else if (ch > 0x1f && ch != 0x7f) {
					if(!colonFound && ch >= 0x80) {
						ban = true;
						return null;
					}
					line.append((char)ch);
				} 
				else {
					ban = true;
					return null;
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

	public boolean isInSubnet() {
		if(!Configs.getBoolean("lanSettings"))
			return false;
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

	private boolean firstLineHeaderCheck(String line) {
		char ch;
		for (int i = 0; i < 3; i++) {
			ch = line.charAt(i);
			if(ch < 'A' || ch > 'Z') {
				return false;
			}
		}
		return true;
	}

	private boolean parsCookieAndWwwForm(String content, Map<String, String> mapArr, String delimiter) {
		String[] tmp = content.split(String.valueOf(delimiter));
		for(String item : tmp) {
			String[] tmp2 = item.split("=", 2);
			if(tmp2.length == 2) {
				mapArr.put(tmp2[0].trim(), tmp2[1].trim());
			}
		}
		return true;
	}


	public void prnt() {
		System.out.println("\n****************************** " + port + " *********************************");
		System.out.println(clientAddress.toString());
		System.out.println("************************************************************");
		System.out.println(getHeaders());
		System.out.println("************************************************************");
		System.out.println("reverse = " + revers);
		System.out.println("httpRequest.wwwForm.size() = " + wwwForm.size());
		
		for(Map.Entry<String, String> entry : wwwForm.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}

		System.out.println("=============================== " + X_Session_ID + " ===================================");
		if(errorMsg != null && !errorMsg.isEmpty()) {
			System.out.println("Error: " + errorMsg);
		}
		// Виводимо байти в hex форматі
		if(body != null && body.length > 0 && false) {
			for(int i = 0; i < body.length; i++) {
				System.out.printf("%c", (char)body[i]);
			}
			System.out.println("=============================== " + body.length + " ===================================");
		}

	}
	
	public boolean chkParam(String par, arrType type) {
		return getZnach(par, type) != null && !getZnach(par, type).isEmpty();
	}
	
	public boolean chkZnach(String par, String znch, arrType type) {
		return getZnach(par, type) != null && getZnach(par, type).equals(znch);
	}
	
	public String getZnach(String par, arrType type) {
		switch(type) {
			case QUERY:
				if(wwwForm.isEmpty()) {
					if(urlQueryString != null && !urlQueryString.isEmpty()) {
						parsCookieAndWwwForm(urlQueryString, wwwForm, "&");
					}
					else {
						String contentType = headers.get("content-type");
						if(contentType != null && contentType.contains("application/x-www-form-urlencoded")) {
							String bodyForm = new String(body);
							parsCookieAndWwwForm(bodyForm, wwwForm, "&");
						}
					}
				}
				if(wwwForm.isEmpty()) {
					wwwForm.put("empty", "empty");
				}
				return wwwForm.get(par);
			case HEADER:
				return headers.get(par);
			case COOKIE:
				if(cookies.isEmpty() && headers.get("cookie") != null) {
					parsCookieAndWwwForm(headers.get("cookie"), cookies, ";");
				}
				if(cookies.isEmpty()) {
					cookies.put("empty", "empty");
				}
				return cookies.get(par);
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

}
