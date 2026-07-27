import java.io.IOException;

/**
 * Обробник для BANRESPONSE типу реверсу
 */
public final class BanResponseHandler {
	
	private BanResponseHandler() {
		throw new UnsupportedOperationException("Utility class");
	}
	
	public static HTTPResponse banResponse(HTTPRequest httpRequest) {
			
		// Тіло забаненого запиту далі не їде: правимо map заголовків, а не готовий рядок
		if(!Configs.getBoolean("ban_response")) {
			return new HTTPResponse(403);
		}
		
		httpRequest.body = new byte[0];
		httpRequest.headers.put("content-length", "0");

		String host = Configs.getParam("ip_ban_response_server");
		int port = Configs.getInt("port_ban_response_server");

		NetworkClient nc;
		try {
			nc = new NetworkClient(host, port, false);
		} catch (IOException e) {
			System.out.println("BanResponseHandler error: " + e.getMessage());
			e.printStackTrace();
			return new HTTPResponse(503);
		}

		byte[] buf2 = nc.sendAndReceive(httpRequest.getHeaders().getBytes(), null);
		nc.close();
		
		return new HTTPResponse(null, buf2, "revers to ban response server");
	}
}
