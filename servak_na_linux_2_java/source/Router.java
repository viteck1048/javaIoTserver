




public final class Router {
    
    private Router() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    public static HTTPResponse route(HTTPRequest httpRequest) {
		HTTPResponse httpResponse = null;
		httpResponse = allPortsRoute(httpRequest);
		if(httpResponse != null) {
			return httpResponse;
		}
		if(httpRequest.port == 80 || httpRequest.port == 443) {
			// Обробка запитів на порти 80 та 443
			httpResponse = webRoute(httpRequest);
		}
		else {
			httpResponse = specialPortRoute(httpRequest);
		}

		if(httpResponse == null && httpRequest.revers == HTTPRequest.ReversType.NO_REVERSE) {
			if(httpResponse == null) {
				if(httpRequest.method.equals("GET") || httpRequest.method.equals("HEAD")) {
					if (httpRequest.path.startsWith("/www_scripts"))
						httpResponse = handleWWWScripts(httpRequest);

					else if (httpRequest.path.startsWith("/www80_scripts"))
						httpResponse = handleWWW80Scripts(httpRequest);

					// GetRes на відсутньому файлі сам провалюється в GetRes80,
					// але на userID == 0 віддає 503, тож анонімів веземо в www80 одразу
					else if (httpRequest.userID != 0 && httpRequest.isHttps)
						httpResponse = GetRes.getRes(httpRequest);

					else
						httpResponse = GetRes80.getRes80(httpRequest);
				}

				else if (httpRequest.method.compareTo("POST") == 0)
					httpResponse = PostRes.postRes(httpRequest);
		/* 		
				else if (httpRequest.method.compareTo("PUT") == 0)
					httpResponse = PutRes.putRes(httpRequest);
				
				else if (httpRequest.method.compareTo("DELETE") == 0)
					httpResponse = DeleteRes.deleteRes(httpRequest);
		*/		
				else
					httpRequest.revers = HTTPRequest.ReversType.BANRESPONSE;
			}
			
		}

		if(httpRequest.revers != HTTPRequest.ReversType.NO_REVERSE || httpResponse == null) {
			httpResponse = ReverseProxy.handleReverseRequest(httpRequest);
		}
		if (httpResponse == null) {
			return new HTTPResponse(503);
		}
        return httpResponse;
    }

	private static HTTPResponse webRoute(HTTPRequest httpRequest) {
		// Обробка запитів на порти 80 та 443
		HTTPResponse httpResponse = null;
		
		if(httpRequest.path.equals("/") || httpRequest.path.equals("/home")) {
			httpRequest.path = Configs.getParam("homepage");
		}

		if(false)
			;// TODO: implement

		else if (httpRequest.path.compareTo("/upload") == 0 && httpRequest.method.compareTo("POST") == 0)
			httpResponse = new HTTPResponse(200);
		
		else if(Configs.getBoolean("esp") && httpRequest.path.startsWith(Configs.getParam("esp_path")))
			httpRequest.revers = HTTPRequest.ReversType.RELAYS_SERVER;

		else if(Configs.getBoolean("liraCalc") && httpRequest.path.startsWith(Configs.getParam("liraCalc_path")))
			httpRequest.revers = HTTPRequest.ReversType.OLD_SERVAK;

		else if(Configs.getBoolean("mach_time_rev") && httpRequest.path.startsWith(Configs.getParam("mach_time_path")))
			httpRequest.revers = HTTPRequest.ReversType.MACHINE_TIME;

		else if(Configs.getBoolean("php_fpm") && httpRequest.path.matches("(?i).*/[^/]+\\.(php|php3|php4|php5|phtml)([/?#].*)?"))
			httpRequest.revers = HTTPRequest.ReversType.PHP_FPM;

		else if(Configs.getBoolean("ai_assist") && httpRequest.path.startsWith(Configs.getParam("ai_assist_api_chat")))
			httpRequest.revers = HTTPRequest.ReversType.AI_CHAT;


		return httpResponse;
	}

	private static HTTPResponse specialPortRoute(HTTPRequest httpRequest) {
		// Обробка запитів на спеціальні порти
		HTTPResponse httpResponse = null;
		
		if(false) {
			;
		}

		else if(Configs.getBoolean("avr") && httpRequest.port == Configs.getInt("avr_port")) {
			httpResponse = DBClass.requestFromGadget(httpRequest);
		}

		else {
			httpRequest.revers = HTTPRequest.ReversType.UNI_PRXY;
		}

		return httpResponse;
	}

