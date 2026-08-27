import java.io.IOException;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Пул потоків, що перевикористовуються, для ServerTask/SimpleHTTPSServer -
 * заміна `new ClientHandler(socket, port, isHttps).start()`.
 *
 * Черга - одна спільна (java.util.concurrent.BlockingQueue), а не по воркеру:
 * будь-який вільний потік (старий чи щойно народжений) бере наступний сокет,
 * замість того щоб застрягати черга-на-воркера, поки інший воркер вільний.
 *
 * Ріст НЕ миттєвий: коли всі воркери зайняті, фіксується момент (backlogStartTime).
 * Новий воркер народжується лише якщо цей стан протримався GROWTH_DEBOUNCE_MS
 * безперервно - перевіряється на кожному новому submit(), без окремого потоку-
 * таймера й без періодичного опитування. Мета: короткий сплеск (once traffic
 * spike, флуд-спроба, що quickBan відріже за мілісекунди) не встигає наплодити
 * купу потоків, кожен з яких незалежно долетить до рішення "забанити" те саме
 * джерело.
 *
 * Всихання - дзеркально, без окремого монітора: кожен воркер сам засинає на
 * WORKER_IDLE_TIMEOUT_MS (queue.poll з таймаутом), і якщо після цього частка
 * вільних потоків усе ще вища за IDLE_TARGET_HIGH (і пул не впав нижче
 * MIN_WORKERS) - сам завершує цикл.
 *
 * ClientHandler не змінений по суті: тут викликається handleConnection()
 * замість .start() - ClientHandler extends Thread, але run()/.start() лишається
 * для прямого використання (ServerTask/SimpleHTTPSServer), яке пул не чіпає.
 * handleConnection() обробляє ОДИН цикл (запит-відповідь або "поки нічого
 * нема") і повертає true, якщо з'єднання варто тримати відкритим далі.
 *
 * Keep-alive не тримається воркером: коли handleConnection() повертає true,
 * задача йде не назад у ClientHandler, а вартовому (guard()/guardLoop()) -
 * малій кількості потоків, які по черзі роблять короткий pollForNextRequest()
 * на кожному з'єднанні, що чекає наступного запиту, і повертають задачу в
 * queue лише коли там справді щось з'явилось. Так відкрите-але-мовчазне
 * з'єднання не займає слот із MAX_WORKERS.
 *
 * Вартові теж ростуть/всихають, але не так, як воркери. Ріст - не за
 * "чи всі зайняті" (вартові технічно завжди "зайняті", коли в guardQueue
 * щось є), а за розрахунковим, а не виміряним, часом повного обходу черги
 * одним вартовим: (guardedCount / guardThreadCount) * KEEP_ALIVE_POLL_TIMEOUT_MS.
 * Перевищив KEEP_ALIVE_GUARD_ROUND_TIME_THRESHOLD_MS - народжується ще один,
 * аж до KEEP_ALIVE_GUARD_THREADS_MAX (maybeGrowGuards(), викликається з
 * guard() на кожному новому keep-alive). Всихання - дзеркально до воркерів:
 * guardQueue.poll(KEEP_ALIVE_GUARD_IDLE_TIMEOUT_MS) повертає null, коли
 * вартувати нема кого, і вартовий іде на вихід, якщо їх усе ще більше за
 * KEEP_ALIVE_GUARD_THREADS_MIN.
 */
public class WorkerPool {

	private static final int MIN_WORKERS = Configs.getInt("workerPoolMin");
	private static final int MAX_WORKERS = Configs.getInt("workerPoolMax");
	/** Верхня межа частки вільних потоків, понад яку зайвий воркер іде на вихід */
	private static final double IDLE_TARGET_HIGH = Configs.getLong("workerPoolIdleHigh") / 100.0;
	/** Скільки мс стан "усі зайняті" має протриматись безперервно, перш ніж народиться новий воркер */
	private static final long GROWTH_DEBOUNCE_MS = Configs.getLong("workerPoolGrowthDebounceMs");
	/** Скільки мс воркер чекає на чергове завдання, перш ніж розглянути власний вихід */
	private static final long WORKER_IDLE_TIMEOUT_MS = Configs.getLong("workerPoolIdleTimeoutMs");
	/** Мінімум вартових потоків keep-alive - стільки тримається завжди, навіть без жодного guarded-сокета */
	private static final int KEEP_ALIVE_GUARD_THREADS_MIN = Configs.getInt("workerPoolKeepAliveGuardThreadsMin");
	/** Стеля, вище якої вартові не ростуть, хай там скільки сокетів чекає */
	private static final int KEEP_ALIVE_GUARD_THREADS_MAX = Configs.getInt("workerPoolKeepAliveGuardThreadsMax");
	/**
	 * Поріг очікуваного часу повного обходу guardQueue одним вартовим (мс), понад який
	 * народжується ще один вартовий потік. Не вимірюється емпірично - рахується напряму
	 * (guardedCount / поточна к-ть вартових) * KEEP_ALIVE_POLL_TIMEOUT_MS, бо ці три числа
	 * й так відомі пулу в кожен момент.
	 */
	private static final long KEEP_ALIVE_GUARD_ROUND_TIME_THRESHOLD_MS = Configs.getLong("workerPoolKeepAliveGuardRoundTimeThresholdMs");
	/** Скільки мс вартовий чекає на guardQueue.poll(), перш ніж розглянути власний вихід (симетрично WORKER_IDLE_TIMEOUT_MS) */
	private static final long KEEP_ALIVE_GUARD_IDLE_TIMEOUT_MS = Configs.getLong("workerPoolKeepAliveGuardIdleTimeoutMs");
	/**
	 * Скільки мс вартовий чекає відповіді від одного сокета, перш ніж перейти до наступного
	 * в черзі. Це не швидкість, з якою ОС здатна відповісти "нема даних" (той read() під
	 * капотом падає на select()/poll(), і там відповідь видно за мікросекунди) - це чисто
	 * навмисна пауза, щоб не бомбити сисколами. Ціна заниженої межі мала (кожен "порожній"
	 * read() дешевий), а ціна завищеної - лінійна: за N задач на одного вартового найгірша
	 * затримка пробудження ~ N * це число.
	 */
	private static final int KEEP_ALIVE_POLL_TIMEOUT_MS = Configs.getInt("workerPoolKeepAlivePollTimeoutMs");

	private final BlockingQueue<Task> queue = new LinkedBlockingQueue<>();
	private final BlockingQueue<Task> guardQueue = new LinkedBlockingQueue<>();
	/** Реальні посилання на воркер-потоки - опора для reconcile(); totalWorkers/idleWorkers самі по собі можуть розійтись з дійсністю */
	private final Set<Thread> workers = ConcurrentHashMap.newKeySet();
	private final AtomicInteger totalWorkers = new AtomicInteger(0);
	private final AtomicInteger idleWorkers = new AtomicInteger(0);
	private final AtomicInteger guardThreadCount = new AtomicInteger(0);
	/** К-ть задач, що зараз під вартою (в guardQueue або якраз перевіряються) - основа для розрахунку часу обходу */
	private final AtomicInteger guardedCount = new AtomicInteger(0);
	/** 0 = зараз немає безперервного бекложу; інакше - System.currentTimeMillis() моменту, коли всі стали зайняті */
	private volatile long backlogStartTime = 0;

	private static final class Task {
		final Socket socket;
		final int port;
		final boolean isHttps;
		/** null до першого handleTask(); далі той самий екземпляр переживає всі keep-alive цикли задачі */
		ClientHandler handler;

		Task(Socket socket, int port, boolean isHttps) {
			this.socket = socket;
			this.port = port;
			this.isHttps = isHttps;
		}
	}

	public WorkerPool() {
		for (int i = 0; i < MIN_WORKERS; i++) {
			spawnWorker();
		}
		for (int i = 0; i < KEEP_ALIVE_GUARD_THREADS_MIN; i++) {
			spawnGuardThread();
		}
	}

	/**
	 * Викликати замість `new ClientHandler(socket, port, isHttps).start()`
	 * у ServerTask/SimpleHTTPSServer.
	 */
	public void submit(Socket socket, int port, boolean isHttps) {
		queue.add(new Task(socket, port, isHttps));
		maybeGrow();
	}

	private void maybeGrow() {
		if (idleWorkers.get() > 0) {
			backlogStartTime = 0;  // є вільний воркер - бекложу нема, скидаємо відлік
			return;
		}

		long now = System.currentTimeMillis();
		long start = backlogStartTime;
		if (start == 0) {
			backlogStartTime = now;  // щойно всі стали зайняті - фіксуємо момент, поки що не ростимо
			return;
		}

		if (now - start >= GROWTH_DEBOUNCE_MS && totalWorkers.get() < MAX_WORKERS) {
			spawnWorker();
			backlogStartTime = 0;  // відлік для наступного можливого сплеску - заново
		}
	}

	private void spawnWorker() {
		spawnWorker(null);
	}

	/**
	 * inheritName != null - нова нитка бере ім'я загиблого воркера (аварійна заміна
	 * з workerLoop()): номери в логах не повзуть угору з кожним крахом, "worker-pool-7"
	 * лишається "worker-pool-7". inheritName == null - штатний ріст пулу, ім'я за
	 * поточним розміром (значення, яке повернув incrementAndGet(), а не окремий get() -
	 * щоб паралельні spawn під флудом не давали двом ниткам однакове ім'я).
	 */
	private void spawnWorker(String inheritName) {
		int size = totalWorkers.incrementAndGet();
		idleWorkers.incrementAndGet();
		String name = inheritName != null ? inheritName : "worker-pool-" + size;
		Thread t = new Thread(this::workerLoop, name);
		t.setDaemon(true);
		workers.add(t);
		t.start();
	}

	private void workerLoop() {
		try {
			while (true) {
				Task task;
				try {
					task = queue.poll(WORKER_IDLE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}

				if (task == null) {
					if (shouldRetire()) {
						return;
					}
					continue;
				}

				idleWorkers.decrementAndGet();
				try {
					handleTask(task);
					idleWorkers.incrementAndGet();
				} catch (Throwable t) {
					// Стан ClientHandler/потоку після цього не довіряємо - воркер
					// свідомо йде на вихід замість повернення в чергу вільних.
					// idleWorkers НЕ повертаємо: цей потік уже ніколи не стане
					// вільним, а лише привидом у лічильнику (саме так виникав
					// баг: idleWorkers лишався інкрементованим за потік, що
					// щойно загинув, і maybeGrow() вважав пул вільним, коли він
					// насправді усихав).
					//
					// Завершення потоку в Java не закриває ресурси само по собі
					// (на відміну від завершення процесу) - сокет закриваємо явно.
					String name = Thread.currentThread().getName();
					System.err.println("WorkerPool: " + name
						+ " crashed on task from " + safeRemote(task) + ", closing socket and retiring worker");
					t.printStackTrace();
					closeQuietly(task.socket);
					// Компенсуємо втрату потужності негайно нового воркера під тим самим іменем,
					// а не через maybeGrow(): totalWorkers тут вже +1 (spawnWorker),
					// нижче в finally поточний потік відніме своє -1 - сумарно 0,
					// заміна один-на-один, а не безконтрольний ріст під флудом
					// крах-запитів.
					spawnWorker(name);
					System.err.println("WorkerPool: " + name + " respawned to replace crashed worker");
					return;
				}
			}
		} finally {
			workers.remove(Thread.currentThread());
			totalWorkers.decrementAndGet();
		}
	}

	private static void closeQuietly(Socket socket) {
		try {
			socket.close();
		} catch (IOException ignored) {
			// сокет однаково викидаємо, помилка закриття тут не критична
		}
	}

	private static String safeRemote(Task task) {
		try {
			return String.valueOf(task.socket.getInetAddress());
		} catch (Exception e) {
			return "unknown";
		}
	}

	/** Викликається лише коли воркер сам щойно простояв WORKER_IDLE_TIMEOUT_MS без завдань */
	private boolean shouldRetire() {
		int total = totalWorkers.get();
		if (total <= MIN_WORKERS) {
			return false;
		}
		double idleRatio = (double) idleWorkers.get() / total;
		return idleRatio > IDLE_TARGET_HIGH;
	}

	/**
	 * Періодична страховка (кличеться з CacheAgent): звіряє реальну живучість
	 * воркер-потоків із власним обліком (totalWorkers/idleWorkers) і доганяє
	 * пул до MIN_WORKERS, якщо десь потік загинув в обхід штатного шляху вище
	 * (наприклад, spawnWorker() під час аварійної заміни сам упав через
	 * нестачу ресурсів - реалістично саме під час OutOfMemoryError). Для
	 * штатного краху ця перевірка не потрібна - catch() у workerLoop() уже сам
	 * все прибирає й заміняє синхронно, це саме страховка на випадок, коли
	 * той шлях сам не спрацював.
	 */
	public void reconcile() {
		int deadRemoved = 0;
		for (Thread t : workers) {
			if (!t.isAlive()) {
				workers.remove(t);
				deadRemoved++;
			}
		}
		int alive = workers.size();
		int reported = totalWorkers.get();
		if (deadRemoved > 0 || alive != reported) {
			System.err.println("WorkerPool: reconcile - " + deadRemoved + " dead thread(s) found, "
				+ alive + " alive vs " + reported + " tracked - correcting");
			totalWorkers.set(alive);
		}
		while (workers.size() < MIN_WORKERS) {
			spawnWorker();
		}
	}

	private void handleTask(Task task) {
		// Повторна перевірка тут навмисно: бан міг настати вже ПІСЛЯ accept(),
		// поки сокет чекав у черзі. Рання перевірка (в accept-циклі, до submit())
		// лишається - вона дешевша й відсікає більшість флуду ще до появи в черзі;
		// ця - друга лінія, закриває вікно між accept() і фактичною видачею воркеру.
		// Спрацьовує і для keep-alive продовжень (task повертається сюди з guard()),
		// тож бан, що настав посеред з'єднання, теж підхоплюється.
		if (FirewallIP.quickBan(task.socket.getInetAddress())) {
			try {
				task.socket.close();
			} catch (IOException e) {
				// сокет однаково викидаємо, помилка закриття тут не критична
			}
			return;
		}

		if (task.handler == null) {
			task.handler = new ClientHandler(task.socket, task.port, task.isHttps);
		}

		if (task.handler.handleConnection()) {
			guard(task);
		}
	}

	/** Задача жива (keep-alive), але зараз нема чого робити - віддаємо вартовому замість воркера */
	private void guard(Task task) {
		guardedCount.incrementAndGet();
		guardQueue.add(task);
		maybeGrowGuards();
	}

	private void spawnGuardThread() {
		int id = guardThreadCount.incrementAndGet();
		Thread t = new Thread(this::guardLoop, "worker-pool-keepalive-guard-" + id);
		t.setDaemon(true);
		t.start();
	}

	/**
	 * Менеджер тут - сам пул: guardedCount (скільки задач під вартою),
	 * guardThreadCount (скільки вартових зараз) і KEEP_ALIVE_POLL_TIMEOUT_MS
	 * (ціна перевірки одного сокета) уже відомі, тож очікуваний час повного
	 * обходу guardQueue одним вартовим не вимірюється емпірично, а рахується
	 * напряму. Перевищив поріг - росте ще один вартовий, аж до стелі.
	 */
	private void maybeGrowGuards() {
		int threads = guardThreadCount.get();
		if (threads <= 0 || threads >= KEEP_ALIVE_GUARD_THREADS_MAX) {
			return;
		}
		long estimatedRoundMs = ((long) guardedCount.get() / threads) * KEEP_ALIVE_POLL_TIMEOUT_MS;
		if (estimatedRoundMs > KEEP_ALIVE_GUARD_ROUND_TIME_THRESHOLD_MS) {
			spawnGuardThread();
		}
	}

	/**
	 * Тіло вартового потоку: по черзі бере задачі з guardQueue, коротко
	 * перевіряє кожну (pollForNextRequest) і або повертає в основну queue
	 * (з'явились дані - хай забирає воркер), або кладе назад у guardQueue
	 * чекати далі. guardQueue.poll(timeout) блокується без busy-loop, коли
	 * вартувати нема кого; а якщо це триває довше KEEP_ALIVE_GUARD_IDLE_TIMEOUT_MS
	 * і вартових уже більше за мінімум - цей потік сам іде на вихід (симетрично
	 * shouldRetire() у workerLoop()).
	 */
	private void guardLoop() {
		try {
			while (true) {
				Task task;
				try {
					task = guardQueue.poll(KEEP_ALIVE_GUARD_IDLE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}

				if (task == null) {
					if (guardThreadCount.get() > KEEP_ALIVE_GUARD_THREADS_MIN) {
						return;
					}
					continue;
				}

				boolean ready;
				try {
					ready = task.handler.pollForNextRequest(KEEP_ALIVE_POLL_TIMEOUT_MS);
				} catch (IOException e) {
					// з'єднання розірване чи впало під час підгляду - handler сам закрив сокет
					guardedCount.decrementAndGet();
					continue;
				}

				if (ready) {
					guardedCount.decrementAndGet();
					queue.add(task);
					maybeGrow();
				} else {
					guardQueue.add(task);
				}
			}
		} finally {
			guardThreadCount.decrementAndGet();
		}
	}
}
