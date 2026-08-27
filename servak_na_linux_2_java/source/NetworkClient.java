import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

/**
 * Клас для send/recv до зовнішніх ресурсів
 * Підтримує як шифроване (SSL/TLS), так і нешифроване з'єднання
 */
public class NetworkClient {
	private Socket proxySocket = null;
	private InputStream inputStream = null;
	private OutputStream outputStream = null;

	public NetworkClient(String host, int port, boolean useSSL) throws IOException {
		try {
			if (useSSL) {
				SSLContext sslContext = SSLContext.getDefault();
				SSLSocketFactory factory = sslContext.getSocketFactory();
				proxySocket = factory.createSocket(host, port);
			} else {
				proxySocket = new Socket();
				proxySocket.connect(new java.net.InetSocketAddress(host, port), 2000);
			}
		} catch (NoSuchAlgorithmException e) {
			throw new IOException("SSL algorithm not available", e);
		}
		
		// Встановлюємо опції сокета за замовчуванням
		proxySocket.setReuseAddress(true);
		proxySocket.setSendBufferSize(4 * 1024 * 1024); // 4MB buffer
		proxySocket.setReceiveBufferSize(4 * 1024 * 1024); // 4MB buffer
		proxySocket.setKeepAlive(true);
		proxySocket.setTcpNoDelay(true);
		proxySocket.setSoTimeout(2000);
		
		inputStream = new java.io.BufferedInputStream(proxySocket.getInputStream(), 4 * 1024 * 1024);
		outputStream = proxySocket.getOutputStream();
	}
	
	public void setSendBufferSize(int t) throws IOException {
		proxySocket.setSendBufferSize(t);
	}

	public void setReceiveBufferSize(int t) throws IOException {
		proxySocket.setReceiveBufferSize(t);
	}

	public void setKeepAlive(boolean b) throws IOException {
		proxySocket.setKeepAlive(b);
	}

	public void setTcpNoDelay(boolean b) throws IOException {
		proxySocket.setTcpNoDelay(b);
	}

	public void setSoTimeout(int t) throws IOException {
		proxySocket.setSoTimeout(t);
	}

	/**
	 * Закриває з'єднання
	 */
	public void close() {
		if (inputStream != null) {
			try {
				inputStream.close();
			} catch (IOException e) {
				// Ігноруємо помилки при закритті
			}
		}
		if (outputStream != null) {
			try {
				outputStream.close();
			} catch (IOException e) {
				// Ігноруємо помилки при закритті
			}
		}
		if (proxySocket != null) {
			try {
				proxySocket.close();
			} catch (IOException e) {
				// Ігноруємо помилки при закритті
			}
		}
	}

	/**
	 * Відправляє байт-потік на адресу і отримує байт-потік відповіді
	 * 
	 * @param requestHeader Байт-потік заголовка запиту
	 * @param requestBody Байт-потік тіла запиту
	 * @return Байт-потік відповіді
	 */
	public byte[] sendAndReceive(byte[] requestHeader, byte[] requestBody) {
		sendAll(requestHeader, requestBody);
		return recvAll();
	}

	public boolean sendAll(byte[] requestHeader, byte[] requestBody) {
		
		try {
			if (requestHeader != null) {
				outputStream.write(requestHeader);
				outputStream.write("\r\n".getBytes());
			}
			if (requestBody != null) {
				outputStream.write(requestBody);
			}
			outputStream.flush();
			return true;
		} catch (IOException e) {
			System.err.println("NetworkClient: send failed - " + e);
			return false;
		}
	}

