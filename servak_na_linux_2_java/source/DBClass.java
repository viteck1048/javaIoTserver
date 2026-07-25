import java.util.ArrayList;
import java.util.Iterator;



public class DBClass {
	public ArrayList<AvrRele> avrReleList;
	private static final long TIMEOUT = 3600000; // 60 хвилин
	private static final long TIMEOUT_ONLINE = 20000; // 20 секунд
	private static DBClass instance;
	
	private byte[] StringToByte(String requbuf, int marker) {
		if(marker == 101) {
			String[] hexArray = requbuf.split("\\+");
			byte[] requbufByte = new byte[hexArray.length];
			for(int i = 0; i < hexArray.length; i++) {
				requbufByte[i] = (byte) Integer.parseInt(hexArray[i], 16);
			}
			return requbufByte;
		}
		return null;
	}
	
	private long getSerialNumberReq(byte[] requbufByte, int marker) {
		long serialNumberReq = 0;
		if(marker == 101) {
			for(int i = requbufByte.length - 4; i < requbufByte.length; i++) {
				serialNumberReq += (long)(((long)requbufByte[i] & 0xff) << (8 * (i + 4 - requbufByte.length)));
			}
		}
		return serialNumberReq;
	}
	
	private DBClass() {
		this.avrReleList = new ArrayList<>();
		// Штатний "деструктор" при зупинці сервісу: systemctl stop/restart шле SIGTERM,
		// Ctrl+C — SIGINT, обидва тригерять shutdown hook. НЕ спрацює на kill -9 (SIGKILL).
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			System.out.println("Shutdown hook: saving all AvrRele to DB...");
			saveAll();
		}));
	}

	/** Зберігає всі екземпляри в БД (виклик при зупинці сервера). */
	public void saveAll() {
		ArrayList<AvrRele> snapshot;
		synchronized (this) {
			snapshot = new ArrayList<>(avrReleList);
		}
		for (AvrRele avrRele : snapshot) {
			avrRele.saveRele();
		}
	}
	public static DBClass getInstance() {
		if (instance == null) {
			synchronized (DBClass.class) {
				if (instance == null) {
					instance = new DBClass();
				}
			}
		}
		return instance;
	}
	
	// marker == :101 - POST/PUT 8083 lialiapam2(від реле) .. відповідь - стрінг з закодованими даними: "вас почув/зрозумів" \ "білібєрда"(запрошення для повторної ініціації довіреного з'єднання) \ передача інструкцій для оновлення підпрограми в реле
	public static HTTPResponse requestFromGadget(HTTPRequest httpRequest) {
		String contentType = httpRequest.getZnach("content-type", HTTPRequest.arrType.HEADER);
		if (contentType != null && contentType.equals("application/octet-stream"))
			return handleRequestBase64(httpRequest);
		return handleRequestBase16(httpRequest);
	}

	private static HTTPResponse handleRequestBase16(HTTPRequest httpRequest) {
		DBClass dBClass = DBClass.getInstance();
		byte[] requbufByte = dBClass.StringToByte(new String(httpRequest.body), 101);
		byte[] vidp = dBClass.handleRequest(requbufByte, 101);
		if (vidp == null)
			return new HTTPResponse(400);
		String vidpHex = bytesToPlusHex(vidp);
		return new HTTPResponse("HTTP/1.1 200 OK\r\n" + "Content-Length: " + vidpHex.length() + "\r\n" + "\r\n",
				vidpHex.getBytes(), "\t\trequest on port 8083: " + vidp.length + " bytes (base16)");
	}

	private static HTTPResponse handleRequestBase64(HTTPRequest httpRequest) {
		DBClass dBClass = DBClass.getInstance();
		byte[] requbufByte = decodeCustomBase64(httpRequest.body);
		byte[] vidp = dBClass.handleRequest(requbufByte, 101);
		if (vidp == null)
			return new HTTPResponse(400);
		byte[] encoded = encodeCustomBase64(vidp);
		return new HTTPResponse("HTTP/1.1 200 OK\r\n" + "Content-Length: " + encoded.length + "\r\n" + "\r\n",
				encoded, "\t\trequest on port 8083: " + vidp.length + " bytes (base64)");
	}

	/** Пошук/створення AvrRele за серійником з чистих байтів запиту + виклик update. null, якщо довжина невалідна (очікується 40 або 320 байт). */
	private byte[] handleRequest(byte[] requbufByte, int marker) {
		if (requbufByte.length != 40 && requbufByte.length != 320)
			return null;
		long serialNumberReq = getSerialNumberReq(requbufByte, marker);
		AvrRele avrRele = findAvrReleBySerialNumber(serialNumberReq);
		if (avrRele == null) {
			avrRele = addToAvrReleList(serialNumberReq);
		}
		if(marker == 101)
			return avrRele.update(requbufByte);
		return null;
	}

	/** Декодує пакування 3 байти -> 4 символи (offset 0x40, своп 0x5C<->0x2C) назад у чисті байти. */
	private static byte[] decodeCustomBase64(byte[] wire) {
		int contentLength = wire.length;
		char[] bodyChArr = new char[contentLength];
		for (int ii = 0; ii < contentLength; ii++) {
			bodyChArr[ii] = (char) wire[ii];
		}
		short blocks;
		byte reshta = (byte)(bodyChArr[contentLength - 1] & 0x03);
		for (int ii = 0; ii < contentLength - 1; ii++) {
			if (bodyChArr[ii] == 0x2c)
				bodyChArr[ii] = 0x5c;
		}
		if (reshta == 0) {
			blocks = (short)((contentLength - 1) / 4);
			contentLength = blocks * 3;
		} else {
			blocks = (short)((contentLength - 1) / 4);
			contentLength = (blocks - 1) * 3 + reshta;
		}
		byte[] bodyDataTmp = new byte[contentLength + 3];
		for (short ii = 0; ii < blocks; ii++) {
			bodyDataTmp[ii * 3 + 0] = (byte)(((bodyChArr[ii * 4 + 0] & 0x3f) << 2) | ((bodyChArr[ii * 4 + 1] & 0x30) >> 4));
			bodyDataTmp[ii * 3 + 1] = (byte)(((bodyChArr[ii * 4 + 1] & 0x0f) << 4) | ((bodyChArr[ii * 4 + 2] & 0x3c) >> 2));
			bodyDataTmp[ii * 3 + 2] = (byte)(((bodyChArr[ii * 4 + 2] & 0x03) << 6) | ((bodyChArr[ii * 4 + 3] & 0x3f) >> 0));
		}
		byte[] result = new byte[contentLength];
		System.arraycopy(bodyDataTmp, 0, result, 0, contentLength);
		return result;
	}

	/** Кодує чисті байти у пакування 3 байти -> 4 символи (offset 0x40, своп 0x5C<->0x2C, останній байт - маркер залишку). */
	private static byte[] encodeCustomBase64(byte[] pureBytes) {
		byte reshta = (byte)(pureBytes.length % 3);
		short blocks = (short)(pureBytes.length / 3 + (reshta == 0 ? 0 : 1));
		byte[] bodyTmp = new byte[pureBytes.length + 3];
		System.arraycopy(pureBytes, 0, bodyTmp, 0, pureBytes.length);
		short length2 = (short)(blocks * 4 + 1);
		byte[] vidp2 = new byte[length2];
		for (short ii = 0; ii < blocks; ii++) {
			vidp2[ii * 4 + 0] = (byte)(0x40 | ((bodyTmp[ii * 3 + 0] & 0b11111100) >> 2));
			vidp2[ii * 4 + 1] = (byte)(0x40 | ((bodyTmp[ii * 3 + 0] & 0b00000011) << 4) | ((bodyTmp[ii * 3 + 1] & 0b11110000) >> 4));
			vidp2[ii * 4 + 2] = (byte)(0x40 | ((bodyTmp[ii * 3 + 1] & 0b00001111) << 2) | ((bodyTmp[ii * 3 + 2] & 0b11000000) >> 6));
			vidp2[ii * 4 + 3] = (byte)(0x40 | ((bodyTmp[ii * 3 + 2] & 0b00111111) >> 0));
		}
		for (short ii = 0; ii < length2; ii++) {
			if (vidp2[ii] == (byte)0x5c)
				vidp2[ii] = (byte)0x2c;
		}
		vidp2[length2 - 1] = (byte)(0x20 | reshta);
		return vidp2;
	}

	/** Чисті байти -> "+"-розділений hex-рядок (base16 формат девайса). */
	private static String bytesToPlusHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < bytes.length; i++) {
			if (i > 0) sb.append('+');
			sb.append(String.format("%02X", bytes[i]));
		}
		return sb.toString();
	}

	public AvrRele findAvrReleBySerialNumber(long serialNumberReq) {
		cleanupOldEntries();
		for (AvrRele avrRele : avrReleList) {
			if (avrRele.getSerialNumber() == serialNumberReq) {
				return avrRele;
			}
		}
		return null;
	}
	
	public AvrRele addToAvrReleList(long serialNumber) {
		AvrRele avrRele = new AvrRele(serialNumber);
		avrReleList.add(avrRele);
		return avrRele;
	}
	
	public void cleanupOldEntries() {
		long currentTime = System.currentTimeMillis();
		Iterator<AvrRele> iterator = avrReleList.iterator();
		while (iterator.hasNext()) {
			AvrRele avrRele = iterator.next();
			if (currentTime - avrRele.getLastAccessTime() > TIMEOUT_ONLINE) {
				if(avrRele.online == true) {
					avrRele.online = false;
					avrRele.saveRele();
				}
			}
			if (currentTime - avrRele.getLastAccessTime() > TIMEOUT) {
				avrRele.saveRele();
				iterator.remove();
			}
		}
	}
}






/*
+ " g_id integer PRIMARY KEY,"
						+ " name text,\n"
						+ " sn_mega integer NOT NULL,"
						+ " sn_esp integer,"
						+ " obscht_r integer,"
						+ " rezhym_tmr BLOB,"
						+ " rz BLOB,"
						+ " ri BLOB,"
						+ " rv BLOB,"
						+ " arr_golovne_menu BLOB"
*/


