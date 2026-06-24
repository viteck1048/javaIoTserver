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
		int userID = httpRequest.userID;

		if (httpRequest.method.compareTo("GET") == 0) {
			// Додаємо userID до query string у першому рядку заголовку
			String[] headerLines = httpRequest.header.split("\r\n", 2);
			if (headerLines.length > 0) {
				String firstLine = headerLines[0];
				int firstSpace = firstLine.indexOf(' ');
				int secondSpace = firstLine.indexOf(' ', firstSpace + 1);
				if (firstSpace != -1 && secondSpace != -1) {
					String method = firstLine.substring(0, firstSpace + 1);
					String path   = firstLine.substring(firstSpace + 1, secondSpace);
					String rest   = firstLine.substring(secondSpace);
					path += (path.contains("?") ? "&" : "?") + "userID=" + userID;
					firstLine = method + path + rest;
				}
				httpRequest.header = firstLine + "\r\n" + (headerLines.length > 1 ? headerLines[1] : "");
			}
		} else if (httpRequest.Content_Type.compareTo("application/x-www-form-urlencoded") == 0
				&& (httpRequest.method.compareTo("POST") == 0
					|| httpRequest.method.compareTo("PUT") == 0
					|| httpRequest.method.compareTo("DELETE") == 0)) {
			// Додаємо userID до тіла запиту
			int index = httpRequest.body.indexOf("&");
			if (index != -1)
				httpRequest.body = httpRequest.body.substring(0, index) + "&userID=" + userID + httpRequest.body.substring(index);
			else
				httpRequest.body += "&userID=" + userID;
			httpRequest.bodyData = httpRequest.body.getBytes();
			httpRequest.contentLength = httpRequest.body.length();
			httpRequest.header = httpRequest.header.replaceFirst("Content-Length: \\d+", "Content-Length: " + httpRequest.contentLength);
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

		byte[] buf = nc.sendAndReceive(httpRequest.header.getBytes(), httpRequest.bodyData);
		nc.close();

		return new HTTPResponse(null, buf, "revers to machine time server");
	}
}
