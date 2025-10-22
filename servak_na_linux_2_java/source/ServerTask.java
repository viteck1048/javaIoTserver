import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;



public class ServerTask implements Runnable {
	private int port;

	public ServerTask(int port) {
		this.port = port;
	}

	@Override
	public void run() {
		try (ServerSocket serverSocket = new ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"))) {
			System.out.println("Server is listening on port " + port);
			while (true) {
				try {
					Socket socket = serverSocket.accept();
//					System.out.print("New client connected on port " + port + ";\t");
					new ClientHandler(socket, port).start();
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
