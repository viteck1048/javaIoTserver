import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Обробник для PHP_FPM типу реверсу
 */
public final class PhpFpmHandler {
	
	private final class FastCGIRecordType {
		public static final byte FCGI_BEGIN_REQUEST = 1;
		//public static final byte FCGI_ABORT_REQUEST = 2;
		public static final byte FCGI_END_REQUEST = 3;
		public static final byte FCGI_PARAMS = 4;
		public static final byte FCGI_STDIN = 5;
		public static final byte FCGI_STDOUT = 6;
		public static final byte FCGI_STDERR = 7;
		//public static final byte FCGI_DATA = 8;
		//public static final byte FCGI_GET_VALUES = 9;
		//public static final byte FCGI_GET_VALUES_RESULT = 10;
		public static final byte FCGI_UNKNOWN_TYPE = 11;
	}

	private PhpFpmHandler() {
		throw new UnsupportedOperationException("Utility class");
	}
	
	// TODO: Додати статичні методи для проміжної обробки запитів і відповідей

	public static HTTPResponse phpFpmResend(HTTPRequest httpRequest) {

		String host = Configs.getParam("ip_php_fpm_server");
		int port = Configs.getInt("port_php_fpm_server");			
		
		NetworkClient nc;
		try {
			nc = new NetworkClient(host, port, false);
		} catch (IOException e) {
			System.out.println("PhpFpmHandler error: " + e.getMessage());
			e.printStackTrace();
			return new HTTPResponse(503);
		}
	
		int requestID = 1;
		sendPHPFPMRequest(nc, null, null, FastCGIRecordType.FCGI_BEGIN_REQUEST, requestID);
		for(int i = 0; i < httpRequest.getPhpQueryLength(); i++) {
			sendPHPFPMRequest(nc, httpRequest.getPhpParam(i), httpRequest.getPhpZnach(i), FastCGIRecordType.FCGI_PARAMS, requestID);
		}
		sendPHPFPMRequest(nc, null, null, FastCGIRecordType.FCGI_PARAMS, requestID);
		if(httpRequest.body.length() > 0 && httpRequest.method.compareTo("POST") == 0 || httpRequest.method.compareTo("PUT") == 0 || httpRequest.method.compareTo("DELETE") == 0) {
			int contentBodyLength = httpRequest.body.length();
			int index = 0;
			while(contentBodyLength - index > 65535) {
				sendPHPFPMRequest(nc, null, httpRequest.body.substring(index, index + 65535), FastCGIRecordType.FCGI_STDIN, requestID);
				index += 65535;
			}
			sendPHPFPMRequest(nc, null, httpRequest.body.substring(index), FastCGIRecordType.FCGI_STDIN, requestID);
		}
		sendPHPFPMRequest(nc, null, null, FastCGIRecordType.FCGI_STDIN, requestID);

			
		byte[] responseHeader;// = new byte[8];
		byte[] buffer;// = new byte[65535];
		//byte[] paddingBuffer = new byte[256];			
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		while(true) {
			responseHeader = nc.recvChunk(8);
			int paddingLength = responseHeader[6] & 0xFF;
			int contentLength = (((responseHeader[4] & 0xFF) << 8) + (responseHeader[5] & 0xFF));
			switch(responseHeader[1]) {
				case FastCGIRecordType.FCGI_END_REQUEST:
					SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");							//SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
					String formattedDate = formatter.format(new Date());
					System.out.println("\r" + formattedDate + " PHP Request from " + httpRequest.clientAddress + "; FCGI_END_REQUEST: " + httpRequest.path);
					//System.out.println(new String(baos.toByteArray()));
					nc.close();
				return new HTTPResponse(null, baos.toByteArray(), "revers to old server");
				case FastCGIRecordType.FCGI_STDOUT:
					buffer = nc.recvChunk(contentLength);
					baos.write(buffer, 0, buffer.length);
					nc.recvChunk(paddingLength);
					break;
				case FastCGIRecordType.FCGI_STDERR:
					buffer = nc.recvChunk(contentLength);
					System.out.print("FCGI_STDERR: ");
					System.out.println(new String(buffer, 0, buffer.length));
					nc.recvChunk(paddingLength);
					break;
				default:
					System.out.println("Unknown FastCGI record type: " + responseHeader[1]);
					break;
			}
		}
		//return new HTTPResponse(500);
	}


	private static void sendPHPFPMRequest(NetworkClient nc, String param, String msg, byte type, int requestID) {
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
				nc.sendChunk(header);
				nc.sendChunk(data);
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
				
				nc.sendChunk(header);
				if(stringLength != 0) {
					if(paramLength >= 128) {
						nc.sendChunk(paramLen);
					}
					else {
						nc.sendChunk((byte)(paramLength & 0xFF));
					}
					
					if(msgLength >= 128) {
						nc.sendChunk(msgLen);
					}
					else {
						nc.sendChunk((byte)(msgLength & 0xFF));
					}
					nc.sendChunk(param.getBytes());
					nc.sendChunk(msg.getBytes());
					//System.out.println(param + ": " + msg);
					
					if(paddingLength != 0) {
						byte[] padding = new byte[paddingLength];
						nc.sendChunk(padding);
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
				
				nc.sendChunk(header);
				if(msgLength != 0) {
					nc.sendChunk(msg.getBytes());
					if(paddingLength != 0) {
						byte[] padding = new byte[paddingLength];
						nc.sendChunk(padding);
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
				nc.sendChunk(header);
				break;
		}

		nc.sendFlush();
	}

}
