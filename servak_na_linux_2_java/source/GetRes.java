import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.net.Socket;
import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.io.BufferedInputStream;
import java.util.List;
import java.util.stream.Collectors;


public final class GetRes {
	
	private GetRes() {
		throw new UnsupportedOperationException("Utility class");
	}
	
    public static HTTPResponse getRes(HTTPRequest httpRequest) {
	
		String requestedFile = httpRequest.path;
		
		int userID = httpRequest.userID;
		if(userID == 0) {
			return new HTTPResponse(503);
		}
		
		if(requestedFile.equals("/get_perelik_prystrojiv")) {
			return getPerelikPrystrojiv(userID);
		}
		else if(requestedFile.equals("/get_det_gad")) {
			return getDetGad(httpRequest, userID);
		}
		else if(requestedFile.startsWith("/detali_")) {
		;//	return new HTTPResponse(htmlContent.length, htmlContent, "qq.html");
		}
		else if(requestedFile.equals("/add_prystrij")) {
			return getResFile("add_prystrij.html");
		}
		else if(requestedFile.equals("/view_logs")) {
			return viewLogs(httpRequest);
		}
		else if(requestedFile.equals("/")) {
			requestedFile = "/index.html";
		}
		else if(requestedFile.equals("/home")) {
			httpRequest.path = "/index.html";
			return GetRes80.getRes80(httpRequest);
		}
		Path filePath = Paths.get(Configs.getParam("www_directory") + requestedFile).normalize();
		if (filePath.startsWith(Paths.get(Configs.getParam("www_directory")))) {
			if (!filePath.toFile().isDirectory()) {
				if (filePath.toFile().exists()) {
					try {
						byte[] fileData = FileCacheManager.getFile(filePath.toString());
						if(fileData != null) {
							if(httpRequest.method.compareTo("HEAD") == 0)
								return new HTTPResponse(fileData.length, fileData, requestedFile, true);
							else
								return new HTTPResponse(fileData.length, fileData, requestedFile);
						} else {
							return new HTTPResponse(500);
						}
					} catch (Exception e) {
						return new HTTPResponse(500);
					}
				} else {
					return GetRes80.getRes80(httpRequest);
				}
			}
			else {
				return new HTTPResponse(404, httpRequest);
			}
		}
	
		return new HTTPResponse(400);
	}
	
	private static HTTPResponse getResFile(String fname) {
		String filePath = "res/" + fname;
		byte[] fileData = FileCacheManager.getFile(filePath);

		if (fileData != null) {
			try {
				return new HTTPResponse(fileData.length, fileData, fname);
			} catch (Exception e) {
				return new HTTPResponse(500);
			}
		} else {
			return new HTTPResponse(400);
		}
	}
	
	public static HTTPResponse redirectLogOut() {
		return getResFile("redirect_log_in.html");
	}
	
	public static HTTPResponse scanDwnldDirectory(HTTPRequest httpRequest) {
		try {
			// Отримуємо список файлів з кешу директорії www80
			String dwnldPath = Configs.getParam("dwnld_directory") + "/files/";
			List<String> files = FileCacheManager.scanDir(dwnldPath);

			// Формуємо JSON-відповідь
			StringBuilder jsonBuilder = new StringBuilder();
			
			jsonBuilder.append("{\"files\":[\n");

			for (int i = 0; i < files.size(); i++) {
				jsonBuilder.append("\"").append(files.get(i)).append("\"");
				if (i < files.size() - 1) {
					jsonBuilder.append(",\n");
				}
			}

			jsonBuilder.append("\n]}");

			String jsonString = jsonBuilder.toString();

			if(httpRequest.method.compareTo("HEAD") == 0)
				return new HTTPResponse(jsonString.getBytes().length, jsonString.getBytes(), "scan_dwnld_directory.app-json", true);
			return new HTTPResponse(jsonString.getBytes().length, jsonString.getBytes(), "scan_dwnld_directory.app-json");
		} catch (Exception e) {
			// В разі помилки повертаємо порожній JSON
			String jsonString = "{\"files\":[]}";
			if(httpRequest.method.compareTo("HEAD") == 0)
				return new HTTPResponse(jsonString.getBytes().length, jsonString.getBytes(), "scan_dwnld_directory.app-json", true);
			return new HTTPResponse(jsonString.getBytes().length, jsonString.getBytes(), "scan_dwnld_directory.app-json");
		}
	}