	private static HTTPResponse allPortsRoute(HTTPRequest httpRequest) {
		HTTPResponse httpResponse = null;
		
		if(httpRequest.path.compareTo(Configs.getParam("dbg_post_message_path")) == 0) {
			System.out.println("+++++++++++++++++++++++++++++++++++++");
			System.out.println(httpRequest.getZnach("user-agent", HTTPRequest.arrType.HEADER) + " DBG MSG, length: " + httpRequest.getZnach("content-length", HTTPRequest.arrType.HEADER) + ":");
			System.out.println(new String(httpRequest.body));
			System.out.println("+++++++++++++++++++++++++++++++++++++");
			for(byte b : httpRequest.body)
				System.out.printf("%02X ", b);
			System.out.println();
			System.out.println("+++++++++++++++++++++++++++++++++++++");/**/
			return new HTTPResponse(200);
		}

		// Перевірка по шляху йде першою: || коротко замикається, тож ледачий парсер
		// wwwForm не тригериться. Друга умова — для старих клієнтів, доки не перейдуть
		else if(httpRequest.path.equals("/authorization_check") || httpRequest.chkZnach("authorization", "check")) {
			if(httpRequest.userID == 0 || !httpRequest.isHttps) {
				httpResponse = new HTTPResponse(400);
				httpResponse.set_fl_err_prnt_hdr(false);
				httpResponse.setMsg("Client " + httpRequest.clientAddress + ". Authorization check failed for Session ID: " + httpRequest.X_Session_ID);
			}
			else {
				httpResponse = new HTTPResponse(200);
				httpResponse.set_fl_err_prnt_hdr(false);
				httpResponse.setMsg("Client " + httpRequest.clientAddress + ". Authorization check passed for user ID: " + httpRequest.userID);
			}
		} 

		else if (httpRequest.path.equals("/reestr/new") || httpRequest.path.equals("/reestr/authorize")
				|| httpRequest.chkParam("reestr")) {
			// Запити до RegistrUsers
			if(httpRequest.isHttps) {
				httpResponse = RegistrUsers.reestr(httpRequest);
			}
			else {
				httpResponse = GetRes.redirectLogOut();
			}
		}

		return httpResponse;
	}
	
	private static HTTPResponse handleWWW80Scripts(HTTPRequest httpRequest) {
		if(httpRequest.path.compareTo("/www80_scripts/get_links") == 0) {
			return GetRes80.getLinks(httpRequest);
		}
		if(Configs.getBoolean("ban_response")) {
			httpRequest.revers = HTTPRequest.ReversType.BANRESPONSE;
			return ReverseProxy.handleReverseRequest(httpRequest);
		}
		return new HTTPResponse(400);
	}
	
	private static HTTPResponse handleWWWScripts(HTTPRequest httpRequest) {
		if(httpRequest.userID != 0 && httpRequest.isHttps) {
			if(httpRequest.path.compareTo("/www_scripts/scan_dwnld_directory") == 0 && Configs.getBoolean("download")) {
				return GetRes.scanDwnldDirectory(httpRequest);
			}
			else if(httpRequest.path.compareTo("/www_scripts/logout") == 0) {
				KeyManager.logout(httpRequest.X_Session_ID, httpRequest.clientAddress);
				HTTPResponse httpResponse = new HTTPResponse(200);
				httpResponse.set_fl_err_prnt_hdr(false);
				httpResponse.setMsg("Client " + httpRequest.clientAddress + ". Logout passed for user ID: " + httpRequest.userID);
				return httpResponse;
			}
			else if(httpRequest.path.compareTo("/www_scripts/clear_cache") == 0) {
				long bytes = FileCacheManager.getCacheBytes();
				int files = FileCacheManager.getFileCacheSize();
				int dirs = FileCacheManager.getDirectoryCacheSize();
				FileCacheManager.clearCache();
				HTTPResponse httpResponse = new HTTPResponse(200);
				httpResponse.set_fl_err_prnt_hdr(false);
				httpResponse.setMsg("Client " + httpRequest.clientAddress + ". Cache cleared by user ID: " + httpRequest.userID
					+ " (files: " + files + ", dirs: " + dirs + ", bytes: " + bytes + ")");
				return httpResponse;
			}
			else if(httpRequest.path.compareTo("/www_scripts/clear_banlist") == 0) {
				int removed = FirewallIP.clearBlackList();
				HTTPResponse httpResponse = new HTTPResponse(200);
				httpResponse.set_fl_err_prnt_hdr(false);
				httpResponse.setMsg("Client " + httpRequest.clientAddress + ". Ban list cleared by user ID: " + httpRequest.userID
					+ " (IPs: " + removed + ")");
				return httpResponse;
			}
		}
		else {
			HTTPResponse httpResponse = new HTTPResponse(403);
			httpResponse.set_fl_err_prnt_hdr(false);
			httpResponse.setMsg("www_scripts: " + httpRequest.clientAddress + ". Not authorized");
			return httpResponse;
		}
		if(Configs.getBoolean("ban_response")) {
			httpRequest.revers = HTTPRequest.ReversType.BANRESPONSE;
			return ReverseProxy.handleReverseRequest(httpRequest);
		}
		return new HTTPResponse(400);
	}
}
































