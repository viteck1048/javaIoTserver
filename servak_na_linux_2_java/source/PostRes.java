
import java.nio.charset.StandardCharsets;


public final class PostRes {
	private PostRes() {
		throw new UnsupportedOperationException("Utility class");
	}
	
	public static HTTPResponse postRes(HTTPRequest httpRequest) {
		int userID = 0;
		if(httpRequest.X_Session_ID != 0)
			userID = KeyManager.checkKey(httpRequest.X_Session_ID, httpRequest.clientAddress);
		if(userID == 0) {
			httpRequest.prnt();
			if(Configs.getBoolean("ban_response") || Configs.getBoolean("Firewall")) {
				httpRequest.revers = HTTPRequest.ReversType.BANRESPONSE;
				return ReverseProxy.handleReverseRequest(httpRequest);
			}
			return GetRes.redirectLogOut();
		}
		
		if(httpRequest.path.compareTo("/submit") == 0 && httpRequest.chkParam("findname"))
			return findgadgetForName(httpRequest, userID);
		
		if(httpRequest.path.compareTo("/submit") == 0 && httpRequest.chkParam("sn_mega") && httpRequest.chkParam("pin"))
			return validPinAndRegistrGad(httpRequest, userID);
		
		if(httpRequest.path.startsWith("/reset_") == true && httpRequest.chkParam("pin"))
			return sendReset(httpRequest, KeyManager.getGadget(httpRequest.X_Session_ID, httpRequest.clientAddress));
		
//		if(httpRequest.path.compareTo("/upload") == 0)
//			return new HTTPResponse(200);
		
		if(Configs.getBoolean("photo_upload")) {
			if(httpRequest.path.startsWith(Configs.getParam("photo_post_message_path")))
				return photoUpload(httpRequest);
		}
	//	httpRequest.prnt();
		if(Configs.getBoolean("ban_response") || Configs.getBoolean("Firewall")) {
			httpRequest.revers = HTTPRequest.ReversType.BANRESPONSE;
			return ReverseProxy.handleReverseRequest(httpRequest);
		}
		return new HTTPResponse(400);
	}

	private static HTTPResponse photoUpload(HTTPRequest httpRequest) {
		String userName = KeyManager.getUserName(httpRequest.userID);
		String pathDir;
		if(Configs.getDefine("individual_user_photo_path_" + userName)) {
			pathDir = Configs.getParam("individual_user_photo_path_" + userName);
		} else {
			pathDir = Configs.getParam("path_to_save_photo") + userName;
		}
		String fileName = "photo_" + System.currentTimeMillis() + ".jpg";
		
		// Створюємо директорію, якщо вона не існує
		try {
			java.io.File directory = new java.io.File(pathDir);
			if(!directory.exists()) {
				directory.mkdirs();
				// Встановлюємо права власності 1000:1000 для директорії
				setOwnership(directory.getAbsolutePath(), 1000, 1000);
			}
			
			// Формуємо повний шлях до файлу
			java.io.File outputFile = new java.io.File(directory, fileName);
			
			// Зберігаємо тіло запиту як файл
			try(java.io.FileOutputStream fos = new java.io.FileOutputStream(outputFile)) {
				fos.write(httpRequest.body);
				fos.flush();
			}
			
			// Встановлюємо права власності 1000:1000 для файлу
			setOwnership(outputFile.getAbsolutePath(), 1000, 1000);
			
			System.out.println("Photo saved: " + outputFile.getAbsolutePath());
			return new HTTPResponse(200);
			
		} catch(Exception e) {
			System.err.println("Error saving photo: " + e.getMessage());
			e.printStackTrace();
			return new HTTPResponse(500);
		}
	}
	
	// Метод для встановлення прав власності файлу/директорії
	private static void setOwnership(String filePath, int uid, int gid) {
		try {
			ProcessBuilder pb = new ProcessBuilder("chown", uid + ":" + gid, filePath);
			Process process = pb.start();
			int exitCode = process.waitFor();
			if (exitCode != 0) {
				System.err.println("Failed to set ownership for " + filePath + ", exit code: " + exitCode);
			}
		} catch (Exception e) {
			System.err.println("Error setting ownership for " + filePath + ": " + e.getMessage());
		}
	}