	public static HTTPResponse getLinks(HTTPRequest httpRequest) {
		String name = KeyManager.getUserName(httpRequest.userID);
		if(name == null) {
			return new HTTPResponse(503);
		}
		// Формуємо JSON-відповідь
		StringBuilder jsonBuilder = new StringBuilder();
		jsonBuilder.append("{");
		jsonBuilder.append("\"name\": \"" + name + "\", ");
		jsonBuilder.append("\"links\": [");
		if(Configs.getBoolean("liraCalc"))
			jsonBuilder.append("{\"url\": \"old_servak/\", \"title\": \"LiraCalc ConfigEditor\"}");
		if(Configs.getBoolean("esp"))
			jsonBuilder.append(",{\"url\": \"relay_servak/knopky.html\", \"title\": \"ESP Remote Control\"}");
		if(Configs.getBoolean("avr"))
			jsonBuilder.append(",{\"url\": \"avr_relays_control.html\", \"title\": \"AVR Remote Control\"}");
		
		jsonBuilder.append(",{\"url\": \"https://mijservak.pp.ua:18080/MachineTime18Channels/\", \"title\": \"MachineTime\"}");
		
		jsonBuilder.append("]");
		jsonBuilder.append("}");
		String jsonString = jsonBuilder.toString();
		if(httpRequest.method.compareTo("HEAD") == 0)
			return new HTTPResponse(jsonString.getBytes().length, jsonString.getBytes(), "links.app-json", true);
		return new HTTPResponse(jsonString.getBytes().length, jsonString.getBytes(), "links.app-json");
	}

	private static HTTPResponse getPerelikPrystrojiv(int userID) {
		ArrayList<KeyManager.SnInfo> devices = KeyManager.getSnMegaList(userID);
		
		StringBuilder jsonBuilder = new StringBuilder();
		jsonBuilder.append("{\"gadgets\":[");
		boolean first = true;
		for (KeyManager.SnInfo device : devices) {
			if (!first) {
				jsonBuilder.append(",");
			}
			first = false;
			
			long sn = device.sn_mega & 0xFFFFFFFFL;
			String name = device.name;
			AvrRele avrRele = DBClass.getInstance().findAvrReleBySerialNumber((long)sn);
			jsonBuilder.append("{");
			jsonBuilder.append("\"sn\": \"" + sn + "\", ");
			jsonBuilder.append("\"name\": \"" + name + "\", ");
			jsonBuilder.append("\"class\": \"icon ");
			if(avrRele == null || avrRele.online == false) {
				jsonBuilder.append("gray_ball ");
			}
			else if(avrRele.robota == false) {
				jsonBuilder.append("red_ball ");
			}
			else {
				jsonBuilder.append("green_ball ");
			}
			jsonBuilder.append(" get_gad active-menu-item-arr\", ");
			jsonBuilder.append("\"href\": \"/get_det_gad?sn=" + sn + "\" ");
			jsonBuilder.append("}");
		}
		jsonBuilder.append("]}");
		String jsonString = jsonBuilder.toString();
		return new HTTPResponse(jsonString.getBytes().length, jsonString.getBytes(), "get_perelik_prystrojiv.app-json");
	}

