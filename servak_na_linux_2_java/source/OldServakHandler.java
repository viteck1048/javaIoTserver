import java.io.IOException;

/**
 * Обробник для OLD_SERVAK типу реверсу
 */
public final class OldServakHandler {
	
	private OldServakHandler() {
		throw new UnsupportedOperationException("Utility class");
	}
	
	// TODO: Додати статичні методи для проміжної обробки запитів і відповідей

	public static HTTPResponse oldServakResend(HTTPRequest httpRequest) {
		
		String host = Configs.getParam("ip_liraCalc_server");
		int port = Configs.getInt("port_liraCalc_server");

		NetworkClient nc;
		try {
			nc = new NetworkClient(host, port, false);
		} catch (IOException e) {
			System.out.println("OldServakHandler error: " + e.getMessage());
			e.printStackTrace();
			return new HTTPResponse(503);
		}

		//System.out.println("***\n" + httpRequest.header + "***\n" + httpRequest.body + "***");

		String send = httpRequest.header + "\r\n" + httpRequest.body;
		byte[] buf2 = nc.sendAndReceive(null, send.getBytes());
		nc.close();
		
		return new HTTPResponse(null, buf2, "revers to old server");	
	}

}
