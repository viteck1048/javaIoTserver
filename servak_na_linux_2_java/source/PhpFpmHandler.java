import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
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
		//public static final byte FCGI_UNKNOWN_TYPE = 11;
	}

	private static final byte FCGI_VERSION_1 = 1;
	private static final byte FCGI_RESPONDER = 1;
	/** Максимум, що влазить у 16-бітне поле contentLength заголовка запису */
	private static final int MAX_RECORD_CONTENT = 65535;
	/** Скільки чекати на відповідь: між запитом і першим FCGI_STDOUT FPM виконує весь скрипт */
	private static final int DEFAULT_TIMEOUT_MS = 30000;

	private PhpFpmHandler() {
		throw new UnsupportedOperationException("Utility class");
	}

	// TODO: Додати статичні методи для проміжної обробки запитів і відповідей

	public static HTTPResponse phpFpmResend(HTTPRequest httpRequest) {

		String host = Configs.getParam("ip_php_fpm_server");
		int port = Configs.getInt("port_php_fpm_server");
		int timeout = Configs.getInt("php_fpm_timeout_ms");
		if(timeout <= 0)
			timeout = DEFAULT_TIMEOUT_MS;

		NetworkClient nc;
		try {
			nc = new NetworkClient(host, port, false);
			nc.setSoTimeout(timeout);
		} catch (IOException e) {
			System.out.println("PhpFpmHandler error: " + e.getMessage());
			e.printStackTrace();
			return new HTTPResponse(503);
		}

		try {
			int requestID = 1;
			sendBeginRequest(nc, requestID);
			for(int i = 0; i < httpRequest.getPhpQueryLength(); i++) {
				sendParam(nc, httpRequest.getPhpParam(i), httpRequest.getPhpZnach(i), requestID);
			}
			sendParam(nc, null, null, requestID);								// кінець потоку FCGI_PARAMS

			byte[] bodyData = httpRequest.bodyData;
			if(bodyData != null && bodyData.length > 0
					&& (httpRequest.method.compareTo("POST") == 0
						|| httpRequest.method.compareTo("PUT") == 0
						|| httpRequest.method.compareTo("DELETE") == 0)) {
				int index = 0;
				while(bodyData.length - index > MAX_RECORD_CONTENT) {
					sendStdin(nc, bodyData, index, MAX_RECORD_CONTENT, requestID);
					index += MAX_RECORD_CONTENT;
				}
				sendStdin(nc, bodyData, index, bodyData.length - index, requestID);
			}
			sendStdin(nc, null, 0, 0, requestID);								// кінець потоку FCGI_STDIN

			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			while(true) {
				byte[] responseHeader = nc.recvChunk(8);
				if(responseHeader == null) {
					System.out.println("PhpFpmHandler: no FastCGI record from " + host + ":" + port
						+ " within " + timeout + "ms; path: " + httpRequest.path);
					return new HTTPResponse(504);
				}
				int contentLength = (((responseHeader[4] & 0xFF) << 8) + (responseHeader[5] & 0xFF));
				int paddingLength = responseHeader[6] & 0xFF;
				byte[] buffer;
				switch(responseHeader[1]) {
					case FastCGIRecordType.FCGI_END_REQUEST:
						SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");							//SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
						String formattedDate = formatter.format(new Date());
						System.out.println("\r" + formattedDate + " PHP Request from " + httpRequest.clientAddress + "; FCGI_END_REQUEST: " + httpRequest.path);
						//System.out.println(new String(baos.toByteArray()));
					return new HTTPResponse(null, baos.toByteArray(), "revers to old server");
					case FastCGIRecordType.FCGI_STDOUT:
						buffer = nc.recvChunk(contentLength);
						if(buffer == null || !skip(nc, paddingLength))
							return new HTTPResponse(504);
						baos.write(buffer, 0, buffer.length);
						break;
					case FastCGIRecordType.FCGI_STDERR:
						buffer = nc.recvChunk(contentLength);
						if(buffer == null || !skip(nc, paddingLength))
							return new HTTPResponse(504);
						System.out.print("FCGI_STDERR: ");
						System.out.println(new String(buffer, 0, buffer.length));
						break;
					default:
						// Тіло невідомого запису теж треба вичитати: інакше наступна ітерація
						// прочитає його як заголовок і потік записів розсинхронізується назавжди
						System.out.println("Unknown FastCGI record type: " + responseHeader[1]);
						if(!skip(nc, contentLength) || !skip(nc, paddingLength))
							return new HTTPResponse(504);
						break;
				}
			}
		} finally {
			nc.close();
		}
	}

	/**
	 * Вичитує і відкидає length байтів
	 * @return false, якщо потік обірвався або таймаут
	 */
	private static boolean skip(NetworkClient nc, int length) {
		if(length <= 0)
			return true;
		return nc.recvChunk(length) != null;
	}

	private static void sendBeginRequest(NetworkClient nc, int requestID) {
		byte[] data = new byte[8];
		data[0] = 0x00;
		data[1] = FCGI_RESPONDER;
		data[2] = 0x00;														// flags: без keep-alive, FPM закриє з'єднання після END_REQUEST
		sendHeader(nc, FastCGIRecordType.FCGI_BEGIN_REQUEST, requestID, data.length, 0);
		nc.sendChunk(data);
		nc.sendFlush();
	}

	/**
	 * Один запис FCGI_PARAMS з парою name-value; param == null або msg == null — кінець потоку параметрів
	 */
	private static void sendParam(NetworkClient nc, String param, String msg, int requestID) {
		if(param == null || msg == null) {
			sendHeader(nc, FastCGIRecordType.FCGI_PARAMS, requestID, 0, 0);
			nc.sendFlush();
			return;
		}

		// Довжини рахуємо з байтів, а не з String.length(): у не-ASCII символі байтів
		// більше, ніж символів, і запис із заниженою довжиною розсинхронізує потік
		byte[] paramData = param.getBytes(StandardCharsets.UTF_8);
		byte[] msgData = msg.getBytes(StandardCharsets.UTF_8);
		byte[] paramLen = encodeLength(paramData.length);
		byte[] msgLen = encodeLength(msgData.length);

		int contentLength = paramLen.length + msgLen.length + paramData.length + msgData.length;
		if(contentLength > MAX_RECORD_CONTENT) {
			System.out.println("PhpFpmHandler: FCGI_PARAMS \"" + param + "\" is too long (" + contentLength + " bytes), skipped");
			return;
		}
		int paddingLength = (8 - (contentLength % 8)) % 8;

		sendHeader(nc, FastCGIRecordType.FCGI_PARAMS, requestID, contentLength, paddingLength);
		nc.sendChunk(paramLen);
		nc.sendChunk(msgLen);
		nc.sendChunk(paramData);
		nc.sendChunk(msgData);
		if(paddingLength != 0)
			nc.sendChunk(new byte[paddingLength]);
		nc.sendFlush();
	}

	/**
	 * Один запис FCGI_STDIN; length == 0 — кінець потоку тіла запиту
	 */
	private static void sendStdin(NetworkClient nc, byte[] data, int offset, int length, int requestID) {
		int paddingLength = (8 - (length % 8)) % 8;
		sendHeader(nc, FastCGIRecordType.FCGI_STDIN, requestID, length, paddingLength);
		if(length != 0) {
			nc.sendChunk(Arrays.copyOfRange(data, offset, offset + length));
			if(paddingLength != 0)
				nc.sendChunk(new byte[paddingLength]);
		}
		nc.sendFlush();
	}

	private static void sendHeader(NetworkClient nc, byte type, int requestID, int contentLength, int paddingLength) {
		byte[] header = new byte[8];
		header[0] = FCGI_VERSION_1;
		header[1] = type;
		header[2] = (byte)(requestID >> 8);
		header[3] = (byte)(requestID & 0xFF);
		header[4] = (byte)(contentLength >> 8);
		header[5] = (byte)(contentLength & 0xFF);
		header[6] = (byte)(paddingLength & 0xFF);
		header[7] = 0x00;
		nc.sendChunk(header);
	}

	/**
	 * Довжина name/value в форматі FastCGI: 1 байт до 127, інакше 4 байти зі старшим бітом
	 */
	private static byte[] encodeLength(int length) {
		if(length < 128) {
			return new byte[] {(byte)(length & 0xFF)};
		}
		return new byte[] {
			(byte)((length >> 24) | 0x80),
			(byte)(length >> 16),
			(byte)(length >> 8),
			(byte)(length & 0xFF)
		};
	}

}
