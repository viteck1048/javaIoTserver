import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;

/**
 * Імітатор фізичного 18-входового пристрою MachineTime.
 *
 * Один екземпляр = один віртуальний пристрій. Servak.main() пускає його окремим
 * потоком на кожен увімкнений блок mt_fwd_<i> у config.ini (аналогічно prxy_).
 *
 * Конфіг:
 *   mt_fwd_<i>=true                — увімкнути форвардер №i
 *   mt_fwd_<i>_device1..device18   — серійники (sn_mega) AVR-реле, прибиті до
 *                                    входів 1..18. Порядок у avrReleList нестабільний,
 *                                    тому вхід визначає ТІЛЬКИ серійник, не позиція.
 *   mt_fwd_<i>_timezone=Europe/Kyiv — локальний час форвардера (за ним рахуємо північ)
 *   mt_fwd_<i>_dial_host/_dial_port/_dial_ssl/_dial_path — куди слати (мікросервіс)
 *   mt_fwd_<i>_user_agent / _modulID — User-Agent і ?id= для MachineTime
 *   mt_fwd_<i>_private_key_1..4 / _myStaticKeyRequest / _myStaticKeyResponce — крипта
 *
 * Дві шкали по [19]: індекс 0 = UTC (epoch-секунди), індекси 1..18 = пристрої
 * (obscht_r + tek_r). Незаповнений/незнайдений вхід тримає перманентний 0.
 */
public class MachineTimeForwarder implements Runnable {

	private static final int  SLOTS       = 19;   // [0]=UTC, [1..18]=пристрої
	private static final int  DEVICES     = 18;
	private static final long POLL_MS      = 5000;  // опитування кожні 5 с
	private static final long FIRST_DELAY_MS = 15000; // дати пристроям достукатись перед зняттям baseline

	private final String prefix;          // "mt_fwd_" + index
	private final ZoneId zone;            // локальний час цього форвардера

	private final long[] deviceSerial  = new long[SLOTS]; // [1..18] -> sn_mega (0 = вхід вільний)
	private final long[] sessionStart  = new long[SLOTS]; // початок сесії (baseline) — звідки відлік
	private final long[] nowMoment     = new long[SLOTS]; // час у моменті
	private final long[] lastElapsed   = new long[SLOTS]; // останнє відоме elapsed

	private final MyCripter myCripter;
	private final String dialHost, dialPath, userAgent, modulID, tzName;
	private final int dialPort;
	private final boolean dialSsl;

	// Піднімається щоразу при оновленні sessionStart (старт + кожна північ форвардера).
	public volatile boolean baselineRefreshed = false;

	public MachineTimeForwarder(String prefix) {
		this.prefix = prefix;
		this.zone   = resolveZone();
		loadDeviceMap();

		dialHost  = Configs.getParam(prefix + "_dial_host");
		dialPort  = Configs.getInt(prefix + "_dial_port");
		dialSsl   = Configs.getBoolean(prefix + "_dial_ssl");
		dialPath  = Configs.getParam(prefix + "_dial_path");
		userAgent = Configs.getParam(prefix + "_user_agent");
		modulID   = Configs.getParam(prefix + "_modulID");
		tzName    = Configs.getParam(prefix + "_timezone");

		myCripter = new MyCripter(
			Configs.getLong(prefix + "_private_key_1"),
			Configs.getLong(prefix + "_private_key_2"),
			Configs.getLong(prefix + "_private_key_3"),
			Configs.getLong(prefix + "_private_key_4"),
			Configs.getParam(prefix + "_myStaticKeyRequest"),
			Configs.getParam(prefix + "_myStaticKeyResponce")
		);
	}

	private ZoneId resolveZone() {
		if (Configs.getDefine(prefix + "_timezone")) {
			try {
				return ZoneId.of(Configs.getParam(prefix + "_timezone"));
			} catch (Exception e) {
				System.err.println(prefix + ": невалідна timezone, беру системну - " + e.getMessage());
			}
		}
		return ZoneId.systemDefault();
	}

	/** mt_fwd_<index>_device1..device18 -> deviceSerial[1..18]. */
	private void loadDeviceMap() {
		for (int dev = 1; dev <= DEVICES; dev++) {
			String key = prefix + "_device" + dev;
			deviceSerial[dev] = Configs.getDefine(key) ? Configs.getLong(key) : 0L;
		}
	}

	@Override
	public void run() {
		System.out.println(prefix + " started, zone=" + zone);
		if (!sleep(FIRST_DELAY_MS))
			return;
		refreshSessionStart();                 // базова точка після того, як пристрої достукались
		LocalDate lastDay = LocalDate.now(zone);

		while (true) {
			if (!sleep(POLL_MS))
				return;

			LocalDate today = LocalDate.now(zone);
			if (!today.equals(lastDay)) {       // настала північ за часом форвардера
				refreshSessionStart();
				lastDay = today;
			}

			poll(nowMoment);
			adoptMissingBaselines();
			send();
		}
	}