	public byte[] recvAll() {
		if (inputStream == null) {
			System.err.println("NetworkClient.recvAll: inputStream is null");
			return null;
		}
		
		try {
			// Читаємо заголовки
			byte[] headers = recvChunkHeaders();
			if (headers == null) {
				return null;
			}
			
			String headerStr = new String(headers);
			ByteArrayOutputStream result = new ByteArrayOutputStream();
			result.write(headers);
			
			// Перевіряємо на Transfer-Encoding: chunked
			if (headerStr.toLowerCase().contains("transfer-encoding:") && headerStr.toLowerCase().contains("chunked")) {
				// Збираємо чанки в єдине боді
				ByteArrayOutputStream body = new ByteArrayOutputStream();
				int len;
				while (true) {
					len = recvChunkLen();
					if (len <= 0) {
						break;
					}
					byte[] buf = recvChunk(len);
					if (buf != null) {
						body.write(buf);
					}
					recvChunkEndEnv();
				}
				recvChunkEndStream();
				
				// Прибираємо згадку про chunk з заголовка
				String newHeaderStr = headerStr.replaceAll("(?i)Transfer-Encoding:\\s*chunked\\r\\n", "");
				newHeaderStr = newHeaderStr.replaceAll("(?i)Transfer-Encoding:\\s*chunked", "");
				
				// Формуємо результат з новим заголовком і зібраним боді
				result = new ByteArrayOutputStream();
				result.write(newHeaderStr.getBytes());
				result.write(body.toByteArray());
			} else {
				// Перевіряємо на Content-Length
				int contentLengthIndex = headerStr.toLowerCase().indexOf("content-length:");
				if (contentLengthIndex != -1) {
					String lengthStr = headerStr.substring(contentLengthIndex + 15).split("\r\n")[0].trim();
					try {
						int contentLength = Integer.parseInt(lengthStr);
						if (contentLength > 0) {
							byte[] body = recvChunk(contentLength);
							if (body != null) {
								result.write(body);
							}
						}
					} catch (NumberFormatException e) {
						// Content-Length не вдалося розпарсити, ігноруємо боді
					}
				}
			}
			
			return result.toByteArray();
		} catch (java.net.SocketTimeoutException e) {
			System.err.println("NetworkClient.recvAll: Socket timeout");
			return null;
		} catch (IOException e) {
			System.err.println("NetworkClient.recvAll: IOException - " + e.getMessage());
			return null;
		}
	}
	
	public boolean sendChunk(byte[] data) {
		try {
			outputStream.write(data);
			outputStream.flush();
			return true;
		} catch (IOException e) {
			System.err.println("NetworkClient: send failed - " + e);
			return false;
		}
	}

	public boolean sendChunk(byte data) {
		try {
			outputStream.write(data);
			return true;
		} catch (IOException e) {
			System.err.println("NetworkClient: send failed - " + e);
			return false;
		}
	}

	public void sendFlush() {
		try {
			outputStream.flush();
		} catch (IOException e) {
			System.err.println("NetworkClient.sendFlush: " + e);
		}
	}

	public byte[] recvChunk(int length) {
		if (inputStream == null) {
			System.err.println("NetworkClient.recvChunk: inputStream is null");
			return null;
		}
		
		try {
			byte[] buffer = new byte[length];
			int bytesRead = 0;
			while (bytesRead < length) {
				int count = inputStream.read(buffer, bytesRead, length - bytesRead);
				if (count == -1) {
					throw new EOFException("Stream ended before reading expected bytes");
				}
				bytesRead += count;
			}
			return buffer;
		} catch (java.net.SocketTimeoutException e) {
			System.err.println("NetworkClient.recvChunk: Socket timeout");
			return null;
		} catch (IOException e) {
			System.err.println("NetworkClient.recvChunk: IOException - " + e.getMessage());
			return null;
		}
	}

