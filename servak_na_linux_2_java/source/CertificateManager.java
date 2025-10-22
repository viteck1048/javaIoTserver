import org.shredzone.acme4j.*;
import org.shredzone.acme4j.challenge.Http01Challenge;
import org.shredzone.acme4j.exception.AcmeException;
import org.shredzone.acme4j.util.CSRBuilder;
import org.shredzone.acme4j.util.KeyPairUtils;

import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateEncodingException;
import java.util.Base64;
import java.util.Optional;
import java.net.URL;

public class CertificateManager {
	private static final String DOMAIN = Configs.getParam("host");
	private static final String ACCOUNT_KEY_FILE = Configs.getParam("acme_account_key_file");
	private static final String DOMAIN_KEY_FILE = Configs.getParam("acme_domain_key_file");
	private static final String CERTIFICATE_FILE = Configs.getParam("acme_certificate_file");
	private static final String KEYSTORE_FILE = Configs.getParam("keyStoreFile");
	private static final String KEYSTORE_PASSWORD = Configs.getParam("keyStorePassword");
	private static final String ACME_SERVER_URL = Configs.getParam("acme_server_url");

	private KeyPair accountKey;
	private KeyPair domainKey;
	private Account account;
	private Session session;
	
	
	public CertificateManager() throws IOException, AcmeException {
		// Завантажуємо або створюємо ключі
		accountKey = loadOrCreateKeyPair(ACCOUNT_KEY_FILE);
		domainKey = loadOrCreateKeyPair(DOMAIN_KEY_FILE);

		// Створюємо сесію ACME
		session = new Session(ACME_SERVER_URL);
	/*	if(false) {
			// Спроба завантажити існуючий обліковий запис
			Login login = new Login(new URL("https://acme-v02.api.letsencrypt.org/acme/acct/..."), accountKey, session);
			account = login.getAccount();

		} else {*/
			// Якщо обліковий запис не знайдено, створюємо новий
			System.out.println("Обліковий запис не знайдено, створюємо новий...");
			Login newLogin = new AccountBuilder()
					.addContact(Configs.getParam("acme_contact"))
					.agreeToTermsOfService()
					.useKeyPair(accountKey) // Встановлюємо ключову пару
					.createLogin(session);
			account = newLogin.getAccount();
			System.out.println("Account URL NEW: " + account.getLocation());
	//	}

		// Логування URL облікового запису
		System.out.println("Account URL: " + account.getLocation());
	}


	private KeyPair loadOrCreateKeyPair(String filename) throws IOException {
		File file = new File(filename);
		if (file.exists()) {
			try (FileReader reader = new FileReader(file)) {
				return KeyPairUtils.readKeyPair(reader);
			}
		} else {
			KeyPair keyPair = KeyPairUtils.createKeyPair(2048);
			try (FileWriter writer = new FileWriter(file)) {
				System.out.println("Файл PEM буде створено: " + filename);
				KeyPairUtils.writeKeyPair(keyPair, writer);
			}
			return keyPair;
		}
	}

