//import java.nio.charset.StandardCharsets;  // Для отримання байтів у стандартній кодуванні
import java.util.Date;
import java.util.Arrays;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.*;
import java.util.zip.*;

public class HTTPResponse {
	
	private String headers;
	private byte[] body;
	private String msg;
	private boolean fl_err_prnt_hdr;
	public boolean close_connect_flag;
	private File file;
	public boolean streamResponse;
	private long streamResponseLength;
	private boolean head;
	/** Межі фактичної віддачі для streamResponse - завжди заповнені в normalizeHeaders(): [0; length-1] без Range, звужені - з ним */
	private long byteBegin;
	private long byteEnd;
	
	public HTTPResponse(String headers, byte[] body) {
		this.headers = headers;
		this.body = body;
		this.msg = null;
		fl_err_prnt_hdr = false;
		close_connect_flag = true;
	}
	
	public HTTPResponse(String headers, byte[] body, String msg) {
		this.headers = headers;
		this.body = body;
		this.msg = msg;
		fl_err_prnt_hdr = false;
		close_connect_flag = true;
	}
	
	public HTTPResponse(int length, byte[] body, String nameFile) {
		this(length, body, nameFile, false);
	}

	public HTTPResponse(File file, String nameFile, long length, boolean head) {
		// Виклик this(...) мусить бути першим оператором конструктора - решту
		// стрім-специфічних полів ставимо вже після нього.
		this(length, null, nameFile, head);
		this.file = file;
		this.streamResponse = true;
		this.streamResponseLength = length;
		this.head = head;
		// Content-Length уже виставлений делегованим конструктором коректно
		// (з переданого length, а не з body.length) - справжній розмір файлу
		// відомий наперед, тож chunked-framing не потрібен: "Content-Encoding:
		// chunked" - взагалі не існуюче значення (це Transfer-Encoding, не
		// Content-Encoding), і одночасно з Content-Length його й не можна
		// слати. Звичайний Content-Length + кілька write() замість одного -
		// саме стільки й треба.
	}

	public void streamResponseTo(OutputStream out) throws IOException {
		if (head) {
			return;
		}
		try (FileInputStream fis = new FileInputStream(file)) {
			fis.getChannel().position(byteBegin);
			long remaining = byteEnd - byteBegin + 1;
			byte[] buffer = new byte[8192];
			while (remaining > 0) {
				int toRead = (int) Math.min(buffer.length, remaining);
				int bytesRead = fis.read(buffer, 0, toRead);
				if (bytesRead == -1) {
					// Межі вважались коректними на момент normalizeHeaders() -
					// якщо файл усе ж скоротився між тим і цим моментом,
					// це не "кінець потоку", а розбіжність, яку далі
					// замовчувати не можна.
					throw new IOException("streamResponseTo: unexpected EOF in " + file + ", " + remaining + " byte(s) still expected");
				}
				out.write(buffer, 0, bytesRead);
				remaining -= bytesRead;
			}
		}
	}
	
