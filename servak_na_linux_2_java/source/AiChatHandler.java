import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;

public final class AiChatHandler {

	private static final AtomicBoolean aiChatBusy = new AtomicBoolean(false);
	private static final String DEFAULT_PROMPT = "Ти — AI-помічник на сайті керування IoT-пристроями. Допомагаєш користувачам керувати реле, переглядати статуси пристроїв та налаштовувати систему.";

	private AiChatHandler() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static HTTPResponse aiChatResend(HTTPRequest httpRequest) {
		if (!Configs.getBoolean("ai_assist_parallel_requests")) {
			if (!aiChatBusy.compareAndSet(false, true)) {
				return new HTTPResponse(503);
			}
		}

		NetworkClient nc = null;
		try {
			String url = Configs.getParam("ai_assist_url");
			String[] hostPort = url.split(":");
			String host = hostPort[0];
			int port = Integer.parseInt(hostPort[1]);

			nc = new NetworkClient(host, port, true);
			nc.setSoTimeout(300000);
			nc.setTcpNoDelay(true);

			String userMess      = httpRequest.getZnach("user_mess",    HTTPRequest.arrType.QUERY);
			String chatHistJson  = httpRequest.getZnach("chat_history",  HTTPRequest.arrType.QUERY);
			String page          = httpRequest.getZnach("page",          HTTPRequest.arrType.QUERY);
			String acceptLang    = httpRequest.getZnach("Accept-Language", HTTPRequest.arrType.HEADER);
			String userName      = KeyManager.getUserName(httpRequest.userID);

			String systemPrompt = buildSystemPrompt(page, userName, acceptLang);
			List<String[]> history = parseChatHistory(chatHistJson);
			String jsonBody = buildRequestJson(systemPrompt, history, userMess != null ? userMess : "");

			byte[] jsonBodyBytes = jsonBody.getBytes("UTF-8");

			String request = "POST /v1/chat/completions HTTP/1.1\r\n"
				+ "Host: " + host + "\r\n";
			if (Configs.getDefine("ai_assist_authorization_header")) {
				request += Configs.getParam("ai_assist_authorization_header") + "\r\n";
			} else if (Configs.getDefine("ai_assist_token")) {
				request += "Authorization: Bearer " + Configs.getParam("ai_assist_token") + "\r\n";
			}
			request += "Content-Type: application/json\r\n"
				+ "Content-Length: " + jsonBodyBytes.length + "\r\n"
				+ "Connection: close\r\n"
				+ "\r\n";

			nc.sendChunk(request.getBytes("UTF-8"));
			nc.sendChunk(jsonBodyBytes);
			nc.sendFlush();

			byte[] fullResponse = nc.recvAll();
			String raw = new String(fullResponse, "UTF-8");
			int headerEnd = raw.indexOf("\r\n\r\n");
			if (headerEnd == -1) return new HTTPResponse(503);
			String responseJson = raw.substring(headerEnd + 4);

			String content = extractJsonContent(responseJson);
			byte[] contentBytes = content.getBytes("UTF-8");
			String respHeaders = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: "
				+ contentBytes.length + "\r\nConnection: Closed\r\n\r\n";
			return new HTTPResponse(respHeaders, contentBytes, "AI chat response");

		} catch (Exception e) {
			System.out.println("AI Chat error: " + e.getMessage());
			e.printStackTrace();
			return new HTTPResponse(500);
		} finally {
			if (nc != null) nc.close();
			if (!Configs.getBoolean("ai_assist_parallel_requests")) {
				aiChatBusy.set(false);
			}
		}
	}

	// Builds system prompt: loads from file (ai_assist_prompt_file) or falls back to
	// config string (ai_assist_prompt) or the hardcoded default.
	// If the file has ## /path/ section headers, the general part + the matching section are combined.
	private static String buildSystemPrompt(String page, String userName, String acceptLang) {
		String base;
		if (Configs.getDefine("ai_assist_prompt_file")) {
			byte[] fileBytes = FileCacheManager.getFile(Configs.getParam("ai_assist_prompt_file"));
			if (fileBytes != null) {
				try {
					base = extractPromptForPage(new String(fileBytes, "UTF-8"), page);
				} catch (Exception e) {
					base = DEFAULT_PROMPT;
				}
			} else {
				base = DEFAULT_PROMPT;
			}
		} else if (Configs.getDefine("ai_assist_prompt")) {
			base = Configs.getParam("ai_assist_prompt");
		} else {
			base = DEFAULT_PROMPT;
		}

		StringBuilder sb = new StringBuilder(base);
		if (userName != null && !userName.isEmpty())
			sb.append("\nКористувач: ").append(userName).append(".");
		if (acceptLang != null && !acceptLang.isEmpty())
			sb.append("\nВідповідай мовою браузера клієнта (Accept-Language: ").append(acceptLang).append(").");
		return sb.toString();
	}