	private static HTTPResponse getDetGad(HTTPRequest httpRequest, int userID) {
	
		try {
			long sn = 0;
			if(httpRequest.chkParam("sn") == true)
				sn = Long.parseLong(httpRequest.getZnach("sn"));
			else
				sn = KeyManager.getGadget(httpRequest.X_Session_ID, httpRequest.clientAddress);
			
			boolean valid = false;
			for (KeyManager.SnInfo device : httpRequest.sn_megaList) {
				if(device.sn_mega == sn) {
					valid = true;
					break;
				}
			}

			if(valid) {
				String templatePath = "res/gadget_view.html";
				byte[] templateData = FileCacheManager.cloneFile(templatePath);
				if(templateData == null) {
					return new HTTPResponse(404);
				}

				Document doc = Jsoup.parse(new String(templateData, StandardCharsets.UTF_8));
				Elements tableRows = doc.select("tr.polerele");
				DBClass dBClass = DBClass.getInstance();
				AvrRele avrRele = null;
				avrRele = dBClass.findAvrReleBySerialNumber(sn);
				if(avrRele == null) {
					avrRele = dBClass.addToAvrReleList(sn);
				}
				String nameStr = new String(avrRele.name).trim();
				// Додаємо ім'я в заголовок
				Element namerele = doc.getElementById("namerele");
				if (namerele != null) {
					namerele.text(nameStr);
				}
				for(int i = 0; i < tableRows.size(); i++) {
					//Element tdName = tableRows.get(i).selectFirst("td.name");
					Element tdName = tableRows.get(i).selectFirst("div.content-wrapper");
					Element tdZnach = tableRows.get(i).selectFirst("td.znach");
					if(avrRele.arr_golovne_menu[i] != (byte)0x0f) {
						tableRows.get(i).attr("style", "display: ;");
						switch(avrRele.arr_golovne_menu[i]) {
							case 0x0c:
								tdName.html("<span class='text'>Маш. време общто</span>");
								tdZnach.text(String.format("%d:%02d", (avrRele.obscht_r) / 3600, ((avrRele.obscht_r) / 60) % 60));
								break;
							case 0x0d:
								tdName.html("<button class='reset' href='reset_null_tek'>R</button><span class='text'>Маш.вр.детайл</span>");
								if(avrRele.online) {
									tdZnach.text(String.format("%d:%02d", (avrRele.tek_r) / 3600, ((avrRele.tek_r) / 60) % 60));
								}
								else {
									tdZnach.text("offline");
								}
								break;
							case 0x0e:
								tdName.html("<span class='text'>входове->изходи</span>");
								if(avrRele.online) {
									int coor = (i % 2) + i * 8;
									char[] tmp = new char[7];
									for(int j = 0; j < 7; j++) {
										if(j != 3) {
											if(avrRele.dsplbuf[coor + j] == 0x0a) {
												tmp[j] = '1';
											}
											else {
												tmp[j] = '0';
											}
										}
										else
											tmp[j] = ':';
									}
									tdZnach.text(new String(tmp));
								}
								else {
									tdZnach.text("offline");
								}
								break;
							case 0x00:
								tdName.html("<button class='reset' href='reset_res_rv_1'>R</button><span class='text'>Времереле 1</span>");
								if(avrRele.online) {
									int coor = (i % 2) + i * 8;
									char[] tmp = new char[7];
									for(int j = 0; j < 6; j++) {
										tmp[j] = (char)avrRele.dsplbuf[coor + j];
									}
									/* if(avrRele.dsplbuf[coor + 6] == 8)
										tmp[6] = 11117;
									else if(avrRele.dsplbuf[coor + 6] == 10)
										tmp[6] = 9632;
									else */
										tmp[6] = ' ';
									tdZnach.text(new String(tmp));
								}
								else {
									tdZnach.text("offline");
								}
								break;
							case 0x01:
								tdName.html("<button class='reset' href='reset_res_rv_2'>R</button><span class='text'>Времереле 2</span>");
								if(avrRele.online) {
									int coor = (i % 2) + i * 8;
									char[] tmp = new char[7];
									for(int j = 0; j < 6; j++) {
										tmp[j] = (char)avrRele.dsplbuf[coor + j];
									}
									tmp[6] = ' ';
									tdZnach.text(new String(tmp));
								}
								else {
									tdZnach.text("offline");
								}
								break;
							case 0x02:
								tdName.html("<button class='reset' href='reset_res_rv_3'>R</button><span class='text'>Времереле 3</span>");
								if(avrRele.online) {
									int coor = (i % 2) + i * 8;
									char[] tmp = new char[7];
									for(int j = 0; j < 6; j++) {
										tmp[j] = (char)avrRele.dsplbuf[coor + j];
									}
									tmp[6] = ' ';
									tdZnach.text(new String(tmp));
								}
								else {
									tdZnach.text("offline");
								}
								break;
							case 0x04:
								tdName.html("<button class='reset' href='reset_res_ri_1'>R</button><span class='text'>Бр. импулси 1</span>");
								if(avrRele.online) {
									int coor = (i % 2) + i * 8;
									char[] tmp = new char[7];
									for(int j = 0; j < 7; j++) {
										tmp[j] = (char)avrRele.dsplbuf[coor + j];
									}
									tdZnach.html(avrRele.getStrImp(0, new String(tmp)));
								}
								else {
									tdZnach.html("offline");
								}
								break;
							case 0x05:
								tdName.html("<button class='reset' href='reset_res_ri_2'>R</button><span class='text'>Бр. импулси 2</span>");
								if(avrRele.online) {
									int coor = (i % 2) + i * 8;
									char[] tmp = new char[7];
									for(int j = 0; j < 7; j++) {
										tmp[j] = (char)avrRele.dsplbuf[coor + j];
									}
									tdZnach.html(avrRele.getStrImp(1, new String(tmp)));
								}
								else {
									tdZnach.html("offline");
								}
								break;
							case 0x06:
								tdName.html("<button class='reset' href='reset_res_ri_3'>R</button><span class='text'>Бр. импулси 3</span>");
								if(avrRele.online) {
									int coor = (i % 2) + i * 8;
									char[] tmp = new char[7];
									for(int j = 0; j < 7; j++) {
										tmp[j] = (char)avrRele.dsplbuf[coor + j];
									}
									tdZnach.html(avrRele.getStrImp(2, new String(tmp)));
								}
								else {
									tdZnach.html("offline");
								}
								break;
							case 0x08:
								tdName.html("<button class='reset' href='reset_res_rz_1'>R</button><span class='text'>Реле 1, 2</span>");
								if(avrRele.online) {
									int coor = (i % 2) + i * 8;
									tdZnach.text(String.format("%s %s", avrRele.dsplbuf[coor + 2] == 10 ? "on " : "off", avrRele.dsplbuf[coor + 6] == 10 ? "on " : "off"));
								}
								else {
									tdZnach.text("offline");
								}
								break;
							case 0x09:
								tdName.html("<button class='reset' href='reset_res_rz_2'>R</button><span class='text'>Реле 3, 4</span>");
								if(avrRele.online) {
									int coor = (i % 2) + i * 8;
									tdZnach.text(String.format("%s %s", avrRele.dsplbuf[coor + 2] == 10 ? "on " : "off", avrRele.dsplbuf[coor + 6] == 10 ? "on " : "off"));
								}
								else {
									tdZnach.text("offline");
								}
								break;
							case 0x0a:
								tdName.html("<button class='reset' href='reset_res_rz_3'>R</button><span class='text'>Реле 5, 6</span>");
								if(avrRele.online) {
									int coor = (i % 2) + i * 8;
									tdZnach.text(String.format("%s %s", avrRele.dsplbuf[coor + 2] == 10 ? "on " : "off", avrRele.dsplbuf[coor + 6] == 10 ? "on " : "off"));
								}
								else {
									tdZnach.text("offline");
								}
								break;
							case 0x0b:
								tdName.html("<button class='reset' href='reset_all' style='display: none;'>R</button><span class='text'>WiFi</span>");
								if(avrRele.online) {
									tdZnach.text("Connect");
								}
								else
									tdZnach.text("offline");
								break;
							default:
								tdName.text("error" + avrRele.arr_golovne_menu[i]);
								tdZnach.text("err");
								break;
						}
					}
				}
				Element poleOpysu = doc.selectFirst("div#poleopysu");
				poleOpysu.selectFirst("#sn_mega").html("SN ATMEGA: " + avrRele.serialNumberMega);
				poleOpysu.selectFirst("#sn_esp").html("SN ESP8266: " + avrRele.serialNumberEsp);

				poleOpysu.selectFirst("#logs").html("<a href='view_logs'>View logs</a>");
		/* 		Element rezhym_tmr = doc.selectFirst("#rezhym_tmr");
				rezhym_tmr.select("option[selected]").removeAttr("selected"); // Зняти виділення з попереднього
				rezhym_tmr.select("option[value='" + (int)avrRele.rezhym_tmr + "']").attr("selected", "true");
				rezhym_tmr.attr("disabled", "disabled");
			*/		
				//poleOpysu.selectFirst("select#rezhym_tmr").val("" + (int)avrRele.rezhym_tmr);
				//poleOpysu.selectFirst("select#rezhym_tmr").val("3");
				Elements rr = doc.select("tbody.blockrele");
				for(Element rrr : rr) {
					String type = rrr.select("tr > th[rowspan='4']").first().text();
					int indx = Character.getNumericValue(type.toCharArray()[3]) - 1;
					if(avrRele.getWorkElem(type, indx)) {
						char[] tmp = avrRele.getEnbl(type, indx);
						Elements rowTds = rrr.select("tr").get(0).select("td");
						for(int i = 0; i < 16; i++) {
							//rowTds.get(i).text(String.valueOf(tmp[i]));
							rowTds.get(i).html("<input type='text' class='kl_1_znak' maxlength='1' value='" + String.valueOf(tmp[i]) + "'/>");
						}
						rrr.select("tr").get(0).select("th").get(2).text(avrRele.getSetString(type, indx));
						
						tmp = avrRele.getClck(type, indx);
						rowTds = rrr.select("tr").get(1).select("td");
						for(int i = 0; i < 16; i++) {
							rowTds.get(i).html("<input type='text' class='kl_1_znak' maxlength='1' value='" + String.valueOf(tmp[i]) + "'/>");
						}
						rrr.select("tr").get(1).select("th").get(1).text("clk in: " + ((avrRele.getOrClock(type, indx) == 1) ? "OR" : "AND"));
						
						tmp = avrRele.getOut1(type, indx);
						rowTds = rrr.select("tr").get(2).select("td");
						for(int i = 0; i < 16; i++) {
							rowTds.get(i).html("<input type='text' class='kl_1_znak' maxlength='1' value='" + String.valueOf(tmp[i]) + "'/>");
						}
						rrr.select("tr").get(2).select("th").get(1).text("autores.: " + ((float)(avrRele.getAutores(type, indx) * 8) / 25)  + "s");
						
						tmp = avrRele.getOut2(type, indx);
						rowTds = rrr.select("tr").get(3).select("td");
						for(int i = 0; i < 16; i++) {
							rowTds.get(i).html("<input type='text' class='kl_1_znak' maxlength='1' value='" + String.valueOf(tmp[i]) + "'/>");
						}
						rrr.select("tr").get(3).select("th").get(1).text("тремт.: " + ((float)(avrRele.getTrmtn(type, indx) * 8) / 25)  + "s");
						
					}
					else {
						rrr.html("");
					}
				}
				
				KeyManager.setGadget(httpRequest.X_Session_ID, httpRequest.clientAddress, avrRele.serialNumberMega);
				byte[] htmlContent = doc.outerHtml().getBytes(StandardCharsets.UTF_8);
				return new HTTPResponse(htmlContent.length, htmlContent, "get_det_gad.html");
			}
			else {
				return new HTTPResponse(503);
			}
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return new HTTPResponse(404);
		
	}

	private static HTTPResponse viewLogs(HTTPRequest httpRequest) {
		int userID = httpRequest.userID;
		long sn_mega = KeyManager.getGadget(httpRequest.X_Session_ID, httpRequest.clientAddress);
		if(userID == 0 || sn_mega == 0) {
			return new HTTPResponse(503);
		}

		StringBuilder htmlContent = new StringBuilder();
		htmlContent.append("<html><head>");
		htmlContent.append("<meta charset='UTF-8'>");
		htmlContent.append("<title>Device Logs - ").append(sn_mega).append("</title>");
		htmlContent.append("<style>");
		htmlContent.append("body { font-family: Arial, sans-serif; margin: 14px; line-height: 1.6; }");
		htmlContent.append("h1 { color: #333; }");
		htmlContent.append("p { margin: 5px 0; }");
		htmlContent.append("</style>");
		htmlContent.append("</head><body>");
		htmlContent.append("<h1>Logs for device: ");
		htmlContent.append(sn_mega);
		htmlContent.append("</h1>");
		htmlContent.append("<div style='border: 1px solid #ddd; padding: 10px; border-radius: 5px;'>");
		htmlContent.append(LogFiles.getLog(sn_mega));
		htmlContent.append("</div>");
		htmlContent.append("<p><a href='avr_relays_control.html'>Back to device</a></p>");
		htmlContent.append("</body></html>");
		
		byte[] responseBytes = htmlContent.toString().getBytes(StandardCharsets.UTF_8);
		return new HTTPResponse(responseBytes.length, responseBytes, "AVR_Log_File.html");
	}
}