	public HTTPResponse(long length, byte[] body, String nameFile, boolean head) {
		String[] tmp = nameFile.split("\\.");
		String typeFile;
		if(tmp.length >= 2) {
			typeFile = tmp[tmp.length - 1].toLowerCase();
			
			if(typeFile.compareTo("txt") == 0) {
				typeFile = "text/plain; charset=UTF-8";
			}
			else if(typeFile.compareTo("html") == 0) {
				typeFile = "text/html; charset=UTF-8";
			}
			else if(typeFile.compareTo("css") == 0) {
				typeFile = "text/css; charset=UTF-8";
			}
			else if(typeFile.compareTo("js") == 0) {
				typeFile = "application/javascript";
			}
			else if(typeFile.compareTo("csv") == 0) {
				typeFile = "text/csv; charset=UTF-8";
			}
			else if(typeFile.compareTo("xml") == 0) {
				typeFile = "text/xml; charset=UTF-8";
			}
			else if(typeFile.compareTo("app-json") == 0) {
				typeFile = "application/json";
			}
			else if(typeFile.compareTo("app-xml") == 0) {
				typeFile = "application/xml";
			}
			else if(typeFile.compareTo("app-urlencoded") == 0) {
				typeFile = "application/x-www-form-urlencoded";
			}
			else if(typeFile.compareTo("app-hexstream") == 0) {
				typeFile = "application/octet-stream";
			}
			else if(typeFile.compareTo("pdf") == 0) {
				typeFile = "application/pdf";
			}
			else if(typeFile.compareTo("zip") == 0) {
				typeFile = "application/zip";
			}
			else if(typeFile.compareTo("gz") == 0) {
				typeFile = "application/gzip";
			}
			else if(typeFile.compareTo("jpg") == 0 || typeFile.compareTo("jpeg") == 0) {
				typeFile = "image/jpeg";
			}
			else if(typeFile.compareTo("png") == 0) {
				typeFile = "image/png";
			}
			else if(typeFile.compareTo("gif") == 0) {
				typeFile = "image/gif";
			}
			else if(typeFile.compareTo("webp") == 0) {
				typeFile = "image/webp";
			}
			else if(typeFile.compareTo("svg") == 0) {
				typeFile = "image/svg+xml";
			}
			else if(typeFile.compareTo("mp3") == 0) {
				typeFile = "audio/mpeg";
			}
			else if(typeFile.compareTo("mp4") == 0) {
				typeFile = "video/mp4";
			}
			else if(typeFile.compareTo("ico") == 0) {
				typeFile = "image/x-icon";
			}
			else if(typeFile.compareTo("ttf") == 0) {
				typeFile = "font/ttf";
			}
			else if(typeFile.compareTo("woff") == 0) {
				typeFile = "font/woff";
			}
			else if(typeFile.compareTo("woff2") == 0) {
				typeFile = "font/woff2";
			}
			else if(nameFile.contains(Configs.getParam("acme_challenge_path"))) {
				typeFile = "text/plain";
			}
			else if(typeFile.compareTo("apk") == 0) {
				typeFile = "application/vnd.android.package-archive";
			}
			else if(typeFile.compareTo("exe") == 0) {
				typeFile = "application/octet-stream";
			}
			// Свідомо вимкнено, доки не знадобиться. Джерелмапи (.map, їх тягне Chrome
			// devtools за //# sourceMappingURL=) і сирі .json (не плутати з внутрішнім
			// "app-json") з vendor-дерева PMA зараз падають у 415 нижче:
			/*	else if(typeFile.compareTo("map") == 0) {
					typeFile = "application/json";
				}
				else if(typeFile.compareTo("json") == 0) {
					typeFile = "application/json";
				} */
		/*	else if(typeFile.compareTo("") == 0) {
				typeFile = "";
			} */
			else {
				this.headers = "HTTP/1.1 415 Unsupported Type\r\nServer: MijServak\r\nConnection: Closed\r\n\r\n";
				this.body = null;
				this.msg = "Response Err.415: " + nameFile;
				close_connect_flag = true;
				return;
			}
	 	}
		else {
			typeFile = "application/octet-stream";
		}
		
		fl_err_prnt_hdr = false;
		this.headers = "HTTP/1.1 200 OK\r\nServer: MijServak\r\nContent-Length: " + length + "\r\nContent-Type: " + typeFile + "\r\nConnection: Closed\r\n\r\n";
		if(head == false)
			this.body = body;
		else
			this.body = null;
		this.msg = "200 OK\t\tResponse " + (head == true ? "header " : "file ") + nameFile;
		close_connect_flag = false;
	}
	
	public HTTPResponse(int codeErr) {
		this(codeErr, null);
	}
	
