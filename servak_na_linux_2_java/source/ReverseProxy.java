import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.EOFException;
import java.net.Socket;
//import java.nio.charset.StandardCharsets;
import java.net.SocketTimeoutException;

/**
 * Клас для обробки реверс-проксі запитів
 */
public final class ReverseProxy {
	
	private final class FastCGIRecordType {
		public static final byte FCGI_BEGIN_REQUEST = 1;
		public static final byte FCGI_ABORT_REQUEST = 2;
		public static final byte FCGI_END_REQUEST = 3;
		public static final byte FCGI_PARAMS = 4;
		public static final byte FCGI_STDIN = 5;
		public static final byte FCGI_STDOUT = 6;
		public static final byte FCGI_STDERR = 7;
		public static final byte FCGI_DATA = 8;
		public static final byte FCGI_GET_VALUES = 9;
		public static final byte FCGI_GET_VALUES_RESULT = 10;
		public static final byte FCGI_UNKNOWN_TYPE = 11;
	}

	private ReverseProxy() {
		throw new UnsupportedOperationException("Utility class");
	}
	
	/**
	 * Обробляє запити з реверсом (OLD_SERVAK або RELAYS_SERVER)
	 * 
	 * @param httpRequest Запит з реверсом
	 * @return HTTPResponse з результатом обробки
	 */
	public static HTTPResponse handleReverseRequest(HTTPRequest httpRequest) {
		if (httpRequest.revers == HTTPRequest.ReversType.NO_REVERSE) {
			System.out.println("ReversProxy. err: NO_REVERSE");
			for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
			    System.err.println(ste);
			}
			return new HTTPResponse(500);
		}
		
		int userID = httpRequest.userID;
		