	// Splits the prompt file on "\n## " section markers.
	// Returns: general preamble (first chunk) + the section whose header matches page.
	// If no page section matches, returns just the general preamble.
	private static String extractPromptForPage(String fileContent, String page) {
		String[] sections = fileContent.split("\n## ");
		String general = sections[0].trim();
		if (page == null || page.isEmpty() || sections.length <= 1) return general;
		for (int i = 1; i < sections.length; i++) {
			int nl = sections[i].indexOf('\n');
			String header = (nl >= 0 ? sections[i].substring(0, nl) : sections[i]).trim();
			if (page.equals(header) || page.startsWith(header)) {
				String body = nl >= 0 ? sections[i].substring(nl + 1).trim() : "";
				return general + "\n\n" + body;
			}
		}
		return general;
	}

	// Parses [{role:"...",content:"..."},...] produced by the frontend.
	// Hand-rolled: no JSON library on classpath.
	private static List<String[]> parseChatHistory(String json) {
		List<String[]> result = new ArrayList<>();
		if (json == null || json.trim().isEmpty()) return result;
		int i = 0;
		while (i < json.length()) {
			int objStart = json.indexOf('{', i);
			if (objStart == -1) break;
			int objEnd = findObjectEnd(json, objStart);
			if (objEnd == -1) break;
			String obj = json.substring(objStart + 1, objEnd);
			String role    = jsonStringValue(obj, "role");
			String content = jsonStringValue(obj, "content");
			if (role != null && content != null
					&& (role.equals("user") || role.equals("assistant"))) {
				result.add(new String[]{role, content});
			}
			i = objEnd + 1;
		}
		return result;
	}

	// Finds the closing '}' of a JSON object, respecting nested strings.
	private static int findObjectEnd(String s, int start) {
		int depth = 0;
		boolean inStr = false;
		for (int i = start; i < s.length(); i++) {
			char c = s.charAt(i);
			if (inStr) {
				if (c == '\\') { i++; continue; }
				if (c == '"') inStr = false;
			} else {
				if (c == '"') { inStr = true; continue; }
				if (c == '{') depth++;
				else if (c == '}') { if (--depth == 0) return i; }
			}
		}
		return -1;
	}

	// Extracts the string value for a key from a flat JSON object body (without outer braces).
	private static String jsonStringValue(String obj, String key) {
		String search = "\"" + key + "\"";
		int idx = obj.indexOf(search);
		if (idx == -1) return null;
		int colon = obj.indexOf(':', idx + search.length());
		if (colon == -1) return null;
		int quote = obj.indexOf('"', colon + 1);
		if (quote == -1) return null;
		StringBuilder sb = new StringBuilder();
		int j = quote + 1;
		while (j < obj.length()) {
			char c = obj.charAt(j);
			if (c == '"') break;
			if (c == '\\' && j + 1 < obj.length()) {
				char next = obj.charAt(j + 1);
				switch (next) {
					case 'n':  sb.append('\n'); j += 2; continue;
					case 'r':  sb.append('\r'); j += 2; continue;
					case 't':  sb.append('\t'); j += 2; continue;
					case '"':  sb.append('"');  j += 2; continue;
					case '\\': sb.append('\\'); j += 2; continue;
					default:   sb.append(next); j += 2; continue;
				}
			}
			sb.append(c);
			j++;
		}
		return sb.toString();
	}

	private static String buildRequestJson(String systemPrompt, List<String[]> history, String userMess) {
		StringBuilder sb = new StringBuilder();
		sb.append("{\"model\":\"").append(Configs.getParam("ai_assist_model")).append("\"");
		sb.append(",\"messages\":[");
		sb.append("{\"role\":\"system\",\"content\":\"").append(escapeJson(systemPrompt)).append("\"}");
		for (String[] msg : history) {
			sb.append(",{\"role\":\"").append(msg[0]).append("\",\"content\":\"")
				.append(escapeJson(msg[1])).append("\"}");
		}
		sb.append(",{\"role\":\"user\",\"content\":\"").append(escapeJson(userMess)).append("\"}");
		sb.append("],\"stream\":false}");
		return sb.toString();
	}

	private static String escapeJson(String str) {
		if (str == null) return "";
		StringBuilder sb = new StringBuilder(str.length());
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			switch (c) {
				case '\\': sb.append("\\\\"); break;
				case '"':  sb.append("\\\""); break;
				case '\n': sb.append("\\n");  break;
				case '\r': sb.append("\\r");  break;
				case '\t': sb.append("\\t");  break;
				case '\b': sb.append("\\b");  break;
				case '\f': sb.append("\\f");  break;
				default:
					if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
					else sb.append(c);
			}
		}
		return sb.toString();
	}

	private static String extractJsonContent(String json) {
		int choicesIdx = json.indexOf("\"choices\"");
		if (choicesIdx == -1) return json;
		int contentIdx = json.indexOf("\"content\"", choicesIdx);
		if (contentIdx == -1) return json;
		int colonIdx = json.indexOf(":", contentIdx);
		if (colonIdx == -1) return json;
		int start = json.indexOf("\"", colonIdx + 1);
		if (start == -1) return json;
		start++;
		int end = start;
		while (end < json.length()) {
			if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\') break;
			end++;
		}
		if (end >= json.length()) return json;
		return json.substring(start, end)
			.replace("\\n", "\n")
			.replace("\\\"", "\"")
			.replace("\\\\", "\\");
	}
}
