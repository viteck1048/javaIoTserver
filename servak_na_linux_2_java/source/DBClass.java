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
	
	private long getSerialNumberReq(String requbuf, int marker) {
		long serialNumberReq = 0;
		if(marker == 101) {
			String[] hexArray = requbuf.split("\\+");
			for(int i = hexArray.length - 4; i < hexArray.length; i++) {
				serialNumberReq += Integer.parseInt(hexArray[i], 16) << (8 * (i + 4 - hexArray.length));
			}
		}
		return serialNumberReq;
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
	
	public String handleRequest(String requbuf, int marker) {		// marker == :101 - POST/PUT 8083 lialiapam2(від реле) .. відповідь - стрінг з закодованими даними: "вас почув/зрозумів" \ "білібєрда"(запрошення для повторної ініціації довіреного з'єднання) \ передача інструкцій для оновлення підпрограми в реле
		long serialNumberReq = getSerialNumberReq(requbuf, marker);
		AvrRele avrRele = findAvrReleBySerialNumber(serialNumberReq);
		if (avrRele == null) {
			avrRele = addToAvrReleList(serialNumberReq);
		}
		if(marker == 101)
			return avrRele.update(StringToByte(requbuf, marker));
		return null;
	}

	public String handleRequest(byte[] requbufByte, int marker) {		// marker == :101 - POST/PUT 8083 lialiapam2(від реле) .. відповідь - стрінг з закодованими даними: "вас почув/зрозумів" \ "білібєрда"(запрошення для повторної ініціації довіреного з'єднання) \ передача інструкцій для оновлення підпрограми в реле
		long serialNumberReq = getSerialNumberReq(requbufByte, marker);
		AvrRele avrRele = findAvrReleBySerialNumber(serialNumberReq);
		if (avrRele == null) {
			avrRele = new AvrRele(serialNumberReq);
			avrReleList.add(avrRele);
		}
		if(marker == 101)
			return avrRele.update(requbufByte);
		return null;
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