	public HTTPResponse(int codeErr, HTTPRequest httpRequest) {
		if(codeErr == 200 || codeErr == 0) {
			fl_err_prnt_hdr = false;
		}
		else {
			fl_err_prnt_hdr = true;
		}
		this.body = null;
		close_connect_flag = false;
		switch(codeErr) {
			case 0:
				this.headers = null;
				this.body = null;
				this.msg = "Chunked response end";
				this.close_connect_flag = true;
				break;
			case 200:
				this.headers = "HTTP/1.1 " + codeErr + " OK\r\nServer: MijServak\r\nConnection: Closed\r\n\r\n";
				this.msg = "Response OK." + codeErr;
				this.close_connect_flag = true;
				break;
			case 400:
				this.headers = "HTTP/1.1 " + codeErr + " Bad Request\r\nServer: MijServak\r\nConnection: Closed\r\n\r\n";
				this.msg = "Response Err." + codeErr;
				break;
			case 401:
				this.headers = "HTTP/1.1 " + codeErr + " Unauthorized\r\nServer: MijServak\r\nConnection: Closed\r\n\r\n";
				this.msg = "Response Err." + codeErr;
				break;
			case 403:
				this.headers = "HTTP/1.1 " + codeErr + " Forbidden\r\nServer: MijServak\r\nConnection: Closed\r\n\r\n";
				this.msg = "Response Err." + codeErr;
				break;
			case 404:
				this.headers = "HTTP/1.1 " + codeErr + " Not Found\r\nServer: MijServak\r\nConnection: Closed\r\n\r\n";
				this.msg = "Response Err." + codeErr + (httpRequest != null ? httpRequest.path : " ");
				break;
			case 405:
				this.headers = "HTTP/1.1 " + codeErr + " Method Not Allowed\r\nAllow: GET, HEAD, POST, PUT, DELETE\r\nServer: MijServak\r\nConnection: Closed\r\n\r\n";
				this.msg = "Response Err." + codeErr;
				break;
			case 501:
				this.headers = "HTTP/1.1 " + codeErr + " Not Implemented\r\nServer: MijServak\r\nConnection: Closed\r\n\r\n";
				this.msg = "Response Err." + codeErr;
				break;
			case 503:
				this.headers = "HTTP/1.1 " + codeErr + " Service Unavailable\r\nServer: MijServak\r\nConnection: Closed\r\n\r\n";
				this.msg = "Response Err." + codeErr;
				break;
			case 504:
				this.headers = "HTTP/1.1 " + codeErr + " Gateway Timeout\r\nServer: MijServak\r\nConnection: Closed\r\n\r\n";
				this.msg = "Response Err." + codeErr;
				break;
			case 505:
				this.headers = "HTTP/1.1 " + codeErr + " HTTP Version Not Supported\r\nServer: MijServak\r\nConnection: Closed\r\n\r\n";
				this.msg = "Response Err." + codeErr;
				break;
			case 415:
				this.headers = "HTTP/1.1 " + codeErr + " Unsupported Type\r\nServer: MijServak\r\nConnection: Closed\r\n\r\n";
				this.msg = "Response Err." + codeErr;
				break;
			default:
				this.headers = "HTTP/1.1 " + "500 Internal Server Error\r\nServer: MijServak\r\nConnection: Closed\r\n\r\n";
				this.msg = "Response Err." + codeErr;
		}
	}
	
	public byte[] getHeaders() {
		if(headers != null) {
			return headers.getBytes();
		}
		return null;
	}
	
	public byte[] getBody() {
		return body;
	}
	
	public void set_fl_err_prnt_hdr(boolean fl_err_prnt_hdr) {
		this.fl_err_prnt_hdr = fl_err_prnt_hdr;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}

	public void addMsg(String msg) {
		this.msg += "\n                                                                                        " + msg;
	}

