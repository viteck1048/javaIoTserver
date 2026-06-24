import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Обробник для UNI_PRXY типу реверсу - універсальний реверс-проксі
 */
public final class UniProxyHendler {
	
	private UniProxyHendler() {
		throw new UnsupportedOperationException("Utility class");
	}
	
	public static HTTPResponse uniPrxyResend(HTTPRequest httpRequest) {
		// Отримуємо prxy_key за портом
		String prxyKey = Configs.getKeyForUniPrxyPort(httpRequest.port);
		if (prxyKey == null) {
			System.err.println("UniProxyHendler: No configuration found for port " + httpRequest.port);
			return new HTTPResponse(503);
		}
		
		// Витягуємо параметри з конфігу
		String dialHost = Configs.getParam(prxyKey + "_dial_host");
		int dialPort = Configs.getInt(prxyKey + "_dial_port");
		boolean dialSsl = Configs.getBoolean(prxyKey + "_dial_ssl");
		String authHeader = Configs.getParam(prxyKey + "_authorization_header");
		boolean authUserId = Configs.getBoolean(prxyKey + "_authorization_userID");

		if (dialHost == null || dialPort == 0) {
			System.err.println("UniProxyHendler: Missing dial_host or dial_port for " + prxyKey);
			return new HTTPResponse(503);
		}
		
		// Отримуємо опції дебагу
		String dbgOptions = Configs.getParam(prxyKey + "_dbg_options");
		boolean debugRequestHeaders = false;
		boolean debugRequestBody = false;
		boolean debugResponseHeaders = false;
		boolean debugResponseBody = false;
		boolean debugResponseLLMThinking = false;
		boolean debugResponseLLMFinally = false;
		
		if (Configs.getDefine(prxyKey + "_dbg_options")) {
			String[] options = dbgOptions.toLowerCase().split("\\s+");
			for (String option : options) {
				switch (option) {
					case "request_headers":
						debugRequestHeaders = true;
						break;
					case "request_body":
						debugRequestBody = true;
						break;
					case "response_headers":
						debugResponseHeaders = true;
						break;
					case "response_body":
						debugResponseBody = true;
						break;
					case "response_llm_thinking":
						debugResponseLLMThinking = true;
						break;
					case "response_llm_finally":
						debugResponseLLMFinally = true;
						break;
					default:
						break;
				}
			}
		}
		
		// Дебаг запиту
		if (debugRequestHeaders) {
			System.out.println("=== UNI PROXY REQUEST HEADERS (" + prxyKey + ") ===");
			System.out.println(httpRequest.header);
		}
		if (debugRequestBody && httpRequest.bodyData != null) {
			System.out.println("=== UNI PROXY REQUEST BODY (" + prxyKey + ") ===");
			String bodyStr = new String(httpRequest.bodyData);
			String contentType = httpRequest.getZnach("Content-Type", HTTPRequest.arrType.HEADER);
			if (contentType != null && contentType.toLowerCase().contains("json")) {
				String formattedJson = formatJson(bodyStr);
				System.out.println(formattedJson);
			} else {
				System.out.println(bodyStr);
			}
		}
		
		// Перевіряємо авторизаційний заголовок, якщо він визначено в конфігу
		if (Configs.getDefine(prxyKey + "_authorization_header")) {
			String requestAuth = httpRequest.getZnach("authorization", HTTPRequest.arrType.HEADER);
			if (requestAuth == null || requestAuth.compareTo(authHeader) != 0) {
				System.err.println("UniProxyHendler: Authorization header mismatch for " + prxyKey);
				return new HTTPResponse(401);
			}
		}

		// Перевіряємо userID, якщо для проксі увімкнено авторизацію за ним
		if (authUserId) {
			if(httpRequest.chkZnach("authorization", "check")) {
				HTTPResponse httpResponse; 
				if(httpRequest.userID == 0) {
					httpResponse = new HTTPResponse(400);
					httpResponse.set_fl_err_prnt_hdr(false);
					httpResponse.setMsg("Client " + httpRequest.clientAddress + ". Authorization check failed for Session ID: " + httpRequest.X_Session_ID);
				}
				else {
					httpResponse = new HTTPResponse(200);
					httpResponse.set_fl_err_prnt_hdr(false);
					httpResponse.setMsg("Client " + httpRequest.clientAddress + ". Authorization check passed for user ID: " + httpRequest.userID);
				}
				return httpResponse;
			} else if (httpRequest.chkParam("reestr")) {
				// Запити до RegistrUsers
				return RegistrUsers.reestr(httpRequest);
			} else if (httpRequest.userID == 0) {
				System.err.println("UniProxyHendler: userID authorization failed for " + prxyKey + " (userID == 0)");
				return new HTTPResponse(401);
			}
		}

		NetworkClient nc = null;
		try {
			nc = new NetworkClient(dialHost, dialPort, dialSsl);
			nc.setSoTimeout(300000);
	//		nc.setTcpNoDelay(true);
		} catch (IOException e) {
			System.out.println("UniProxyHendler error: " + e.getMessage());
			e.printStackTrace();
			return new HTTPResponse(503);
		}
		
		try {
			
			nc.sendAll(httpRequest.header.getBytes(), httpRequest.bodyData);
			
			byte[] headers = nc.recvChunkHeaders();
			if (headers == null) {
				return null;
			}
			String headerStr = new String(headers);
			if (debugResponseHeaders) {
				System.out.println("=== UNI PROXY RESPONSE HEADERS (" + prxyKey + ") ===");
				System.out.println(headerStr);
				System.out.println("=== || ===");
			}
			String lowerHeaders = headerStr.toLowerCase();
			if (lowerHeaders.contains("transfer-encoding:") && lowerHeaders.contains("chunked")) {

				String thinking = "";
				String reasoning = "";
				String content = "";
				
				HTTPResponse resp = new HTTPResponse(headerStr, null, "chunkHeaders");
				resp.normalizeHeaders(httpRequest);
				httpRequest.outStream.write(resp.getHeaders());
				httpRequest.outStream.flush();

				int len = 1;
				byte[] bufLen;
				byte[] bufChunk;
				if (debugResponseBody) {
					System.out.println("=== UNI PROXY RESPONSE CHUNK BODY (" + prxyKey + ") START ===");
				}
				while (len > 0) {
					bufLen = nc.recvChunkStr();
					len = 0;
					int i = 0;
					for (byte b : bufLen) {
						int dig = Character.digit(b, 16);
						if(dig == -1)
							break;
						i++;
						if (i > 6)
							break;
						len = (len << 4) | dig;
					}
					if (len > 1024 * 1024 * 10) {
						throw new IOException("Chunk too big or invalid " + len);
					}
					bufChunk = nc.recvChunk(len);
					if (bufChunk != null) {
			
						String chunkJson = new String(bufChunk);

						if (debugResponseBody) {
							System.out.print(chunkJson);
						}

						int thinkingIndex = chunkJson.indexOf("\"thinking\":");
						int reasonigIndex = chunkJson.indexOf("\"reasoning\":");
						int contntIndex = chunkJson.indexOf("\"content\":");
						
						if (thinkingIndex > 0) {
							thinking += getContent(chunkJson, thinkingIndex + 11);
						}
						if (reasonigIndex > 0) {
							reasoning += getContent(chunkJson, reasonigIndex + 11);
						}

						if (contntIndex > 0) {
							content += getContent(chunkJson, contntIndex + 9);
						}
						
					}
					nc.recvChunkEndStream();

					httpRequest.outStream.write(bufLen);
					httpRequest.outStream.write(13);
					httpRequest.outStream.write(10);
					httpRequest.outStream.write(bufChunk);
					httpRequest.outStream.write(13);
					httpRequest.outStream.write(10);
					httpRequest.outStream.flush();
				}

				if (debugResponseBody) {
					System.out.println("=== UNI PROXY RESPONSE CHUNK BODY (" + prxyKey + ") END ===");
				}

				if (debugResponseLLMThinking) {
					System.out.print("=================== Роздуми ===");
					if(thinking.length() != 0) {
						System.out.println(" openIA API ====================");
						System.out.println(thinking);
					} else {
						System.out.println(" ollama API ====================");
						System.out.println(reasoning);
					}
					System.out.println("======================== || =======================");
				}
				if (debugResponseLLMFinally) {
					System.out.println("========================== Відповідь ==========================");
					System.out.println(content);
					System.out.println("========================== || ============================");
				}

				return new HTTPResponse(0, httpRequest);
			} else {
				// Перевіряємо на Content-Length
				int contentLengthIndex = lowerHeaders.indexOf("content-length:");
				byte[] body = null;
				if (contentLengthIndex != -1) {
					String lengthStr = headerStr.substring(contentLengthIndex + 15).split("\r\n")[0].trim();
					try {
						int contentLength = Integer.parseInt(lengthStr);
						if (contentLength > 0) {
							body = nc.recvChunk(contentLength);
							if (debugResponseBody) {
								System.out.println("=== UNI PROXY RESPONSE BODY (" + prxyKey + ") ===");
								for (byte b : body) {
									System.out.print((char) b);
								}
								System.out.println();
								for (int i = 0; i < body.length; i++) {
									byte b = body[i];
									System.out.print(String.format("%02X ", b));
									if (i % 16 == 15) {
										System.out.println();
									}
								}
								System.out.println();
							}
						}
					} catch (NumberFormatException e) {
						// Content-Length не вдалося розпарсити, ігноруємо боді
					}
				}
				return new HTTPResponse(headerStr, body, "revers to " + prxyKey);
			}
		
		} catch (IOException e) {
			System.err.println("uniPrxy.recvAll: IOException - " + e.getMessage());
			e.printStackTrace();
			return null;
		} finally {
			if (nc != null) {
				nc.close();
			}
		}
	}

