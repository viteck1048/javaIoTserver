import javax.net.ssl.*;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.Duration;
import java.util.Date;

public class SimpleHTTPSServer implements Runnable {
	private int port;
	private CertificateManager certificateManager;

	public SimpleHTTPSServer(int port) {
		this.port = port;
		try {
//			this.certificateManager = new CertificateManager();
//			this.certificateManager.requestCertificate();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	//private static final int PORT = 443;
	private static final String KEYSTORE_FILE = Configs.getParam("keyStoreFile");
	private static final String KEYSTORE_PASSWORD = Configs.getParam("keyStorePassword");

	public void run() {
		try {
			// Налаштування SSL
			SSLContext context = SSLContext.getInstance("TLS");
			KeyStore ks = KeyStore.getInstance("PKCS12");
			ks.load(new java.io.FileInputStream(KEYSTORE_FILE), KEYSTORE_PASSWORD.toCharArray());
			
			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(ks, KEYSTORE_PASSWORD.toCharArray());
			
			context.init(kmf.getKeyManagers(), null, null);
			
			// Налаштування SSL-серверного сокету
			SSLServerSocketFactory ssf = context.getServerSocketFactory();
			SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(port);
			System.out.println("HTTPS Server is running on port " + port);
			
			while (true) {
				SSLSocket socket = (SSLSocket) serverSocket.accept();
				new ClientHandler(socket, port).start();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}