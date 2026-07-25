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
		boolean autorizUser = httpRequest.userID != 0 && httpRequest.isHttps;
		if (!autorizUser) {
			return new HTTPResponse(401);
		}

		String espPath = Configs.getParam("esp_path");
		String contentType = httpRequest.getZnach("content-type", HTTPRequest.arrType.HEADER);
		int userID = httpRequest.userID;

		// Правимо поля запиту, а перший рядок і заголовки збере getHeaders() нижче
		if (httpRequest.method.compareTo("GET") == 0) {
			if(httpRequest.urlQueryString.isEmpty())
				httpRequest.urlQueryString = "userID=" + userID;
			else
				httpRequest.urlQueryString += "&userID=" + userID;
		}
		else if(contentType != null && contentType.startsWith("application/x-www-form-urlencoded")
				&& (httpRequest.method.compareTo("POST") == 0
					|| httpRequest.method.compareTo("PUT") == 0
					|| httpRequest.method.compareTo("DELETE") == 0)) {
			String form = httpRequest.body == null ? "" : new String(httpRequest.body);
			int index = form.indexOf("&");
			if(index != -1)
				form = form.substring(0, index) + "&userID=" + userID + form.substring(index);
			else
				form += "&userID=" + userID;
			httpRequest.body = form.getBytes();
			httpRequest.headers.put("content-length", String.valueOf(httpRequest.body.length));
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

		// Префікс, за яким Router упізнав цей реверс, бекенду не потрібен
		String requestPath = httpRequest.path;
		httpRequest.path = ReverseProxy.stripPrefix(requestPath, espPath);
		byte[] head = httpRequest.getHeaders().getBytes();
		httpRequest.path = requestPath;

		byte[] buf2 = nc.sendAndReceive(head, httpRequest.body);
		nc.close();
		
		return new HTTPResponse(null, buf2, "revers to old server");	
		}
}
