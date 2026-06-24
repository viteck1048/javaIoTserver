import java.io.IOException;

/**
 * Клас для обробки реверс-проксі запитів
 */
public final class ReverseProxy {

	private ReverseProxy() {
		throw new UnsupportedOperationException("Utility class");
	}
	
	/**
	 * Обробляє запити з реверсом
	 *
	 * @param httpRequest Запит з реверсом
	 * @return HTTPResponse з результатом обробки
	 */
	public static HTTPResponse handleReverseRequest(HTTPRequest httpRequest) {
		if (httpRequest.revers == HTTPRequest.ReversType.NO_REVERSE) {
			System.out.println("ReversProxy. err: NO_REVERSE");
			for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
				System.err.println(ste);
			}
			return new HTTPResponse(500);
		}

		if (httpRequest.userID == 0 && (httpRequest.revers != HTTPRequest.ReversType.BANRESPONSE && httpRequest.revers != HTTPRequest.ReversType.UNI_PRXY)) {
			System.err.println("ReversProxy. err: userID == 0; !banresponse");
			for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
				System.err.println(ste);
			}
			return new HTTPResponse(500);
		}

		try {
			switch (httpRequest.revers) {
				case BANRESPONSE:
					if (Configs.getBoolean("FirewallRun")) {
						FirewallIP.statisticCollection(httpRequest);
					}
					if (Configs.getBoolean("ban_response")) {
						return BanResponseHandler.banResponse(httpRequest);
					}
					break;
				case PHP_FPM:
					if (Configs.getBoolean("php_fpm")) {
						return PhpFpmHandler.phpFpmResend(httpRequest);
					}
					break;
				case OLD_SERVAK:
					if (Configs.getBoolean("liraCalc")) {
						return OldServakHandler.oldServakResend(httpRequest);
					}
					break;
				case RELAYS_SERVER:
					if (Configs.getBoolean("esp")) {
						return RelaysServerHandler.relaysServerResend(httpRequest);
					}
					break;
				case AI_CHAT:
					if (Configs.getBoolean("ai_assist")) {
						return AiChatHandler.aiChatResend(httpRequest);
					}
					break;
				case UNI_PRXY:
					return UniProxyHendler.uniPrxyResend(httpRequest);
					//break;
				default:
					throw new IOException("Unknown reverse type: " + httpRequest.revers);
			}
			throw new IOException("Reverse type " + httpRequest.revers + " is disabled in the configuration");
		} catch (Exception e) {
			System.out.println("Error in reverse proxy: " + e.getMessage());
			e.printStackTrace();
			return new HTTPResponse(503);
		}
	}

}
