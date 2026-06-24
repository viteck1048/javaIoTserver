import java.io.IOException;

/**
 * Обробник для RELAYS_SERVER типу реверсу
 */
public final class RelaysServerHandler {
	
	private RelaysServerHandler() {
		throw new UnsupportedOperationException("Utility class");
	}
	
	// TODO: Додати статичні методи для проміжної обробки запитів і відповідей

	public static HTTPResponse relaysServerResend(HTTPRequest httpRequest) {
		int userID = httpRequest.userID;
		if (httpRequest.path.startsWith("/relay_servak/") && httpRequest.method.compareTo("GET") == 0) {
		// Додаємо userID до першого рядка header
			String[] headerLines = httpRequest.header.split("\r\n", 2);
			if (headerLines.length > 0) {
				String firstLine = headerLines[0];
				int firstSpace = firstLine.indexOf(' ');
				int secondSpace = firstLine.indexOf(' ', firstSpace + 1);
				if (firstSpace != -1 && secondSpace != -1) {
					String method = firstLine.substring(0, firstSpace + 1);
					String path = firstLine.substring(firstSpace + 1, secondSpace);
					String rest = firstLine.substring(secondSpace);
					if (path.contains("?")) {
						path += "&userID=" + userID;
					} else {
						path += "?userID=" + userID;
					}
					firstLine = method + path + rest;
				}
				httpRequest.header = firstLine + "\r\n" + (headerLines.length > 1 ? headerLines[1] : "");
			}
		}
		else if(httpRequest.path.startsWith("/relay_servak") && httpRequest.Content_Type.compareTo("application/x-www-form-urlencoded") == 0 && (httpRequest.method.compareTo("POST") == 0 || httpRequest.method.compareTo("PUT") == 0 || httpRequest.method.compareTo("DELETE") == 0)) {
			int index = httpRequest.body.indexOf("&");
			if(index != -1)
				httpRequest.body = httpRequest.body.substring(0, index) + "&userID=" + userID + httpRequest.body.substring(index);
			else
				httpRequest.body += "&userID=" + userID;
			// Оновлюємо content-length
			httpRequest.bodyData = httpRequest.body.getBytes();
			httpRequest.contentLength = httpRequest.body.length();
			httpRequest.header = httpRequest.header.replaceFirst("Content-Length: \\d+", "Content-Length: " + httpRequest.contentLength);
		}

		String host = Configs.getParam("ip_relay_server");
		int port = Configs.getInt("port_relay_server");

		NetworkClient nc;
		try {
			nc = new NetworkClient(host, port, false);
		} catch (IOException e) {
			System.out.println("RelaysServerHandler error: " + e.getMessage());
			e.printStackTrace();
			return new HTTPResponse(503);
		}

		byte[] buf2 = nc.sendAndReceive(httpRequest.header.getBytes(), httpRequest.bodyData);
		nc.close();
		
		return new HTTPResponse(null, buf2, "revers to old server");	
		}
}
