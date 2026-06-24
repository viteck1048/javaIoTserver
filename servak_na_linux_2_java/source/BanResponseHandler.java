import java.io.IOException;

/**
 * Обробник для BANRESPONSE типу реверсу
 */
public final class BanResponseHandler {
	
	private BanResponseHandler() {
		throw new UnsupportedOperationException("Utility class");
	}
	
	public static HTTPResponse banResponse(HTTPRequest httpRequest) {
			
		httpRequest.header = httpRequest.header.replaceFirst("Content-Length: \\d+\r\n", "Content-Length: 0\r\n");
		httpRequest.body = "";
		httpRequest.bodyData = "".getBytes();

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

		byte[] buf2 = nc.sendAndReceive(httpRequest.header.getBytes(), null);
		nc.close();
		
		return new HTTPResponse(null, buf2, "revers to ban response server");
	}
}