		if (userID == 0 && httpRequest.revers != HTTPRequest.ReversType.BANRESPONSE) {
			httpRequest.revers = HTTPRequest.ReversType.NO_REVERSE;
			System.err.println("ReversProxy. err: userID == 0; !banresponse");
			for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
			    System.err.println(ste);
			}
			return new HTTPResponse(500);
		}
		
		Socket proxySocket = null;
		try {
			proxySocket = new Socket();
			// Встановлюємо опції сокета
			proxySocket.setReuseAddress(true);
			proxySocket.setSendBufferSize(4 * 1024 * 1024); // 4MB buffer
			proxySocket.setReceiveBufferSize(4 * 1024 * 1024); // 4MB buffer
			proxySocket.setKeepAlive(true);
			proxySocket.setTcpNoDelay(true);
			proxySocket.setSoTimeout(2000);
			switch (httpRequest.revers) {
				case BANRESPONSE:
					if(!Configs.getBoolean("ban_response"))
						return new HTTPResponse(503);
					proxySocket.connect(new java.net.InetSocketAddress(Configs.getParam("ip_ban_response_server"), Configs.getInt("port_ban_response_server")), 2000);
					break;
				case PHP_FPM:
					//System.out.println("PHP_FPM ip: " + Configs.getParam("ip_php_fpm_server") + ", port: " + Configs.getInt("port_php_fpm_server"));
					if(!Configs.getBoolean("php_fpm"))
						return new HTTPResponse(503);
					proxySocket.connect(new java.net.InetSocketAddress(Configs.getParam("ip_php_fpm_server"), Configs.getInt("port_php_fpm_server")), 2000);
					break;
				case OLD_SERVAK:
					if(!Configs.getBoolean("liraCalc"))
						return new HTTPResponse(503);
					proxySocket.connect(new java.net.InetSocketAddress(Configs.getParam("ip_liraCalc_server"), Configs.getInt("port_liraCalc_server")), 2000);
					break;
				case RELAYS_SERVER:
					if(!Configs.getBoolean("esp"))
						return new HTTPResponse(503);
					proxySocket.connect(new java.net.InetSocketAddress(Configs.getParam("ip_relay_server"), Configs.getInt("port_relay_server")), 2000);
					if (httpRequest.path.startsWith("/relay_servak/") && httpRequest.method.compareTo("GET") == 0) {
					// Додаємо userID до першого рядка header
						String[] headerLines = httpRequest.header.split("\r\n", 2);
						if (headerLines.length > 0) {
							String firstLine = headerLines[0];
							int firstSpace = firstLine.indexOf(' ');
							int secondSpace = firstLine.indexOf(' ', firstSpace + 1);
							if (firstSpace != -1 && secondSpace != -1) {
								String method = firstLine.substring(0, firstSpace + 1);
								String path = firstLine.substring(firstSpace + 1, secondSpace);
								String rest = firstLine.substring(secondSpace);
								if (path.contains("?")) {
									path += "&userID=" + userID;
								} else {
									path += "?userID=" + userID;
								}
								firstLine = method + path + rest;
							}
							httpRequest.header = firstLine + "\r\n" + (headerLines.length > 1 ? headerLines[1] : "");
						}
					}
					else if(httpRequest.path.startsWith("/relay_servak") && httpRequest.Content_Type.compareTo("application/x-www-form-urlencoded") == 0 && (httpRequest.method.compareTo("POST") == 0 || httpRequest.method.compareTo("PUT") == 0 || httpRequest.method.compareTo("DELETE") == 0)) {
						int index = httpRequest.body.indexOf("&");
						if(index != -1)
							httpRequest.body = httpRequest.body.substring(0, index) + "&userID=" + userID + httpRequest.body.substring(index);
						else
							httpRequest.body += "&userID=" + userID;
						// Оновлюємо content-length
						int contentLength = httpRequest.body.length();
						httpRequest.header = httpRequest.header.replaceFirst("Content-Length: \\d+", "Content-Length: " + contentLength);
					}
					break;
				default:
					throw new IOException("Unknown reverse type: " + httpRequest.revers);
			}
		
			// Відправляємо запит на проксі-сервер
			if(httpRequest.revers != HTTPRequest.ReversType.PHP_FPM) {
				proxySocket.getOutputStream().write(httpRequest.header.getBytes());//System.out.print("***\r" + httpRequest.header + "\r\n***");
				proxySocket.getOutputStream().write("\r\n".getBytes());
							// Відправляємо body частинами
				byte[] bodyBytes = httpRequest.body.getBytes();
				int bytesSent = 0;
				int chunkSize = 8192; // 8KB chunks
				
				while (bytesSent < bodyBytes.length) {
					int bytesToWrite = Math.min(chunkSize, bodyBytes.length - bytesSent);
					proxySocket.getOutputStream().write(bodyBytes, bytesSent, bytesToWrite);
					bytesSent += bytesToWrite;
				}

				proxySocket.getOutputStream().flush();
				
				// Отримуємо відповідь
				InputStream responseFromOldServer = new java.io.BufferedInputStream(proxySocket.getInputStream(), 4 * 1024 * 1024); // 4MB buffer
				
				byte[] buffer = new byte[8196];
				int bytesRead;
				int bytesReadAll = 0;
				int contentLength = 0;
				int headerEndIndex = 0;
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				while ((bytesRead = responseFromOldServer.read(buffer)) != -1) {
					baos.write(buffer, 0, bytesRead);
					if(bytesReadAll == 0) {
						String raw = new String(buffer);
						headerEndIndex = raw.indexOf("\r\n\r\n") + 4;
						contentLength = raw.contains("Content-Length:") ? Integer.parseInt(raw.split("Content-Length: ")[1].split("\\r?\\n")[0].trim()) : 0;
					}
					bytesReadAll += bytesRead;
					if(contentLength + headerEndIndex <= bytesReadAll)
						break;
				}
				byte[] buf2 = baos.toByteArray();
				if(httpRequest.revers == HTTPRequest.ReversType.BANRESPONSE)
					return new HTTPResponse(null, buf2, "revers to ban response server");
				else
					return new HTTPResponse(null, buf2, "revers to old server");
			}
			else {
				int requestID = 1;
				sendPHPFPMRequest(proxySocket, null, null, FastCGIRecordType.FCGI_BEGIN_REQUEST, requestID);
				for(int i = 0; i < httpRequest.getPhpQueryLength(); i++) {
					sendPHPFPMRequest(proxySocket, httpRequest.getPhpParam(i), httpRequest.getPhpZnach(i), FastCGIRecordType.FCGI_PARAMS, requestID);
				}
				sendPHPFPMRequest(proxySocket, null, null, FastCGIRecordType.FCGI_PARAMS, requestID);
				if(httpRequest.body.length() > 0 && httpRequest.method.compareTo("POST") == 0 || httpRequest.method.compareTo("PUT") == 0 || httpRequest.method.compareTo("DELETE") == 0) {
					int contentBodyLength = httpRequest.body.length();
					int index = 0;
					while(contentBodyLength - index > 65535) {
						sendPHPFPMRequest(proxySocket, null, httpRequest.body.substring(index, index + 65535), FastCGIRecordType.FCGI_STDIN, requestID);
						index += 65535;
					}
					sendPHPFPMRequest(proxySocket, null, httpRequest.body.substring(index), FastCGIRecordType.FCGI_STDIN, requestID);
				}
				sendPHPFPMRequest(proxySocket, null, null, FastCGIRecordType.FCGI_STDIN, requestID);

				// Отримуємо відповідь
				InputStream responseFromOldServer = new java.io.BufferedInputStream(proxySocket.getInputStream(), 4 * 1024 * 1024);
				byte[] responseHeader = new byte[8];
				byte[] buffer = new byte[65535];
				byte[] paddingBuffer = new byte[256];
				int bytesRead;
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				while(true) {
					bytesRead = 0;
					while (bytesRead < 8) {
						int count = responseFromOldServer.read(responseHeader, bytesRead, 8 - bytesRead);
						if (count == -1) {
							throw new EOFException("Stream ended before reading expected bytes");
						}
						bytesRead += count;
					}
					int paddingLength = responseHeader[6] & 0xFF;
					int contentLength = (((responseHeader[4] & 0xFF) << 8) + (responseHeader[5] & 0xFF));
					switch(responseHeader[1]) {
						case FastCGIRecordType.FCGI_END_REQUEST:
							System.out.println("FCGI_END_REQUEST");
							//System.out.println(new String(baos.toByteArray()));
							return new HTTPResponse(null, baos.toByteArray(), "revers to old server");
						case FastCGIRecordType.FCGI_STDOUT:
							bytesRead = 0;
							while (bytesRead < contentLength) {
								int count = responseFromOldServer.read(buffer, bytesRead, contentLength - bytesRead);
								if (count == -1) {
									throw new EOFException("Stream ended before reading expected bytes");
								}
								bytesRead += count;
							}
							baos.write(buffer, 0, bytesRead);
							bytesRead = 0;
							while (bytesRead < paddingLength) {
								int count = responseFromOldServer.read(paddingBuffer, bytesRead, paddingLength - bytesRead);
								if (count == -1) {
									throw new EOFException("Stream ended before reading expected bytes");
								}
								bytesRead += count;
							}
							break;
						case FastCGIRecordType.FCGI_STDERR:
							while (bytesRead < contentLength) {
								int count = responseFromOldServer.read(buffer, bytesRead, contentLength - bytesRead);
								if (count == -1) {
									throw new EOFException("Stream ended before reading expected bytes");
								}
								bytesRead += count;
							}
							System.out.print("FCGI_STDERR: ");
							System.out.println(new String(buffer, 0, bytesRead));
							bytesRead = 0;
							while (bytesRead < paddingLength) {
								int count = responseFromOldServer.read(paddingBuffer, bytesRead, paddingLength - bytesRead);
								if (count == -1) {
									throw new EOFException("Stream ended before reading expected bytes");
								}
								bytesRead += count;
							}
							break;
						default:
							System.out.println("Unknown FastCGI record type: " + responseHeader[1]);
							break;
					}
				}
				//return new HTTPResponse(500);
			}

		} catch (SocketTimeoutException e) {
			System.out.println("Timeout forwarding request: " + e.getMessage());
			return new HTTPResponse(504);
		} catch (Exception e) {
			System.out.println("Error in reverse proxy: " + e.getMessage());
			return new HTTPResponse(500);
		} finally {
			if (proxySocket != null) {
				try {
					proxySocket.close();
				} catch (IOException e) {
					// Ігноруємо помилки при закритті
				}
			}
		}
	}

	private static void sendPHPFPMRequest(Socket proxySocket, String param, String msg, byte type, int requestID) {
		try {
			byte[] header = new byte[8];
			int paramLength = 0;
			int msgLength = 0;
			short paddingLength = 0;
			if(type == FastCGIRecordType.FCGI_PARAMS &&(param == null ^ msg == null)) {
				type = FastCGIRecordType.FCGI_UNKNOWN_TYPE;
			}
			switch(type) {
				case FastCGIRecordType.FCGI_BEGIN_REQUEST:
					header[0] = 0x01;
					header[1] = type;
					header[2] = (byte)(requestID >> 8);
					header[3] = (byte)(requestID & 0xFF);
					header[4] = 0x00;
					header[5] = 0x08;
					header[6] = 0x00;
					header[7] = 0x00;
					byte[] data = new byte[8];
					data[0] = 0x00;
					data[1] = 0x01;
					data[2] = 0x00;
					data[3] = 0x00;
					data[4] = 0x00;
					data[5] = 0x00;
					data[6] = 0x00;
					data[7] = 0x00;
					proxySocket.getOutputStream().write(header);
					proxySocket.getOutputStream().write(data);
					break;
				case FastCGIRecordType.FCGI_PARAMS:
					int stringLength = 0;
					byte[] paramLen = new byte[4];
					byte[] msgLen = new byte[4];
					if(param != null) {
						paramLength = param.length();
						msgLength = msg.length();
						if(paramLength >= 128) {
							paramLen[0] = (byte)((paramLength >> 24) | 0x80);
							paramLen[1] = (byte)(paramLength >> 16);
							paramLen[2] = (byte)(paramLength >> 8);
							paramLen[3] = (byte)(paramLength & 0xFF);
							stringLength += 4;
						}
						else {
							stringLength++;
						}
						if(msgLength >= 128) {
							msgLen[0] = (byte)((msgLength >> 24) | 0x80);
							msgLen[1] = (byte)(msgLength >> 16);
							msgLen[2] = (byte)(msgLength >> 8);
							msgLen[3] = (byte)(msgLength & 0xFF);
							stringLength += 4;
						}
						else {
							stringLength++;
						}
						stringLength += paramLength + msgLength;
						paddingLength = (short)(stringLength % 8);
						if(paddingLength != 0)
							paddingLength = (short)(8 - paddingLength);
					}
					header[0] = 0x01;
					header[1] = type;
					header[2] = (byte)(requestID >> 8);
					header[3] = (byte)(requestID & 0xFF);
					header[4] = (byte)(stringLength >> 8);
					header[5] = (byte)(stringLength & 0xFF);
					header[6] = (byte)(paddingLength & 0xFF);
					header[7] = 0x00;
					
					proxySocket.getOutputStream().write(header);
					if(stringLength != 0) {
						if(paramLength >= 128) {
							proxySocket.getOutputStream().write(paramLen);
						}
						else {
							proxySocket.getOutputStream().write((byte)(paramLength & 0xFF));
						}
						
						if(msgLength >= 128) {
							proxySocket.getOutputStream().write(msgLen);
						}
						else {
							proxySocket.getOutputStream().write((byte)(msgLength & 0xFF));
						}
						proxySocket.getOutputStream().write(param.getBytes());
						proxySocket.getOutputStream().write(msg.getBytes());
						//System.out.println(param + ": " + msg);
						
						if(paddingLength != 0) {
							byte[] padding = new byte[paddingLength];
							proxySocket.getOutputStream().write(padding);
						}
					}
					
					break;
				case FastCGIRecordType.FCGI_STDIN:
								
					if(msg != null) {
						msgLength = msg.length();
						paddingLength = (short)(msgLength % 8);
						if(paddingLength != 0)
							paddingLength = (short)(8 - paddingLength);
					}
					header[0] = 0x01;
					header[1] = type;
					header[2] = (byte)(requestID >> 8);
					header[3] = (byte)(requestID & 0xFF);
					header[4] = (byte)(msgLength >> 8);
					header[5] = (byte)(msgLength & 0xFF);
					header[6] = (byte)(paddingLength & 0xFF);
					header[7] = 0x00;
					
					proxySocket.getOutputStream().write(header);
					if(msgLength != 0) {
						proxySocket.getOutputStream().write(msg.getBytes());
						if(paddingLength != 0) {
							byte[] padding = new byte[paddingLength];
							proxySocket.getOutputStream().write(padding);
						}
					}
					
					break;
				//case FastCGIRecordType.FCGI_DATA:
				//	break;
				default:
					header[0] = 0x01;
					header[1] = FastCGIRecordType.FCGI_UNKNOWN_TYPE;
					header[2] = (byte)(requestID >> 8);
					header[3] = (byte)(requestID & 0xFF);
					header[4] = 0x00;
					header[5] = 0x00;
					header[6] = 0x00;
					header[7] = 0x00;
					proxySocket.getOutputStream().write(header);
					break;
			}

			proxySocket.getOutputStream().flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

