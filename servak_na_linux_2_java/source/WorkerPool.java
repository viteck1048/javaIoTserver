import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
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
 * малій фіксованій кількості потоків, які по черзі роблять короткий
 * pollForNextRequest() на кожному з'єднанні, що чекає наступного запиту, і
 * повертають задачу в queue лише коли там справді щось з'явилось. Так
 * відкрите-але-мовчазне з'єднання не займає слот із MAX_WORKERS.
 */
public class WorkerPool {

	private static final int MIN_WORKERS =
		Configs.getDefine("workerPoolMin") ? Configs.getInt("workerPoolMin") : 20;
	private static final int MAX_WORKERS =
		Configs.getDefine("workerPoolMax") ? Configs.getInt("workerPoolMax") : 500;
	/** Верхня межа частки вільних потоків, понад яку зайвий воркер іде на вихід */
	private static final double IDLE_TARGET_HIGH =
		Configs.getDefine("workerPoolIdleHigh") ? Configs.getLong("workerPoolIdleHigh") / 100.0 : 0.50;
	/** Скільки мс стан "усі зайняті" має протриматись безперервно, перш ніж народиться новий воркер */
	private static final long GROWTH_DEBOUNCE_MS =
		Configs.getDefine("workerPoolGrowthDebounceMs") ? Configs.getLong("workerPoolGrowthDebounceMs") : 500;
	/** Скільки мс воркер чекає на чергове завдання, перш ніж розглянути власний вихід */
	private static final long WORKER_IDLE_TIMEOUT_MS =
		Configs.getDefine("workerPoolIdleTimeoutMs") ? Configs.getLong("workerPoolIdleTimeoutMs") : 5000;
	/** Кількість вартових потоків keep-alive - фіксована, не росте/не всихає як воркери */
	private static final int KEEP_ALIVE_GUARD_THREADS =
		Configs.getDefine("workerPoolKeepAliveGuardThreads") ? Configs.getInt("workerPoolKeepAliveGuardThreads") : 2;
	/**
	 * Скільки мс вартовий чекає відповіді від одного сокета, перш ніж перейти до наступного
	 * в черзі. Це не швидкість, з якою ОС здатна відповісти "нема даних" (той read() під
	 * капотом падає на select()/poll(), і там відповідь видно за мікросекунди) - це чисто
	 * навмисна пауза, щоб не бомбити сисколами. Ціна заниженої межі мала (кожен "порожній"
	 * read() дешевий), а ціна завищеної - лінійна: за N задач на одного вартового найгірша
	 * затримка пробудження ~ N * це число.
	 */
	private static final int KEEP_ALIVE_POLL_TIMEOUT_MS =
		Configs.getDefine("workerPoolKeepAlivePollTimeoutMs") ? Configs.getInt("workerPoolKeepAlivePollTimeoutMs") : 5;

	private final BlockingQueue<Task> queue = new LinkedBlockingQueue<>();
	private final BlockingQueue<Task> guardQueue = new LinkedBlockingQueue<>();
	private final AtomicInteger totalWorkers = new AtomicInteger(0);
	private final AtomicInteger idleWorkers = new AtomicInteger(0);
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
		for (int i = 0; i < KEEP_ALIVE_GUARD_THREADS; i++) {
			Thread t = new Thread(this::guardLoop, "worker-pool-keepalive-guard-" + i);
			t.setDaemon(true);
			t.start();
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
		totalWorkers.incrementAndGet();
		idleWorkers.incrementAndGet();
		Thread t = new Thread(this::workerLoop, "worker-pool-" + totalWorkers.get());
		t.setDaemon(true);
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
				} finally {
					idleWorkers.incrementAndGet();
				}
			}
		} finally {
			totalWorkers.decrementAndGet();
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
		guardQueue.add(task);
	}

	/**
	 * Тіло вартового потоку: по черзі бере задачі з guardQueue, коротко
	 * перевіряє кожну (pollForNextRequest) і або повертає в основну queue
	 * (з'явились дані - хай забирає воркер), або кладе назад у guardQueue
	 * чекати далі. guardQueue.take() блокується, коли вартувати нема кого -
	 * жодного busy-loop, коли активних keep-alive з'єднань немає.
	 */
	private void guardLoop() {
		while (true) {
			Task task;
			try {
				task = guardQueue.take();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}

			boolean ready;
			try {
				ready = task.handler.pollForNextRequest(KEEP_ALIVE_POLL_TIMEOUT_MS);
			} catch (IOException e) {
				// з'єднання розірване чи впало під час підгляду - handler сам закрив сокет
				continue;
			}

			if (ready) {
				queue.add(task);
				maybeGrow();
			} else {
				guardQueue.add(task);
			}
		}
	}
}
