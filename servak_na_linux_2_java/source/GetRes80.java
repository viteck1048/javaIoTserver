import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class GetRes80 {
	
	private GetRes80() {
		throw new UnsupportedOperationException("Utility class");
	}
	
	/**
	 * Обробляє запити для порту 80 та неавтентифікованих запитів на порт 443
	 * Доступ тільки до файлів у директорії www80_directory
	 */
	public static HTTPResponse getRes80(HTTPRequest httpRequest) {
		String requestedFile = httpRequest.path;
		
		// Для всіх запитів - обробка файлів тільки з www80_directory
		if (requestedFile.equals("/") || requestedFile.equals("/home")) {
			requestedFile = Configs.getParam("homepage");
		}
		
		// Формуємо шлях до файлу в директорії www80_directory
		Path filePath = Paths.get(Configs.getParam("www80_directory") + requestedFile).normalize();
		
		// Перевірка безпеки шляху - файл має бути в директорії www80_directory
		if (filePath.startsWith(Paths.get(Configs.getParam("www80_directory")))) {
			if (filePath.toFile().exists() && !filePath.toFile().isDirectory()) {
				try {
					byte[] fileData = FileCacheManager.getFile(filePath.toString());
					if(fileData != null) {
						if (httpRequest.method.compareTo("HEAD") == 0)
							return new HTTPResponse(fileData.length, fileData, requestedFile, true);
						else
							return new HTTPResponse(fileData.length, fileData, requestedFile);
					} else {
						if(Configs.getBoolean("ban_response") || Configs.getBoolean("Firewall")) {
							httpRequest.revers = HTTPRequest.ReversType.BANRESPONSE;
							return ReverseProxy.handleReverseRequest(httpRequest);
						}
						return new HTTPResponse(500);
					}
				} catch (Exception e) {
					if(Configs.getBoolean("ban_response") || Configs.getBoolean("Firewall")) {
						httpRequest.revers = HTTPRequest.ReversType.BANRESPONSE;
						return ReverseProxy.handleReverseRequest(httpRequest);
					}
					return new HTTPResponse(500);
				}
			}
			else {
				if(Configs.getBoolean("ban_response") || Configs.getBoolean("Firewall")) {
					httpRequest.revers = HTTPRequest.ReversType.BANRESPONSE;
					return ReverseProxy.handleReverseRequest(httpRequest);
				}
				filePath = Paths.get("res/redirect_log_in.html").normalize();
				byte[] fileData = FileCacheManager.getFile(filePath.toString());
				if (fileData != null) {
					try {
						if (httpRequest.method.compareTo("HEAD") == 0)
							return new HTTPResponse(fileData.length, fileData, requestedFile + "_notfound,redirect_log_in.html", true);
						else
							return new HTTPResponse(fileData.length, fileData, requestedFile + "_notfound,redirect_log_in.html");
					} catch (Exception e) {
						return new HTTPResponse(500);
					}
				}
			}
		}
		if(Configs.getBoolean("ban_response") || Configs.getBoolean("Firewall")) {
			httpRequest.revers = HTTPRequest.ReversType.BANRESPONSE;
			return ReverseProxy.handleReverseRequest(httpRequest);
		}
		return new HTTPResponse(404, httpRequest);
	}
	
	/**
	 * Формує меню для головної сторінки: ім'я користувача (порожнє для неавторизованих),
	 * links - ресурси, що відкриваються окремими сторінками,
	 * pages - ресурси, що відкриваються в контейнері поточної сторінки.
	 */
	public static HTTPResponse getLinks(HTTPRequest httpRequest) {
		StringBuilder links = new StringBuilder();
		StringBuilder pages = new StringBuilder();
		String name = "";

		if(httpRequest.userID != 0 && httpRequest.port == 443) {
			String userName = KeyManager.getUserName(httpRequest.userID);
			if(userName != null)
				name = userName;

			if(Configs.getBoolean("avr"))
				appendLink(links, "avr_relays_control.html", "AVR Remote Control");
			if(Configs.getBoolean("esp"))
				appendLink(links, "relay_servak/knopky.html", "ESP Remote Control");
			if(Configs.getBoolean("liraCalc"))
				appendLink(links, "old_servak/", "LiraCalc ConfigEditor");
			if(Configs.getBoolean("download"))
				appendLink(pages, "download/html/index.html", "Downloads");
		}

		if(Configs.getBoolean("mach_time_rev"))
			appendLink(links, Configs.getParam("mach_time_path"), "MachineTime");

		for(String file : scanDirectory())
			appendLink(pages, file, file.substring(0, file.length() - 5).replace('_', ' '));

		String jsonString = "{\"name\":\"" + name + "\",\"links\":[" + links + "],\"pages\":[" + pages + "]}";

		if(httpRequest.method.compareTo("HEAD") == 0)
			return new HTTPResponse(jsonString.getBytes().length, jsonString.getBytes(), "links.app-json", true);
		return new HTTPResponse(jsonString.getBytes().length, jsonString.getBytes(), "links.app-json");
	}

	private static void appendLink(StringBuilder sb, String url, String title) {
		if(sb.length() > 0)
			sb.append(",");
		sb.append("{\"url\":\"").append(url).append("\",\"title\":\"").append(title).append("\"}");
	}

	/**
	 * Повертає html-сторінки з www80_directory, окрім домашньої
	 */
	private static List<String> scanDirectory() {
		try {
			String homepage = Configs.getParam("homepage");
			if(homepage.startsWith("/"))
				homepage = homepage.substring(1);
			final String home = homepage;

			return FileCacheManager.scanDir(Configs.getParam("www80_directory")).stream()
					.filter(filename -> filename.toLowerCase().endsWith(".html") && !filename.equals(home))
					.collect(Collectors.toList());
		} catch (Exception e) {
			return new ArrayList<>();
		}
	}
}
