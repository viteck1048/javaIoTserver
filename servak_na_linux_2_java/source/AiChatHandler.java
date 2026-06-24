import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Обробник для AI_CHAT типу реверсу
 */
public final class AiChatHandler {
	
	private static final AtomicBoolean aiChatBusy = new AtomicBoolean(false);

	private AiChatHandler() {
		throw new UnsupportedOperationException("Utility class");
	}
	
	// TODO: Додати статичні методи для проміжної обробки запитів і відповідей


	public static HTTPResponse aiChatResend(HTTPRequest httpRequest) {
		if(!Configs.getBoolean("ai_assist_paralel_requests")) {
			if(!aiChatBusy.compareAndSet(false, true)) {
				return new HTTPResponse(503);
			}
		}
		
		//Socket socket = null;
		NetworkClient nc = null;
		try {
			String url = Configs.getParam("ai_assist_url");
			String[] hostPort = url.split(":");
			String host = hostPort[0];
			int port = Integer.parseInt(hostPort[1]);
			/*
			if(Configs.getBoolean("ai_assist_url_ssl")) {
				SSLContext sslContext = SSLContext.getDefault();
				SSLSocketFactory factory = sslContext.getSocketFactory();
				socket = factory.createSocket(host, port);
			}
			else {
				socket = new Socket(host, port);
			}
			*/
			nc = new NetworkClient(host, port, true);
			nc.setSoTimeout(300000);
			nc.setTcpNoDelay(true);
			
			String userMess = httpRequest.getZnach("user_mess", HTTPRequest.arrType.QUERY);
			String chatHistory = httpRequest.getZnach("chat_history", HTTPRequest.arrType.QUERY);
			String page = httpRequest.getZnach("page", HTTPRequest.arrType.QUERY);
			String userName = KeyManager.getUserName(httpRequest.userID);
			
			String systemPrompt;
			if(Configs.getDefine("ai_assist_prompt"))
				systemPrompt = Configs.getParam("ai_assist_prompt");
			else
				systemPrompt = "Ти — AI-помічник на сайті керування IoT-пристроями. Допомагаєш користувачам керувати реле, переглядати статуси пристроїв та налаштовувати систему.";
			
			String acceptLang = httpRequest.getZnach("Accept-Language", HTTPRequest.arrType.HEADER);
			if(acceptLang != null && !acceptLang.isEmpty()) {
				systemPrompt += "Я - " + userName + ". Відповідай мовою, вказаною в заголовку Accept-Language браузера клієнта: " + acceptLang;
			}
			
			String userContent = "<page>\n" + (page != null ? page : "") + "\n</page>\n\n<chat_history>\n" + (chatHistory != null ? chatHistory : "") + "\n</chat_history>\n\n<user_message>\n" + (userMess != null ? userMess : "") + "\n</user_message>";
			
			String jsonBody = "{\"model\":\"" + Configs.getParam("ai_assist_model") + "\",\"messages\":[{\"role\":\"system\",\"content\":\"" + escapeJson(systemPrompt) + "\"},{\"role\":\"user\",\"content\":\"" + escapeJson(userContent) + "\"}],\"stream\":false}";
			
			byte[] jsonBodyBytes = jsonBody.getBytes();
			
			String request = "POST /v1/chat/completions HTTP/1.1\r\n"
				+ "Host: " + Configs.getParam("ai_assist_url") + "\r\n";
			if(Configs.getDefine("ai_assist_autorization_header")) {
				request += Configs.getParam("ai_assist_autorization_header") + "\r\n";
			}
			else if(Configs.getDefine("ai_assist_token")) {
				request += "Authorization: Bearer " + Configs.getParam("ai_assist_token") + "\r\n";
			}
			request += "Content-Type: application/json\r\n"
				+ "Content-Length: " + jsonBodyBytes.length + "\r\n"
				+ "Connection: close\r\n"
				+ "\r\n";
			
			nc.sendChunk(request.getBytes());
			nc.sendChunk(jsonBodyBytes);
			nc.sendFlush();
			/*
			InputStream in = socket.getInputStream();
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int bytesRead;
			int headerEndIndex = -1;
			while((bytesRead = in.read(buffer)) != -1) {
				baos.write(buffer, 0, bytesRead);
				if(headerEndIndex == -1) {
					String raw = new String(baos.toByteArray());
					headerEndIndex = raw.indexOf("\r\n\r\n");
					if(headerEndIndex != -1) {
						headerEndIndex += 4;
					}
				}
			}
			
			byte[] fullResponse = baos.toByteArray();
			*/
			byte[] fullResponse = nc.recvAll();
			String raw = new String(fullResponse);
			String bodyStr = new String(fullResponse);
			String responseJson;
			int headerEndIndex = raw.indexOf("\r\n\r\n");
			if(headerEndIndex != -1 && headerEndIndex < bodyStr.length()) {
				responseJson = bodyStr.substring(headerEndIndex);
			}
			else {
				return new HTTPResponse(503);
			}
			
			String content = extractJsonContent(responseJson);
			byte[] contentBytes = content.getBytes();
			String respHeaders = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: " + contentBytes.length + "\r\nConnection: Closed\r\n\r\n";
			return new HTTPResponse(respHeaders, contentBytes, "AI chat response");
			
		} catch (Exception e) {
			System.out.println("AI Chat error: " + e.getMessage());
			e.printStackTrace();
			return new HTTPResponse(500);
		} finally {
			if(nc != null) {
				nc.close();
			}
			if(!Configs.getBoolean("ai_assist_paralel_requests")) {
				aiChatBusy.set(false);
			}
		}
	}

	private static String escapeJson(String str) {
		if(str == null) return "";
		StringBuilder sb = new StringBuilder(str.length());
		for(int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			switch(c) {
				case '\\': sb.append("\\\\"); break;
				case '"': sb.append("\\\""); break;
				case '\n': sb.append("\\n"); break;
				case '\r': sb.append("\\r"); break;
				case '\t': sb.append("\\t"); break;
				case '\b': sb.append("\\b"); break;
				case '\f': sb.append("\\f"); break;
				default:
					if(c < 0x20) {
						sb.append(String.format("\\u%04x", (int)c));
					} else {
						sb.append(c);
					}
			}
		}
		return sb.toString();
	}

	private static String extractJsonContent(String json) {
		int choicesIdx = json.indexOf("\"choices\"");
		if(choicesIdx == -1) return json;
		
		int contentIdx = json.indexOf("\"content\"", choicesIdx);
		if(contentIdx == -1) return json;
		
		int colonIdx = json.indexOf(":", contentIdx);
		if(colonIdx == -1) return json;
		
		int start = json.indexOf("\"", colonIdx + 1);
		if(start == -1) return json;
		start++;
		
		int end = start;
		while(end < json.length()) {
			if(json.charAt(end) == '"' && json.charAt(end - 1) != '\\') {
				break;
			}
			end++;
		}
		
		if(end >= json.length()) return json;
		
		return json.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
	}

}