	/** Сон із обробкою переривання. false -> потік перервано, треба вийти. */
	private boolean sleep(long ms) {
		try {
			Thread.sleep(ms);
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	/**
	 * Один цикл звʼязку. Не connected -> POST (хендшейк zerno), інакше PUT (дані).
	 * Прапорець baselineRefreshed гасимо лише після успішного PUT у стані connected.
	 */
	private void send() {
		boolean wasConnected = myCripter.getConnected();
		String body = myCripter.encrypt(prepareDiff());
		byte[] payload = body.getBytes(StandardCharsets.ISO_8859_1);

		String header = (wasConnected ? "PUT " : "POST ")
			+ dialPath + "?id=" + modulID + " HTTP/1.1\r\n"
			+ "Host: " + dialHost + "\r\n"
			+ "User-Agent: " + userAgent + "\r\n"
			+ "Content-Type: application/octet-stream\r\n"
			+ "Content-Length: " + payload.length + "\r\n";
		if (!wasConnected)
			header += "X-Timezone: " + tzName + "\r\n";
		header += "Connection: close\r\n";

		NetworkClient nc = null;
		try {
			nc = new NetworkClient(dialHost, dialPort, dialSsl);
			byte[] response = nc.sendAndReceive(header.getBytes(StandardCharsets.ISO_8859_1), payload);
			if (response == null) {
				myCripter.resetConnected();
				return;
			}
			String resp = new String(response, StandardCharsets.ISO_8859_1);
			if (!resp.startsWith("HTTP/1.1 200")) {
				myCripter.resetConnected();
				return;
			}
			int bodyStart = resp.indexOf("\r\n\r\n");
			String respBody = bodyStart >= 0 ? resp.substring(bodyStart + 4) : "";
			if (myCripter.decrypt(respBody) && wasConnected && baselineRefreshed)
				baselineRefreshed = false;
		} catch (Exception e) {
			myCripter.resetConnected();
			System.err.println(prefix + " send: " + e.getMessage());
		} finally {
			if (nc != null)
				nc.close();
		}
	}

	/** Знімає baseline і піднімає прапорець. */
	private void refreshSessionStart() {
		poll(sessionStart);
		baselineRefreshed = true;
	}

	/**
	 * Якщо baseline входу не зняли на старті/опівночі (пристрій тоді був відсутній),
	 * беремо за нуль перший момент його появи — інакше diff покаже повне напрацювання.
	 */
	private void adoptMissingBaselines() {
		for (int i = 1; i <= DEVICES; i++) {
			if (deviceSerial[i] != 0 && sessionStart[i] < 0 && nowMoment[i] >= 0)
				sessionStart[i] = nowMoment[i];
		}
	}

	/**
	 * Знімає поточні абсолютні секунди у переданий масив:
	 *   [0] = UTC epoch-секунди; [1..18] = obscht_r + tek_r відповідного пристрою.
	 * Вхід без серійника  -> 0 (нічого не підключено).
	 */
	private void poll(long[] arr) {
		arr[0] = Instant.now().getEpochSecond();
		for (int i = 1; i <= DEVICES; i++) {
			long serial = deviceSerial[i];
			if (serial == 0) {
				arr[i] = 0;
				continue;
			}
			AvrRele dev = DBClass.getInstance().findAvrReleBySerialNumber(serial);
			if (dev == null) {
				arr[i] = -1;
				continue;
			}
			arr[i] = dev.readRuntimeAbsolute();
			//System.out.println(prefix + ": device " + i + " (sn=" + serial + ") runtime=" + arr[i]);
		}
	}

	/**
	 * Масив для крипти/відправлення (20 слотів, слот 11 — прапорець, як у пристрої).
	 * Слово каналу: [3 байти секунд][молодший байт = id каналу j].
	 */
	public long[] prepareDiff() {
		long[] diff = new long[SLOTS + 1];
		for (int i = 0; i < SLOTS; i++) {
			int j = i + i / 11;
			long now = nowMoment[i];
			long base = sessionStart[i];
			long elapsed;
			if (now < 0) {
				elapsed = lastElapsed[i];
			} else if (base < 0) {
				elapsed = 0;
			} else {
				elapsed = now - base;
				if (elapsed < 0) elapsed = 0;
				lastElapsed[i] = elapsed;
			}
			diff[j] = (elapsed << 8) | j;
		}
		diff[11] = baselineRefreshed ? 0xff : 0; // індикатор оновлення baseline
		return diff;
	}
}