	private static byte[] gzipCompress(byte[] data) {
		if(data == null || data.length == 0)
			return data;
		try {
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream);
			gzipOutputStream.write(data);
			gzipOutputStream.close();
			return byteArrayOutputStream.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
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
	
	public void prntMsg(HTTPRequest httpRequest) {
		int userID = 0;
		if(httpRequest.port == Configs.getInt("avr_port"))
			userID = -1;
		else
			userID = httpRequest.userID;
		if(httpRequest.port == Configs.getInt("avr_port")) {
			if(Configs.getBoolean("avr_log"))
				System.out.println("\r\t\tNew client " + httpRequest.clientAddress + " on port " + String.format("%5d;", httpRequest.port) + (userID == -1 ? "\t\t\t\t\t" : ("\tuserID: " + userID + "\t\t")) + msg);
			else{
				// httpRequest.body тут - сирі байти дроту (ще НЕ декодовані base16/base64),
				// а 40/320 - це довжина вже декодованого пакета (DBClass.handleRequest).
				// Порівнювати треба з довжиною дроту для тієї самої обгортки: base64
				// (application/octet-stream) - ceil(N/3)*4+1, hex ("+"-розділювач) - 3*N-1.
				int bodyLength = httpRequest.body == null ? 0 : httpRequest.body.length;
				String contentType = httpRequest.getZnach("content-type", HTTPRequest.arrType.HEADER);
				boolean base64Wire = contentType != null && contentType.equals("application/octet-stream");
				int wire320 = base64Wire ? ((320 + 2) / 3) * 4 + 1 : 3 * 320 - 1;
				int wire40 = base64Wire ? ((40 + 2) / 3) * 4 + 1 : 3 * 40 - 1;
				if(bodyLength == wire320)
					System.out.print(".");
				else if(bodyLength == wire40)
					System.out.print("i");
				else {
					//httpRequest.prnt();
					System.out.println("\r\tBAN AVR " + httpRequest.clientAddress + ":" + String.format("%d;  ", httpRequest.port) + httpRequest.getHeaders().split("\r\n")[0] + " -> " + headers.split("\r\n")[0]);
				}
			}
		}
		else if(httpRequest.revers != HTTPRequest.ReversType.NO_REVERSE) {
			if(httpRequest.revers == HTTPRequest.ReversType.BANRESPONSE) {
				if(Configs.getBoolean("revers_log")){
					System.out.print("Request:");
					httpRequest.prnt();
					System.out.println("Response:");
					System.out.println("##################################################################");
					System.out.print(headers);
					System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
				}
				else {
					System.out.println("\r\tBANRESPONSE " + httpRequest.clientAddress + ":" + String.format("%d;  ", httpRequest.port) + httpRequest.getHeaders().split("\r\n")[0] + " -> " + headers.split("\r\n")[0]);
				}
			}
			else if(Configs.getBoolean("revers_log"))
				System.out.println("\r\t\tNew client " + httpRequest.clientAddress + " on port " + String.format("%5d;", httpRequest.port) + (userID == -1 ? "\t\t\t\t\t" : ("\tuserID: " + userID + "\t\t")) + msg);
		}
		else
			System.out.println("\r\t\tNew client " + httpRequest.clientAddress + " on port " + String.format("%5d;", httpRequest.port) + (userID == -1 ? "\t\t\t\t\t" : ("\tuserID: " + userID + "\t\t")) + msg);
		
		if((fl_err_prnt_hdr == true && Configs.getBoolean("log_err_prnt_header")) || (Configs.getBoolean("log_banresp_prnt_header") && httpRequest.revers == HTTPRequest.ReversType.BANRESPONSE))
			httpRequest.prnt();
	}

	boolean isTextual(String contentType) {
		if (contentType == null) return false;

		contentType = contentType.toLowerCase();

		return contentType.startsWith("text/")
			|| contentType.contains("json")
			|| contentType.contains("xml")
			|| contentType.contains("javascript")
			|| contentType.contains("ecmascript")
			|| contentType.contains("xhtml")
			|| contentType.contains("svg")
			|| contentType.contains("csv");
	}

	public void normalizeHeaders(HTTPRequest httpRequest) {
		if (headers == null && body != null) {
			int headerEndIndex = -1;
			for(int i = 0; i < body.length - 3; i++) {
				if(body[i] == '\r' && body[i + 1] == '\n' && body[i + 2] == '\r' && body[i + 3] == '\n') {
					headerEndIndex = i + 4;
					break;
				}
			}
			
			if (headerEndIndex != -1) {
				headers = new String(Arrays.copyOfRange(body, 0, headerEndIndex));
				body = Arrays.copyOfRange(body, headerEndIndex, body.length);
			}
		}
		if (headers != null) {
			if(headers.startsWith("Status: ")) {			//відновлення гедера після php-fpm
				headers = headers.replaceFirst("Status:", httpRequest.protocol);
			}
			else if(!headers.startsWith("HTTP")) {
				headers = httpRequest.protocol + " 200 OK\r\n" + headers;
			}
			
			headers = headers.replaceAll("Connection: [^\r\n]*\r\n", "");
			
			String connectionHeader = httpRequest.getZnach("connection", HTTPRequest.arrType.HEADER);
			boolean isKeepAlive = httpRequest.protocol.equals("HTTP/1.0") ? false : true;
			if(connectionHeader != null) {
				isKeepAlive = "keep-alive".equalsIgnoreCase(connectionHeader);
			}
			if (isKeepAlive && !close_connect_flag) {
				headers = headers.replace("\r\n\r\n", "\r\nConnection: keep-alive\r\n\r\n");
				close_connect_flag = false;
			}
			else {
				headers = headers.replace("\r\n\r\n", "\r\nConnection: Closed\r\n\r\n");
				close_connect_flag = true;
			}
			
			DateTimeFormatter customFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
			Date date = new Date();
			Instant instant = date.toInstant();
			ZonedDateTime zonedDateTime = instant.atZone(ZoneOffset.UTC);
			headers = headers.replaceAll("Date: [^\r\n]*\r\n", "");
			headers = headers.replace("\r\n\r\n", "\r\nDate: " + zonedDateTime.format(customFormatter) + "\r\n\r\n");

			String timezone = httpRequest.getZnach("x-timezone", HTTPRequest.arrType.HEADER);
			if(timezone != null && !timezone.isEmpty()) {
				try {
					zonedDateTime = instant.atZone(ZoneId.of(timezone));
					long offset = zonedDateTime.getOffset().getTotalSeconds() - ZoneOffset.UTC.getTotalSeconds();
					headers = headers.replace("\r\n\r\n", "\r\nX-Timezone-Offset: " + offset + "\r\n\r\n");
					addMsg("Gadget's Timezone: \"" + timezone + "\", X-Timezone-Offset: " + offset + " seconds");
				}
				catch (IllegalArgumentException e) {
					System.out.println("Invalid timezone: " + timezone);
					headers = headers.replace("\r\n\r\n", "\r\nX-Timezone-Offset: 0\r\n\r\n");
					addMsg("Gadget's Timezone: \"" + timezone + "\" doesn't exist, X-Timezone-Offset: 0 seconds");
				}
				catch (Exception e) {
					System.out.println("Invalid timezone: " + timezone);
					headers = headers.replace("\r\n\r\n", "\r\nX-Timezone-Offset: 0\r\n\r\n");
					addMsg("Gadget's Timezone: \"" + timezone + "\" doesn't exist, X-Timezone-Offset: 0 seconds");
				}
			}
			
			if(headers.contains("Content-Encoding: gzip")) {
				body = gzipDecompress(body);
				headers = headers.replace("Content-Encoding: gzip\r\n", "");
			}
		
			if (httpRequest.revers == HTTPRequest.ReversType.BANRESPONSE && body != null) {
				String oldPort = Configs.getParam("port_ban_response_server").trim();
				String newPort = String.valueOf(httpRequest.port).trim();
				String oldHost = Configs.getParam("ip_ban_response_server").trim();
				String newHost = Configs.getParam("host").trim();
				body = new String(body).replace(oldPort, newPort).replace(oldHost, newHost).getBytes();
			}
			
	
			if(Configs.getBoolean("ai_assist") && httpRequest.userID != 0 && body != null) {
				if(headers.contains("Content-Type: text/html")) {
					boolean pathMatch = false;
					for(String p : Configs.getList("ai_assist_path_list")) {
						if(httpRequest.path.equals(p)) {
							pathMatch = true;
							break;
						}
					}
					
					if(pathMatch) {
						byte[] widget = FileCacheManager.getFile("res/chat_widget.html");
						if(widget != null) {
							String bodyStr = new String(body);
							int bodyTagIndex = bodyStr.indexOf("<body>");
							if(bodyTagIndex != -1) {
								String widgetStr = new String(widget);
								bodyStr = bodyStr.substring(0, bodyTagIndex + 6) + widgetStr + bodyStr.substring(bodyTagIndex + 6);
								body = bodyStr.getBytes();
							}
						}
					}
				}
			}
			
			if(headers.contains("Content-Type: ")) {
				String contentType = headers.split("Content-Type: ")[1].split("\r\n")[0];
				if(isTextual(contentType)) {
					String acceptEncoding = httpRequest.getZnach("accept-encoding", HTTPRequest.arrType.HEADER);
					if(acceptEncoding != null && acceptEncoding.contains("gzip")) {
						body = gzipCompress(body);
						if(body != null && body.length > 0)
							headers = headers.replace("\r\n\r\n", "\r\nContent-Encoding: gzip\r\n\r\n");
					}
				}
			}
			long contentLength = 0;
			if (streamResponse) {
				// За замовчуванням - весь файл як один "діапазон" [0; length-1].
				// Range-заголовок (якщо є) звужує ці межі й перемикає статус на
				// 206/416 - в обох випадках streamResponseTo() далі просто читає
				// byteBegin..byteEnd, без окремої гілки "весь файл чи шматок".
				byteBegin = 0;
				byteEnd = streamResponseLength - 1;
				// msg тут нижче ще підміниться на 206/416, якщо Range це
				// виправдає - інакше лишається як маркер "це був стрім
				// цілого файлу", щоб відрізняти в логах від кешованої
				// віддачі (той самий формат "200 OK\t\tResponse file X").
				String kind = head ? "header " : "file ";
				msg = "200 OK\t\tResponse " + kind + httpRequest.path + " (stream, " + streamResponseLength + " bytes)";

				String rangeHeader = httpRequest.getZnach("range", HTTPRequest.arrType.HEADER);
				if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
					String spec = rangeHeader.substring("bytes=".length());
					int dash = spec.indexOf('-');
					long requestedBegin = -1;
					long requestedEnd = -1;
					if (dash >= 0) {
						String startStr = spec.substring(0, dash);
						String endStr = spec.substring(dash + 1);
						try {
							if (startStr.isEmpty()) {
								// bytes=-500 - суфікс: останні 500 байт файлу
								long suffixLength = Long.parseLong(endStr);
								requestedBegin = streamResponseLength - suffixLength;
								requestedEnd = streamResponseLength - 1;
							}
							else {
								requestedBegin = Long.parseLong(startStr);
								// bytes=500- - кінець опущений: до кінця файлу
								requestedEnd = endStr.isEmpty() ? streamResponseLength - 1 : Long.parseLong(endStr);
							}
						}
						catch (NumberFormatException e) {
							requestedBegin = -1;  // впаде в 416 нижче
						}
					}

					if (requestedBegin < 0 || requestedEnd < requestedBegin || requestedEnd >= streamResponseLength) {
						// Незадовольний діапазон - 416, і однозначно НЕ віддаємо
						// тіло: знімаємо streamResponse, щоб ClientHandler навіть
						// не спробував його стрімити.
						streamResponse = false;
						fl_err_prnt_hdr = true;  // помилка, не штатний 200 - як і решта кодів помилок у файлі
						headers = headers.replaceFirst("HTTP/1\\.1 200 OK", "HTTP/1.1 416 Range Not Satisfiable");
						headers = headers.replace("\r\n\r\n", "\r\nContent-Range: bytes */" + streamResponseLength + "\r\n\r\n");
						msg = "416 Range Not Satisfiable\t\tResponse " + kind + httpRequest.path
							+ " (rejected Range: " + rangeHeader + ", size " + streamResponseLength + ")";
					}
					else {
						byteBegin = requestedBegin;
						byteEnd = requestedEnd;
						headers = headers.replaceFirst("HTTP/1\\.1 200 OK", "HTTP/1.1 206 Partial Content");
						headers = headers.replace("\r\n\r\n", "\r\nContent-Range: bytes " + byteBegin + "-" + byteEnd + "/" + streamResponseLength + "\r\n\r\n");
						msg = "206 Partial\t\tResponse " + kind + httpRequest.path
							+ " [" + byteBegin + "-" + byteEnd + "/" + streamResponseLength + "]";
					}
				}

				if (streamResponse) {
					headers = headers.replace("\r\n\r\n", "\r\nAccept-Ranges: bytes\r\n\r\n");
					contentLength = byteEnd - byteBegin + 1;
				}
				// 416: contentLength лишається 0 - тіла нема.
			}
			else if(body != null && body.length > 0) {
				contentLength = body.length;
			}
			String contentLengthPattern = "Content-Length: \\d+\r\n";
			Pattern pattern = Pattern.compile(contentLengthPattern);
			Matcher matcher = pattern.matcher(headers);
			Pattern chankPattern = Pattern.compile("chunked");
			Matcher chunkMatcher = chankPattern.matcher(headers);
			if(matcher.find()) {
				String newContentLength = "Content-Length: " + contentLength + "\r\n";
				headers = matcher.replaceFirst(newContentLength);
			}
			else if(!(chunkMatcher.find())){
				headers = headers.replace("\r\n\r\n", "\r\nContent-Length: " + contentLength + "\r\n\r\n");
			}
			
			if (!headers.contains("X-Content-Type-Options:")) {
				headers = headers.replace("\r\n\r\n", "\r\nX-Content-Type-Options: nosniff\r\n\r\n");
			}
			if (!headers.contains("Cache-Control:")) {
				headers = headers.replace("\r\n\r\n", "\r\nCache-Control: no-cache\r\n\r\n");
			}
			if (!headers.contains("Server: MijServak")) {
				headers = headers.replace("\r\n\r\n", "\r\nServer: MijServak\r\n\r\n");
			}
			if (httpRequest.port == 443 && Configs.getBoolean("https_run")) {
				headers = headers.replace("\r\n\r\n", "\r\nStrict-Transport-Security: max-age=31536000; includeSubDomains\r\n\r\n");
			}
			/*// Security headers для всіх відповідей
			if (!headers.contains("X-Frame-Options:")) {
				headers = headers.replace("\r\n\r\n", "\r\nX-Frame-Options: DENY\r\n\r\n");
			}
			if (!headers.contains("X-XSS-Protection:")) {
				headers = headers.replace("\r\n\r\n", "\r\nX-XSS-Protection: 1; mode=block\r\n\r\n");
			}
			if (!headers.contains("Content-Security-Policy:")) {
				headers = headers.replace("\r\n\r\n", "\r\nContent-Security-Policy: default-src 'self'\r\n\r\n");
			}
			if (!headers.contains("Referrer-Policy:")) {
				headers = headers.replace("\r\n\r\n", "\r\nReferrer-Policy: strict-origin-when-cross-origin\r\n\r\n");
			}
			if (!headers.contains("Permissions-Policy:")) {
				headers = headers.replace("\r\n\r\n", "\r\nPermissions-Policy: geolocation=(), microphone=(), camera=()\r\n\r\n");
			}*/
			//System.out.println(headers);
		}
	}
	
}
