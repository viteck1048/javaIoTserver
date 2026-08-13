import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;



public class ServerTask implements Runnable {
	private int port;
	private WorkerPool workerPool;

	public ServerTask(int port, WorkerPool workerPool) {
		this.port = port;
		this.workerPool = workerPool;
	}

	@Override
	public void run() {
		try (ServerSocket serverSocket = new ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"))) {
			System.out.println("Server is listening on port " + port);
			boolean authoriz_whithout_https = Configs.getBoolean("authoriz_whithout_https");
			boolean usePool = Configs.getBoolean("workerpool");
			while (true) {
				try {
					Socket socket = serverSocket.accept();
//					System.out.print("New client connected on port " + port + ";\t");
					if(FirewallIP.quickBan(socket.getInetAddress())) {
						socket.close();
						continue;
					}
					if(!usePool) {
						new ClientHandler(socket, port, authoriz_whithout_https).start();
					}
					else {
						workerPool.submit(socket, port, authoriz_whithout_https);
					}
				} catch (IOException e) {
					System.out.println("Exception caught when trying to listen on port " + port + " or listening for a connection");
					System.out.println(e.getMessage());
				}
			}
		} catch (IOException e) {
			System.out.println("Could not listen on port " + port);
			System.out.println(e.getMessage());
		}
	}
}