	/**
	 * Просте форматування JSON з відступами
	 */
	private static String formatJson(String json) {
		StringBuilder formatted = new StringBuilder();
		int indent = 0;
		boolean inString = false;
		
		for (int i = 0; i < json.length(); i++) {
			char c = json.charAt(i);
			
			if (c == '"') {
				inString = !inString;
				formatted.append(c);
			} else if (inString) {
				formatted.append(c);
			} else {
				switch (c) {
					case '{':
					case '[':
						formatted.append(c).append('\n');
						indent++;
						addIndent(formatted, indent);
						break;
					case '}':
					case ']':
						formatted.append('\n');
						indent--;
						addIndent(formatted, indent);
						formatted.append(c);
						break;
					case ',':
						formatted.append(c).append('\n');
						addIndent(formatted, indent);
						break;
					case ':':
						formatted.append(c).append(' ');
						break;
					default:
						if (!Character.isWhitespace(c)) {
							formatted.append(c);
						}
						break;
				}
			}
		}
		
		return formatted.toString();
	}

	private static void addIndent(StringBuilder sb, int level) {
		for (int i = 0; i < level; i++) {
			sb.append("  ");
		}
	}

	private static String getContent(String json, int startIndex) {
		// Знаходимо перший '"'
		int startQuote = json.indexOf('"', startIndex);
		if (startQuote == -1) {
			return null;
		}
		
		// Знаходимо наступний '"' який не є '\"'
		int endQuote = startQuote + 1;
		boolean escaped = false;
		while (endQuote < json.length()) {
			char c = json.charAt(endQuote);
			if (escaped) {
				escaped = false;
			} else if (c == '\\') {
				escaped = true;
			} else if (c == '"') {
				break;
			}
			endQuote++;
		}
		
		if (endQuote >= json.length()) {
			return null;
		}
		
		// Вирізаємо сабстрінг між лапками
		String content = json.substring(startQuote + 1, endQuote);
		
		// Замінюємо екрановані символи на відповідні символи
		content = content.replace("\\\"", "\"");
		content = content.replace("\\r", "\r");
		content = content.replace("\\n", "\n");
		//content = content.replace("\\{", "{");
		//content = content.replace("\\}", "}");
		
		return content;
	}
}
