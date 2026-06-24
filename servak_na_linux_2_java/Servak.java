
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
		
		if(Configs.getDefine("ai_assist_path_list")) {
			Configs.loadList("ai_assist_path_list");
			System.out.println(" AI Path list loaded, elements: " + Configs.getList("ai_assist_path_list").size());
		}

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
				
				int renewalThresholdHours = 200;
				if(Configs.getDefine("acme_renewal_threshold_hours"))
					renewalThresholdHours = Configs.getInt("acme_renewal_threshold_hours");
				
				if (hoursPassed > renewalThresholdHours && Configs.getBoolean("acme")) {
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

		for(int i = 1; i <= 256; i++) {
			String prxy = "prxy_" + i;
			if(Configs.getBoolean(prxy)) {
				int port = Configs.getInt(prxy + "_listen_port");
				boolean portValid = port > 2000 && port != 80 && port != 443;
				
				// Perevirka na spivpadinnya z avr_port
				if(Configs.getBoolean("avr") && port == Configs.getInt("avr_port"))
					portValid = false;
				
				// Perevirka na spivpadinnya z inshymy prxy portamy
				for(int j = 1; j < i; j++) {
					String prevPrxy = "prxy_" + j;
					if(Configs.getBoolean(prevPrxy) && port == Configs.getInt(prevPrxy + "_listen_port")) {
						portValid = false;
						break;
					}
				}
				
				if(portValid) {
					// Perevirka na nayavnist danyh dlya portu
					
					if(Configs.getDefine(prxy + "_dial_host") && Configs.getInt(prxy + "_dial_port") != 0) {
						if(Configs.getBoolean(prxy + "_listen_ssl")) {
							new Thread(new SimpleHTTPSServer(port)).start();
						}
						
						else{
							new Thread(new ServerTask(port)).start();
						}
					}
				} else {
					System.err.println(prxy + " port " + port + " nevalydnyy chi dublyuetsya");
				}
			}
		}

		// Форвардери MachineTime: окремий потік на кожен увімкнений блок mt_fwd_<i> (аналогічно prxy_)
		String[] mtfRequired = {
			"_dial_host", "_dial_port", "_dial_path", "_user_agent", "_modulID", "_timezone",
			"_private_key_1", "_private_key_2", "_private_key_3", "_private_key_4",
			"_myStaticKeyRequest", "_myStaticKeyResponce"
		};
		for(int i = 1; i <= 256; i++) {
			String mtf = "mt_fwd_" + i;
			if(!Configs.getBoolean(mtf))
				continue;

			boolean valid = true;
			for(String key : mtfRequired) {
				if(!Configs.getDefine(mtf + key)) {
					System.err.println(mtf + " propuscheno: brakuje " + mtf + key);
					valid = false;
					break;
				}
			}
			if(!valid)
				continue;

			System.out.println("Starting MachineTimeForwarder: " + mtf);
			new Thread(new MachineTimeForwarder(mtf)).start();
		}

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