	private static HTTPResponse sendReset(HTTPRequest httpRequest, long sn_mega) {
		DBClass dBClass = DBClass.getInstance();
		AvrRele avrRele = dBClass.findAvrReleBySerialNumber(sn_mega);
		if(avrRele == null)
			return new HTTPResponse(503);
		if(avrRele.sendReset(httpRequest) == false)
			return new HTTPResponse(503);
		
		try {
			byte perev_upd = 0;
			int i;
			for(i = 0; i < 20 && perev_upd == 0; i++) {
				Thread.sleep(2000);
				if(i > 3) {
					perev_upd = avrRele.getPerevUpd();
				}
			}
			
			if(i == 20) {
				String str = "изтече времето за изчакване на отговор от устройството";
				avrRele.resetUpdBt();
				return new HTTPResponse(str.getBytes().length, str.getBytes(), "validPinAndRegistrGad_pin_err_504.txt");
			}
			else if(perev_upd == 1) {
				httpRequest.method = "GET";
				httpRequest.path = "/get_det_gad";
				return GetRes.getRes(httpRequest);
			}
			else if(perev_upd == 2) {
				String str = "грешният pin, опитайте отново";
				return new HTTPResponse(str.getBytes().length, str.getBytes(), "validPinAndRegistrGad_pin_NOT_OK.txt");
			}
			else
				return new HTTPResponse(503);
			
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		return new HTTPResponse(503);
	}
	
	private static HTTPResponse validPinAndRegistrGad(HTTPRequest httpRequest, int userID) {
		DBClass dBClass = DBClass.getInstance();
		long sn_mega = Long.parseLong(httpRequest.getZnach("sn_mega"));
		AvrRele avrRele = dBClass.findAvrReleBySerialNumber(sn_mega);
		if(avrRele == null)
			return new HTTPResponse(503);
		if(avrRele.validPin(httpRequest.getZnach("pin")) == false)
			return new HTTPResponse(503);
		
		try {
			byte perev_upd = 0;
			int i;
			for(i = 0; i < 15 && perev_upd == 0; i++) {
				Thread.sleep(2000);
				if(i > 3) {
					perev_upd = avrRele.getPerevUpd();
				}
			}
			
			if(i == 15) {
	//			return new HTTPResponse(504);
				String str = "{\"msg\":\"изтече времето за изчакване на отговор от устройството\",\"status\":\"error\"}";
				avrRele.resetUpdBt();
				return new HTTPResponse(str.getBytes().length, str.getBytes(), "validPinAndRegistrGad_pin_err_504.app-json");
			}
			else if(perev_upd == 1) {
				String str = KeyManager.addGad(userID, sn_mega, avrRele.g_id, avrRele.getName());
				return new HTTPResponse(str.getBytes().length, str.getBytes(), "validPinAndRegistrGad_pin_OK.app-json");
			}
			else if(perev_upd == 2) {
				String str = "{\"msg\":\"грешният pin, опитайте отново\",\"status\":\"error\"}";
				return new HTTPResponse(str.getBytes().length, str.getBytes(), "validPinAndRegistrGad_pin_NOT_OK.app-json");
			}
			else
				return new HTTPResponse(503);
			
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		return new HTTPResponse(503);
	}
	
	private static HTTPResponse findgadgetForName(HTTPRequest httpRequest, int userID) {
		String html = "<div><h3 id='zaholovokslashkomentar'>Намерени устройства онлайн</h3>";
		String findPattern = httpRequest.getZnach("findname");
		DBClass dBClass = DBClass.getInstance();
		for(AvrRele avrRele : dBClass.avrReleList) {
			if(avrRele.online == true && avrRele.getName().toLowerCase().contains(findPattern.toLowerCase())) {
				boolean registraited = false;
				for (KeyManager.SnInfo device : httpRequest.sn_megaList) {
					if(device.sn_mega == avrRele.serialNumberMega) {
						registraited = true;
						break;
					}
				}
				if(registraited == false) {
					html += 
						"<p>" +
						"<div>" +
							"<a class='perelik_znajdenykh_prystrojiv' href='pokazhe pole i vse'>" + avrRele.getName() + " sn = " + avrRele.serialNumberMega + "</a>" +
							"<div class='pole_vvodu_pin' style='display: none;' value='" + avrRele.serialNumberMega + "'>" +
								"<form class='pin_proba' action='/submit' method='POST'>" +
									"<label for='pin'>Enter 4-digit PIN:</label><br>" +
									"<input type='hidden' name='sn_mega' value='" + avrRele.serialNumberMega + "' />" +
									"<input type='password' id='pin' name='pin' pattern='\\d{4}' maxlength='4' required autocomplete='new-password' placeholder='****'/>" +
									"<button type='submit'>Send</button>" +
								"</form>" +
							"</div>" +
						"</div>";
				}
			}
		}
		html += "</div>";
		
		html += """
			<script>
				$(document).ready(function() {
					$(document).on('click', 'a.perelik_znajdenykh_prystrojiv', function(e) {
						e.preventDefault();
						$('.pole_vvodu_pin').each(function() {
							$(this).css('display', 'none');
						});
						$(this).next('div.pole_vvodu_pin').css('display', '');
					});
				});
			</script>
		""";
		
		byte[] htmlContent = html.getBytes(StandardCharsets.UTF_8);
		return new HTTPResponse(htmlContent.length, htmlContent, "findgadgetForName.html");		
	}
	
}
