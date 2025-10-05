//import java.nio.charset.StandardCharsets;  // Для отримання байтів у стандартній кодуванні
import java.text.SimpleDateFormat;  
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


public class HTTPResponse {
	
	private String headers;
	private byte[] body;
	private String msg;
	private boolean fl_err_prnt_hdr;
	public boolean close_connect_flag;
	
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
	
	public HTTPResponse(int length, byte[] body, String nameFile, boolean head) {
		String[] tmp = nameFile.split("\\.");
		String typeFile = tmp[tmp.length - 1].toLowerCase();
		
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
	 	else if(typeFile.compareTo("ico") == 0) {
			typeFile = "image/x-icon";
		}
		else if(nameFile.contains(Configs.getParam("acme_challenge_path"))) {
			typeFile = "text/plain";
		}
	/*	else if(typeFile.compareTo("") == 0) {
			typeFile = "";
		} */
	 	else {
			this.headers = "HTTP/1.1 415 Unsupported Type\r\nServer: MijServak\r\nConnection: Closed\r\n\r\n";
			this.body = null;
			this.msg = "Response Err.415";
			close_connect_flag = true;
			return;
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
		fl_err_prnt_hdr = true;
		this.body = null;
		close_connect_flag = true;
		switch(codeErr) {
			case 200:
				this.headers = "HTTP/1.1 " + codeErr + " OK\r\nServer: MijServak\r\nConnection: Closed\r\n\r\n";
				this.msg = "Response OK." + codeErr;
				break;
			case 400:
				this.headers = "HTTP/1.1 " + codeErr + " Bad Request\r\nServer: MijServak\r\nConnection: Closed\r\n\r\n";
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

	public void prntMsg(HTTPRequest httpRequest) {
		SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");		//SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
		String formattedDate = formatter.format(new Date());
		int userID = 0;
		if(httpRequest.port == 8083)
			userID = -1;
		else
			userID = httpRequest.userID;
		if(httpRequest.port == 8083) {
			if(Configs.getBoolean("avr_log"))
				System.out.println("\r" + formattedDate + "\t\tNew client " + httpRequest.clientAddress + " on port " + String.format("%5d;", httpRequest.port) + (userID == -1 ? "\t\t\t\t\t" : ("\tuserID: " + userID + "\t\t")) + msg);
			else{
				if(httpRequest.contentLength == 320)
					System.out.print(".");
				else if(httpRequest.contentLength == 40)
					System.out.printf("%02X", httpRequest.bodyData[35]);
				else
					httpRequest.prnt();
			}
		}
		else if(httpRequest.revers != HTTPRequest.ReversType.NO_REVERSE) {
			if(httpRequest.revers == HTTPRequest.ReversType.BANRESPONSE) {
				System.out.print("Request:");
				httpRequest.prnt();
				System.out.println("Response. Headers:");
				System.out.println("##################################################################");
				System.out.print(headers);
				System.out.println("Response. Body:");
				if(body.length < 500)
					System.out.println(new String(body));
				else
					System.out.println("Body is too long");
				System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
			}
			if(Configs.getBoolean("revers_log"))
				System.out.println("\r" + formattedDate + "\t\tNew client " + httpRequest.clientAddress + " on port " + String.format("%5d;", httpRequest.port) + (userID == -1 ? "\t\t\t\t\t" : ("\tuserID: " + userID + "\t\t")) + msg);
		}
		else
			System.out.println("\r" + formattedDate + "\t\tNew client " + httpRequest.clientAddress + " on port " + String.format("%5d;", httpRequest.port) + (userID == -1 ? "\t\t\t\t\t" : ("\tuserID: " + userID + "\t\t")) + msg);
		
		if(fl_err_prnt_hdr == true)
			httpRequest.prnt();
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
		if (httpRequest.revers == HTTPRequest.ReversType.BANRESPONSE && body != null && body.length < 500) {
			String oldPort = "Port " + Configs.getParam("port_ban_response_server");
			String newPort = "Port " + httpRequest.portTrue;
			body = new String(body).replace(oldPort, newPort).getBytes();
		}
		if (headers != null) {
			if(headers.startsWith("Status: ")) {
				headers = headers.replaceFirst("Status:", httpRequest.protocol);
			}
			else if(!headers.startsWith("HTTP")) {
				headers = httpRequest.protocol + " 200 OK\r\n" + headers;
			}
			
			headers = headers.replaceAll("Connection: [^\r\n]*\r\n", "");
			
			if (!httpRequest.close_connect_flag && !close_connect_flag) {
				headers = headers.replace("\r\n\r\n", "\r\nConnection: keep-alive\r\n\r\n");
			}
			else {
				headers = headers.replace("\r\n\r\n", "\r\nConnection: Closed\r\n\r\n");
			}
			
			DateTimeFormatter customFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
			Date date = new Date();
			Instant instant = date.toInstant();
			ZonedDateTime zonedDateTime = instant.atZone(ZoneOffset.UTC);
			headers = headers.replaceAll("Date: [^\r\n]*\r\n", "");
			headers = headers.replace("\r\n\r\n", "\r\nDate: " + zonedDateTime.format(customFormatter) + "\r\n\r\n");

			if(httpRequest.header.contains("X-Timezone: ")) {
				String timezone = httpRequest.header.split("X-Timezone: ")[1].split("\r\n")[0];//"Europe/Kyiv";
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
			String contentLengthPattern = "Content-Length: \\d+\r\n";
			Pattern pattern = Pattern.compile(contentLengthPattern);
			Matcher matcher = pattern.matcher(headers);
			if(matcher.find()) {
				String newContentLength = "Content-Length: " + body.length + "\r\n";
				headers = matcher.replaceFirst(newContentLength);
			}
			else {
				if(body != null)
					headers = headers.replace("\r\n\r\n", "\r\nContent-Length: " + body.length + "\r\n\r\n");
				else
					headers = headers.replace("\r\n\r\n", "\r\nContent-Length: 0\r\n\r\n");
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
			if (httpRequest.portTrue == 443 && Configs.getBoolean("https_run")) {
				headers = headers.replace("\r\n\r\n", "\r\nStrict-Transport-Security: max-age=31536000; includeSubDomains\r\n\r\n");
			}
			// Security headers для всіх відповідей
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
			}
			//System.out.println(headers);
		}
	}
	
}