	/**
	 * Читає рядок до \r\n
	 * @return байти рядка без \r\n, або null при помилці
	 */
	public byte[] recvChunkStr() {
		if (inputStream == null) {
			System.err.println("NetworkClient.recvChunkStr: inputStream is null");
			return null;
		}
		
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			int prevByte = -1;
			int currentByte;
			
			while ((currentByte = inputStream.read()) != -1) {
				if (prevByte == '\r' && currentByte == '\n') {
					// Видаляємо \r з результату
					byte[] result = baos.toByteArray();
					if (result.length > 0 && result[result.length - 1] == '\r') {
						byte[] trimmed = new byte[result.length - 1];
						System.arraycopy(result, 0, trimmed, 0, result.length - 1);
						return trimmed;
					}
					return result;
				}
				if (prevByte != -1) {
					baos.write(prevByte);
				}
				prevByte = currentByte;
			}
			
			// Потік закінчився без \r\n
			if (prevByte != -1) {
				baos.write(prevByte);
			}
			return baos.toByteArray();
		} catch (java.net.SocketTimeoutException e) {
			System.err.println("NetworkClient.recvChunkStr: Socket timeout");
			return null;
		} catch (IOException e) {
			System.err.println("NetworkClient.recvChunkStr: IOException - " + e.getMessage());
			return null;
		}
	}

	/**
	 * Читає рядок з розміром чанка, парсить шістнадцяткове число
	 * @return розмір чанка, або -1 при помилці
	 */
	public int recvChunkLen() {
		byte[] lineBytes = recvChunkStr();
		if (lineBytes == null) {
			return -1;
		}
		
		String line = new String(lineBytes).trim();
		try {
			return Integer.parseInt(line.split(";")[0], 16);
		} catch (NumberFormatException e) {
			System.err.println("NetworkClient.recvChunkLen: Invalid chunk size: " + line);
			return -1;
		}
	}

	/**
	 * Читає 4 байти і перевіряє на \r\n
	 * @return true якщо це \r\n\, false інакше
	 */
	public boolean recvChunkEndEnv() {
		byte[] marker = recvChunk(2);
		if (marker == null || marker.length != 2) {
			return false;
		}
		return marker[0] == '\r' && marker[1] == '\n';
	}

	/**
	 * Очищає потік від трейлерів, читає до \r\n 
	 */
	public void recvChunkEndStream() {
		if (inputStream == null) {
			return;
		}
		
		try {
			int prevByte = -1;
			int currentByte;
			
			while ((currentByte = inputStream.read()) != -1) {
				if (prevByte == '\r' && currentByte == '\n') {
					// Знайшли кінець трейлерів \r\n
					break;
				}
				prevByte = currentByte;
			}
		} catch (java.net.SocketTimeoutException e) {
			// Таймаут - трейлери можуть бути відсутні
		} catch (IOException e) {
			System.err.println("NetworkClient.recvChunkTrash: IOException - " + e.getMessage());
		}
	}

	/**
	 * Читає HTTP заголовки до \r\n\r\n
	 * @return байти заголовків (включно з \r\n\r\n), або null при помилці
	 */
	public byte[] recvChunkHeaders() {
		if (inputStream == null) {
			System.err.println("NetworkClient.recvChunkHeaders: inputStream is null");
			return null;
		}
		
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			int prevByte = -1;
			int prevPrevByte = -1;
			int prevPrevPrevByte = -1;
			int currentByte;
			
			// Читаємо байт за байтом поки не знайдемо \r\n\r\n
			while ((currentByte = inputStream.read()) != -1) {
				baos.write(currentByte);
				
				// Перевіряємо на \r\n\r\n
				if (prevPrevPrevByte == '\r' && prevPrevByte == '\n' && prevByte == '\r' && currentByte == '\n') {
					break;
				}
				
				prevPrevPrevByte = prevPrevByte;
				prevPrevByte = prevByte;
				prevByte = currentByte;
			}
			
			return baos.toByteArray();
		} catch (java.net.SocketTimeoutException e) {
			System.err.println("NetworkClient.recvChunkHeaders: Socket timeout");
			return null;
		} catch (IOException e) {
			System.err.println("NetworkClient.recvChunkHeaders: IOException - " + e.getMessage());
			return null;
		}
	}

}