	public void requestCertificate() throws AcmeException, IOException, CertificateEncodingException, KeyStoreException, NoSuchAlgorithmException {
		// Створюємо замовлення на сертифікат
		Order order = account.newOrder().domains(DOMAIN).create();
		
		System.out.println("Замовлення сертифікату створено. Статус: " + order.getStatus());

		// Отримуємо HTTP-01 виклик
		Optional<Http01Challenge> challengeOpt = order.getAuthorizations().get(0).findChallenge(Http01Challenge.TYPE);
		if (challengeOpt.isEmpty()) {
			throw new AcmeException("No HTTP challenge found");
		}
		Http01Challenge challenge = challengeOpt.get();

		// Отримуємо токен та ключ авторизації
		String token = challenge.getToken();
		String keyAuthorization = challenge.getAuthorization();

		// Шлях до файлу
		File challengeFile = new File(Configs.getParam("www80_directory") + Configs.getParam("acme_challenge_path") + token);

		// Переконайтеся, що потрібні директорії існують
		challengeFile.getParentFile().mkdirs();

		try (FileWriter writer = new FileWriter(challengeFile)) {
			writer.write(keyAuthorization);
			System.out.println("Файл для токену створено: " + challengeFile.getAbsolutePath());
		} catch (IOException e) {
			throw new AcmeException("Не вдалося створити файл для токену", e);
		}
		// TODO: Тут потрібно налаштувати ваш веб-сервер для відповіді на виклик
		// Наприклад, створити файл .well-known/acme-challenge/{token} з вмістом {keyAuthorization}

		// Повідомляємо Let's Encrypt, що виклик готовий
		challenge.trigger();
	   

		// Чекаємо завершення виклику
		try {
			int attempts = 10;
			while (order.getStatus() != Status.VALID && order.getStatus() != Status.READY && attempts > 0) {
				Thread.sleep(5000L);
				order = account.newOrder().domains(DOMAIN).create();
				//order.update();
				System.out.println("Статус: " + order.getStatus());
				attempts--;
			}
		} catch (InterruptedException ex) {
			throw new AcmeException("Challenge failed", ex);
		}

		if (order.getStatus() != Status.VALID && order.getStatus() != Status.READY) {
			throw new AcmeException("Challenge failed");
		}

		// Створюємо CSR
		CSRBuilder csrb = new CSRBuilder();
		csrb.addDomain(DOMAIN);
		csrb.sign(domainKey);

		// Завершуємо замовлення
		order.execute(csrb.getEncoded());


		try {
			int attempts = 10;
			while (order.getStatus() != Status.VALID /*&& order.getStatus() != Status.READY*/ && attempts > 0) {
				Thread.sleep(5000L);
				order = account.newOrder().domains(DOMAIN).create();
				//order.update();
				System.out.println("Статус: " + order.getStatus());
				attempts--;
			}
		} catch (InterruptedException ex) {
			throw new AcmeException("Challenge failed", ex);
		}

		if (order.getStatus() != Status.VALID /*&& order.getStatus() != Status.READY*/) {
			throw new AcmeException("Challenge failed");
		}

  /*      // Створюємо CSR
		CSRBuilder csrb = new CSRBuilder();
		csrb.addDomain(DOMAIN);
		csrb.sign(domainKey);

		// Завершуємо замовлення
		order.execute(csrb.getEncoded());
*/
		// Отримуємо сертифікат
		org.shredzone.acme4j.Certificate acmeCert = order.getCertificate();
		X509Certificate cert = acmeCert.getCertificate();

		// Зберігаємо сертифікат
		try (FileWriter writer = new FileWriter(CERTIFICATE_FILE)) {
			writer.write("-----BEGIN CERTIFICATE-----\n");
			writer.write(Base64.getEncoder().encodeToString(cert.getEncoded()));
			writer.write("\n-----END CERTIFICATE-----\n");
		}

		// Створюємо PKCS12 keystore
		KeyStore ks = KeyStore.getInstance("PKCS12");
		try {
			ks.load(null, null);
		} catch (IOException | NoSuchAlgorithmException | CertificateException e) {
			e.printStackTrace();
		}

		ks.setKeyEntry("alias", domainKey.getPrivate(), KEYSTORE_PASSWORD.toCharArray(),
				new java.security.cert.Certificate[]{cert});

		try (FileOutputStream fos = new FileOutputStream(KEYSTORE_FILE)) {
			ks.store(fos, KEYSTORE_PASSWORD.toCharArray());
		} catch (IOException | NoSuchAlgorithmException | CertificateException e) {
			e.printStackTrace();
		}

	}

	public X509Certificate getCertificate() throws IOException, KeyStoreException, NoSuchAlgorithmException, CertificateException {
		KeyStore ks = KeyStore.getInstance("PKCS12");
		try (FileInputStream fis = new FileInputStream(KEYSTORE_FILE)) {
			ks.load(fis, KEYSTORE_PASSWORD.toCharArray());
			return (X509Certificate) ks.getCertificate("alias");
		}
	}

	public KeyPair getDomainKeyPair() {
		return domainKey;
	}
}

