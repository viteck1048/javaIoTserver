
import java.io.IOException;
import javax.net.ssl.*;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.Duration;
import java.util.Date;
import java.io.*;

public class Servak {
	
	private static CertificateManager certificateManager;
	
	public static void main(String[] args) {
		// Ініціалізація системи логування
		MyOutClass.init();
		// Встановлюємо вивід тільки в консоль за замовчуванням
		MyOutClass.setOutput(true, false);
		
		String configfile = "config.ini";
		for(int i = 0; i < args.length; i++) {
			if(args[i].equals("-c")) {
				if(i + 1 < args.length) {
					String[] dotSplit = args[i + 1].split("\\.");
					//крутота
					if(dotSplit.length == 2 && dotSplit[1].equals("ini")) {
						configfile = args[i + 1].trim();
					}
					else {
						System.out.println("Invalid config file name: " + args[i + 1]);
					}
				}
			}
		}
		
		Configs.init(configfile);
        // ensure DB schema exists
        DatabaseHelper.initializeTables();

		for(int i = 0; i < args.length; i++) {
			if(args[i].equals("-p")) {
				for(int j = i + 1; j < args.length; j++) {
					String[] parts = args[j].split("=", 2);
					if(parts.length >= 2) {
						Configs.priorityParam(parts[0].trim(), parts[1].trim());
					}
					else
						break;
				}
			}
		}

		if(Configs.validate() == false) {
			System.out.println("Invalid configs");
			return;
		}

		// Explicit initialization of Firewall components
		System.out.println("Initializing Firewall components...");
		initializeFirewallComponents();

		if(Configs.getDefine("logToFile")) {
			MyOutClass.setPrintToFile(Configs.getBoolean("logToFile"));
			if(Configs.getDefine("maxLogFileSize"))
				MyOutClass.setMaxFileSize(Configs.getLong("maxLogFileSize"));
			if(Configs.getDefine("maxLogBackupIndex"))
				MyOutClass.setMaxBackupIndex(Configs.getInt("maxLogBackupIndex"));
		}
		if(Configs.getDefine("logToConsole"))
			MyOutClass.setPrintToConsole(Configs.getBoolean("logToConsole"));
		
		new Thread(new ServerTask(80)).start();
		
		try{
				// Налаштування SSL
			String keyStoreFile = Configs.getParam("keyStoreFile");
			char[] keyStorePassword = Configs.getParam("keyStorePassword").toCharArray();
			KeyStore ks = KeyStore.getInstance("PKCS12");
			
			java.io.File keyStore = new java.io.File(keyStoreFile);
			if (!keyStore.exists()) {
				System.out.println("Файл сховища ключів не знайдено. Створюємо новий...");
				ks.load(null, keyStorePassword); // Ініціалізуємо пусте сховище
				try (java.io.FileOutputStream fos = new java.io.FileOutputStream(keyStore)) {
					ks.store(fos, keyStorePassword);
				}
				System.out.println("Новий файл сховища ключів створено: " + keyStoreFile);
			} else {
				try (java.io.FileInputStream fis = new java.io.FileInputStream(keyStore)) {
					ks.load(fis, keyStorePassword);
				}
			}
			
			// Отримання сертифіката
			X509Certificate certificate = (X509Certificate) ks.getCertificate(Configs.getParam("keyStoreAlias"));
			if (certificate == null) {
				System.out.println("Сертифікат не знайдено у Keystore! Отримання нового...");
				certificateManager = new CertificateManager();
				certificateManager.requestCertificate();
			}
			else {
				// Отримуємо дату видачі (початку дії) сертифіката
				Date notBefore = certificate.getNotBefore();
				System.out.println("Дата видачі сертифіката: " + notBefore);

				// Перевірка, чи пройшло більше 200 годин з дати видачі
				Instant now = Instant.now();
				Instant issuedDate = notBefore.toInstant();
				Duration duration = Duration.between(issuedDate, now);
				long hoursPassed = duration.toHours();

				System.out.println("Пройшло годин з моменту видачі: " + hoursPassed);

				if (hoursPassed > 200 && Configs.getBoolean("acme")) {
					System.out.println("Сертифікат застарілий. Отримання нового...");
					// Викликаємо метод для оновлення сертифіката
					certificateManager = new CertificateManager();
					certificateManager.requestCertificate();

				} else {
					System.out.println("Сертифікат ще валідний.");
				}
			}		
		} catch (Exception e) {
			e.printStackTrace();
		}
		
//		new Thread(new ServerTask(8081)).start();
		
		
		if(Configs.getBoolean("https_run"))
			new Thread(new SimpleHTTPSServer(443)).start();
		if(Configs.getBoolean("avr") && (Configs.getInt("avr_port") != 80 || Configs.getInt("avr_port") != 443))
			new Thread(new ServerTask(Configs.getInt("avr_port"))).start();

		// Запускаємо агент очистки кешу
		CacheAgent cacheAgentInstance = new CacheAgent();
		cacheAgentInstance.initFromConfig();
		Thread cacheAgent = new Thread(cacheAgentInstance);
		cacheAgent.setDaemon(true);
		cacheAgent.start();
	}

	/**
	 * Explicitly initialize Firewall components
	 */
	private static void initializeFirewallComponents() {
		System.out.println("FIREWALL IP: Explicit initialization...");
		// Access FirewallIP class to trigger static initialization
		try {
			// Force class loading and static initialization
			FirewallIP.initialize();
			System.out.println("FIREWALL IP: Initialization completed");
		} catch (Exception e) {
			System.err.println("Error initializing FirewallIP: " + e.getMessage());
		}

		System.out.println("FIREWALL PHP: Explicit initialization...");
		// Access FirewallPHP class to trigger static initialization
		try {
			// Force class loading and static initialization
			FirewallPHP.initialize();
			System.out.println("FIREWALL PHP: Initialization completed");
		} catch (Exception e) {
			System.err.println("Error initializing FirewallPHP: " + e.getMessage());
		}

		System.out.println("Firewall components initialized successfully");
	}
}
