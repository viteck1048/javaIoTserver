import java.io.IOException;

/**
 * Обробник для MACHINE_TIME типу реверсу.
 * Форвардить запити на esp8266_decoder (mach_time_ip:mach_time_port).
 * Шлях не стрипається — цільовий сервер сам обробляє /MachineTime18Channels/.
 */
public final class MachineTimeProxyHandler {

	private MachineTimeProxyHandler() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static HTTPResponse machineTimeResend(HTTPRequest httpRequest) {
		boolean machineTimeRead = httpRequest.method.compareTo("GET") == 0 || httpRequest.method.compareTo("HEAD") == 0;
		if (!machineTimeRead) {
			boolean autorizUser = httpRequest.userID != 0 && httpRequest.isHttps;
			if (!autorizUser) {
				return new HTTPResponse(401);
			}
		}

		String contentType = httpRequest.getZnach("content-type", HTTPRequest.arrType.HEADER);
		int userID = httpRequest.userID;

		// Правимо поля запиту, а перший рядок і заголовки збере getHeaders() нижче
		if (httpRequest.method.compareTo("GET") == 0) {
			if(httpRequest.urlQueryString.isEmpty())
				httpRequest.urlQueryString = "userID=" + userID;
			else
				httpRequest.urlQueryString += "&userID=" + userID;
		} else if (contentType != null && contentType.startsWith("application/x-www-form-urlencoded")
				&& (httpRequest.method.compareTo("POST") == 0
					|| httpRequest.method.compareTo("PUT") == 0
					|| httpRequest.method.compareTo("DELETE") == 0)) {
			String form = httpRequest.body == null ? "" : new String(httpRequest.body);
			int index = form.indexOf("&");
			if (index != -1)
				form = form.substring(0, index) + "&userID=" + userID + form.substring(index);
			else
				form += "&userID=" + userID;
			httpRequest.body = form.getBytes();
			httpRequest.headers.put("content-length", String.valueOf(httpRequest.body.length));
		}

		String host = Configs.getParam("mach_time_ip");
		int port    = Configs.getInt("mach_time_port");

		NetworkClient nc;
		try {
			nc = new NetworkClient(host, port, false);
		} catch (IOException e) {
			System.out.println("MachineTimeProxyHandler error: " + e.getMessage());
			e.printStackTrace();
			return new HTTPResponse(503);
		}

		byte[] buf = nc.sendAndReceive(httpRequest.getHeaders().getBytes(), httpRequest.body);
		nc.close();

		return new HTTPResponse(null, buf, "revers to machine time server");
	}
}