/*
if (httpRequest.path.startsWith("/www80_scripts") && (httpRequest.method.compareTo("GET") == 0 || httpRequest.method.compareTo("HEAD") == 0)) {
							httpResponse = handleWWW80Scripts(httpRequest);
						} 
						else if (httpRequest.path.startsWith("/www_scripts") && (httpRequest.method.compareTo("GET") == 0 || httpRequest.method.compareTo("HEAD") == 0)) {
							httpResponse = handleWWWScripts(httpRequest);
						}
						/*else if (httpRequest.revers == HTTPRequest.ReversType.UNI_PRXY) {
							httpResponse = handlePort443(httpRequest);
						}
						// Розділення обробки запитів за портами та статусом автентифікації
						else {
							switch (port) {
								case 8083:
									// Порт 8083 має своє шифрування
									if(httpRequest.revers != HTTPRequest.ReversType.NO_REVERSE) {
										// Якщо це запит з реверсом, обробляємо як звичайний запит
									} else if(((httpRequest.method.compareTo("POST") == 0 && httpRequest.contentLength == 119) || 
												(httpRequest.method.compareTo("PUT") == 0 && httpRequest.contentLength == 959)) && 
												httpRequest.Content_Type.compareTo("application/octet-stream") != 0) {
										// Обробка текстових запитів на порт 8083
										httpResponse = handlePort8083(httpRequest.body, 101);
									} else if(((httpRequest.method.compareTo("POST") == 0 && httpRequest.contentLength == 40) || 
												(httpRequest.method.compareTo("PUT") == 0 && httpRequest.contentLength == 320)) && 
												httpRequest.Content_Type.compareTo("application/octet-stream") == 0) {
										// Обробка бінарних запитів на порт 8083
										httpResponse = handlePort8083(httpRequest.bodyData, 101);
									}
									break;
								case 80:
									// Порт 80 - завжди неавтентифіковані запити
									httpResponse = handlePort80(httpRequest);
									break;
								case 443:
									// Порт 443 - перевіряємо параметр reestr для RegistrUsers
									if(httpRequest.chkZnach("authorization", "check")) {
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
									} else if (httpRequest.chkParam("reestr")) {
										// Запити до RegistrUsers
										httpResponse = RegistrUsers.reestr(httpRequest);
									} else if (httpRequest.userID == 0 && httpRequest.revers == HTTPRequest.ReversType.MACHINE_TIME && (httpRequest.method.compareTo("GET") == 0 || httpRequest.method.compareTo("HEAD") == 0)) {
										// Читання MachineTime дозволене без автентифікації
										httpResponse = handlePort443(httpRequest);
									} else if (httpRequest.userID == 0) {
										// Неавтентифіковані запити на порт 443
										httpResponse = handlePort80(httpRequest);
									} else {
										// Автентифіковані запити на порт 443
										httpResponse = handlePort443(httpRequest);
									}
									break;
								default:
									httpResponse = handlePort443(httpRequest);
									//httpResponse = new HTTPResponse("HTTP/1.1 400 ERROR\r\n\r\n", null);
									break;
							}
						}
*/

				
/*	                            дебаґ - в www80
				
*/
/*					            аплоад для спідтесту - в www80
				if (httpRequest.path.compareTo("/upload") == 0 && httpRequest.method.compareTo("POST") == 0)
					httpResponse = new HTTPResponse(200);
				else
*/		