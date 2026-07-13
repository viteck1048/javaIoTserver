import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Менеджер кешування файлів в RAM.
 *
 * Кеш когерентний: актуальність запису перевіряється в момент віддачі — звіркою
 * mtime і розміру файлу на диску, а не фоновим обходом директорій. Змінений на
 * диску файл витісняється і перечитується при першому ж запиті до нього. Поки
 * файл ніхто не просить — жоден stat не виконується.
 *
 * CacheAgent додатково витісняє записи, до яких давно не зверталися. Для
 * коректності це не потрібно (її забезпечує звірка при віддачі) — лише щоб
 * повертати пам'ять.
 */
public class FileCacheManager {
	private static final Map<String, FileMem> cashFiles = new HashMap<>();
	private static final Map<String, DirectoryMem> scanDirectoriesCache = new HashMap<>();
	private static final Object lock = new Object();

	/** Скільки запис живе без звернень, поки його не витіснить CacheAgent (сек) */
	private static final long IDLE_TIMEOUT_MS =
		Configs.getDefine("cacheIdleTime") ? Configs.getLong("cacheIdleTime") * 1000L : 30 * 60 * 1000L;

	/** Ліміт сумарного обсягу кешу файлів (байт) */
	private static final long MAX_CACHE_BYTES =
		Configs.getDefine("maxCacheSize") ? Configs.getLong("maxCacheSize") : 64L * 1024 * 1024;

	/**
	 * Файли, більші за це, не кешуються взагалі — віддаються з диска повз кеш.
	 * Без цього ліміту один .apk із www/download/files/ (146 МБ) назавжди осідає в heap.
	 */
	private static final long MAX_ENTRY_BYTES =
		Configs.getDefine("maxCacheFileSize") ? Configs.getLong("maxCacheFileSize") : 8L * 1024 * 1024;

	/** Сумарний обсяг cashFiles. Змінюється лише під lock. */
	private static long cacheBytes = 0;

	private static class FileMem {
		final String path;
		final byte[] data;
		final long size;   // розмір на момент читання
		final long mtime;  // lastModified на момент читання
		long lastAccess;

		FileMem(String path, byte[] data, long size, long mtime) {
			this.path = path;
			this.data = data;
			this.size = size;
			this.mtime = mtime;
			this.lastAccess = System.currentTimeMillis();
		}
	}

	private static class DirectoryMem {
		final String path;
		final List<String> files;
		final long mtime;  // lastModified директорії на момент сканування
		long lastAccess;

		DirectoryMem(String path, List<String> files, long mtime) {
			this.path = path;
			this.files = new ArrayList<>(files);
			this.mtime = mtime;
			this.lastAccess = System.currentTimeMillis();
		}
	}

	/**
	 * Отримує файл з кешу або завантажує з диска.
	 * Якщо файл на диску змінився з моменту кешування — перечитує його.
	 *
	 * @param filePath шлях до файлу
	 * @return байти файлу або null, якщо файлу немає / не прочитався
	 */
	public static byte[] getFile(String filePath) {
		File file = new File(filePath);

		// Один stat поза локом. Читається з inode-кешу ядра, диска не торкається.
		long mtime = file.lastModified();
		long size = file.length();

		if (mtime == 0L || !file.isFile()) {
			synchronized (lock) {
				dropFile(filePath);  // файл зник — прибираємо з кешу
			}
			return null;
		}

		synchronized (lock) {
			FileMem mem = cashFiles.get(filePath);
			if (mem != null) {
				if (mem.mtime == mtime && mem.size == size) {
					mem.lastAccess = System.currentTimeMillis();
					return mem.data;
				}
				dropFile(filePath);  // змінився на диску
			}
		}

		// Читаємо ПОЗА локом: повільний диск не має блокувати всі інші запити.
		byte[] data;
		try {
			data = Files.readAllBytes(file.toPath());
		} catch (IOException e) {
			System.err.println("Помилка завантаження файлу: " + filePath + " - " + e.getMessage());
			return null;
		}

		if (data.length > MAX_ENTRY_BYTES) {
			return data;  // завеликий — віддаємо, але не кешуємо
		}

		// Якщо файл правили саме поки ми його читали — дані можуть бути рваними, не кешуємо.
		if (file.lastModified() != mtime || file.length() != size) {
			return data;
		}

		synchronized (lock) {
			dropFile(filePath);  // раптом інший потік уже поклав
			cashFiles.put(filePath, new FileMem(filePath, data, size, mtime));
			cacheBytes += data.length;
			enforceSizeLimit();
		}
		return data;
	}

	/**
	 * Отримує копію файлу з кешу (безпечна для модифікації)
	 * @param filePath шлях до файлу
	 * @return копія байтів файлу
	 */
	public static byte[] cloneFile(String filePath) {
		byte[] original = getFile(filePath);
		if (original != null) {
			byte[] copy = new byte[original.length];
			System.arraycopy(original, 0, copy, 0, original.length);
			return copy;
		}
		return null;
	}

