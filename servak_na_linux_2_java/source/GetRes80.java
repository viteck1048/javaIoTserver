import java.nio.file.Path;
import java.nio.file.Paths;
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
	 * Сканує директорію www80 і повертає список файлів у форматі JSON
	 * 
	 * @return HTTPResponse з JSON-даними про файли
	 */
	public static HTTPResponse scanDirectory(HTTPRequest httpRequest) {
		try {
			// Отримуємо список файлів з кешу директорії www80
			String www80Path = Configs.getParam("www80_directory");
			List<String> files = FileCacheManager.scanDir(www80Path);

			// Фільтруємо тільки .html файли
			files = files.stream().filter(filename -> filename.toLowerCase().endsWith(".html")).collect(Collectors.toList());

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
				return new HTTPResponse(jsonString.getBytes().length, jsonString.getBytes(), "scan_www80_directory.app-json", true);
			return new HTTPResponse(jsonString.getBytes().length, jsonString.getBytes(), "scan_www80_directory.app-json");
		} catch (Exception e) {
			// В разі помилки повертаємо порожній JSON
			String jsonString = "{\"files\":[]}";
			if(httpRequest.method.compareTo("HEAD") == 0)
				return new HTTPResponse(jsonString.getBytes().length, jsonString.getBytes(), "scan_www80_directory.app-json", true);
			return new HTTPResponse(jsonString.getBytes().length, jsonString.getBytes(), "scan_www80_directory.app-json");
		}
	}
}
