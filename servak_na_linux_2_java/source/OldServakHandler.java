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

		boolean autorizUser = httpRequest.userID != 0 && httpRequest.isHttps;
		if (!autorizUser) {
			return new HTTPResponse(401);
		}

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

		// Префікс, за яким Router упізнав цей реверс, зрізаємо: бекенд знає лише свої шляхи.
		// path повертаємо на місце — по ньому потім друкуються логи запиту
		String requestPath = httpRequest.path;
		httpRequest.path = ReverseProxy.stripPrefix(requestPath, Configs.getParam("liraCalc_path"));
		byte[] head = httpRequest.getHeaders().getBytes();
		httpRequest.path = requestPath;

		byte[] buf2 = nc.sendAndReceive(head, httpRequest.body);
		nc.close();
		
		return new HTTPResponse(null, buf2, "revers to old server");
	}

}