	/**
	 * Сканує директорію і повертає список файлів (з кешуванням).
	 * Якщо в директорії щось додали/видалили/перейменували — її mtime змінюється,
	 * і список пересканується.
	 *
	 * Порядок: свіжіші файли першими (за mtime), як стрічка новин — закинув сторінку
	 * в директорію, вона сама вилізла нагору. За однакового mtime — за іменем, щоб
	 * порядок був детермінованим. Files.list() власного порядку не гарантує: він
	 * віддає елементи так, як їх повертає readdir(), тобто в порядку htree-хешу імені.
	 *
	 * @param dirPath шлях до директорії
	 * @return список імен файлів у директорії, свіжіші першими
	 */
	public static List<String> scanDir(String dirPath) {
		File directory = new File(dirPath);
		long mtime = directory.lastModified();

		if (mtime == 0L || !directory.isDirectory()) {
			synchronized (lock) {
				scanDirectoriesCache.remove(dirPath);
			}
			return new ArrayList<>();
		}

		synchronized (lock) {
			DirectoryMem dirMem = scanDirectoriesCache.get(dirPath);
			if (dirMem != null) {
				if (dirMem.mtime == mtime) {
					dirMem.lastAccess = System.currentTimeMillis();
					return new ArrayList<>(dirMem.files);
				}
				scanDirectoriesCache.remove(dirPath);  // вміст директорії змінився
			}
		}

		List<String> files = new ArrayList<>();
		try (Stream<Path> stream = Files.list(directory.toPath())) {
			List<File> entries = stream
				.map(Path::toFile)
				.filter(File::isFile)
				.collect(Collectors.toList());

			// mtime знімаємо один раз на файл, а не на кожне порівняння всередині сортування
			Map<File, Long> mtimes = new HashMap<>();
			for (File entry : entries) {
				mtimes.put(entry, entry.lastModified());
			}

			entries.sort(Comparator
				.comparingLong((File entry) -> mtimes.get(entry)).reversed()
				.thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));

			for (File entry : entries) {
				files.add(entry.getName());
			}
		} catch (IOException e) {
			System.err.println("Помилка сканування директорії: " + dirPath + " - " + e.getMessage());
			return files;
		}

		synchronized (lock) {
			scanDirectoriesCache.put(dirPath, new DirectoryMem(dirPath, files, mtime));
		}
		return new ArrayList<>(files);
	}

	/**
	 * Витісняє записи, до яких давно не зверталися, і тримає обсяг кешу в межах ліміту.
	 * Викликається періодично з CacheAgent.
	 *
	 * Актуальність записів тут НЕ перевіряється — це робить getFile()/scanDir()
	 * при кожній віддачі.
	 */
	public static void evictStale() {
		long now = System.currentTimeMillis();
		synchronized (lock) {
			Iterator<Map.Entry<String, FileMem>> fileIterator = cashFiles.entrySet().iterator();
			while (fileIterator.hasNext()) {
				FileMem fileMem = fileIterator.next().getValue();
				if (now - fileMem.lastAccess > IDLE_TIMEOUT_MS) {
					cacheBytes -= fileMem.data.length;
					fileIterator.remove();
				}
			}

			scanDirectoriesCache.entrySet().removeIf(
				entry -> now - entry.getValue().lastAccess > IDLE_TIMEOUT_MS);

			enforceSizeLimit();
		}
	}

	/**
	 * Повністю очищає кеш. Ручний виклик з /www_scripts/clear_cache.
	 * @return скільки записів було викинуто (файли + директорії)
	 */
	public static int clearCache() {
		synchronized (lock) {
			int removed = cashFiles.size() + scanDirectoriesCache.size();
			cashFiles.clear();
			scanDirectoriesCache.clear();
			cacheBytes = 0;
			return removed;
		}
	}

	/** Викидає найдавніше запитуваний файл, поки кеш не влізе в MAX_CACHE_BYTES. Лише під lock. */
	private static void enforceSizeLimit() {
		while (cacheBytes > MAX_CACHE_BYTES && !cashFiles.isEmpty()) {
			String lruPath = null;
			long oldest = Long.MAX_VALUE;
			for (FileMem fileMem : cashFiles.values()) {
				if (fileMem.lastAccess < oldest) {
					oldest = fileMem.lastAccess;
					lruPath = fileMem.path;
				}
			}
			if (lruPath == null) {
				break;
			}
			dropFile(lruPath);
		}
	}

	/** Прибирає файл з кешу, не забувши списати його байти. Лише під lock. */
	private static void dropFile(String path) {
		FileMem removed = cashFiles.remove(path);
		if (removed != null) {
			cacheBytes -= removed.data.length;
		}
	}

	/**
	 * Повертає поточний розмір кешу файлів
	 */
	public static int getFileCacheSize() {
		synchronized (lock) {
			return cashFiles.size();
		}
	}

	/**
	 * Повертає поточний розмір кешу директорій
	 */
	public static int getDirectoryCacheSize() {
		synchronized (lock) {
			return scanDirectoriesCache.size();
		}
	}

	/**
	 * Повертає сумарний обсяг закешованих файлів у байтах
	 */
	public static long getCacheBytes() {
		synchronized (lock) {
			return cacheBytes;
		}
	}
}
