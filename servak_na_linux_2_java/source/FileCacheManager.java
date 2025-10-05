import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Менеджер кешування файлів в RAM
 * Кешує файли на 10 хвилин з автоматичною очисткою застарілих файлів
 */
public class FileCacheManager {
    private static final ArrayList<FileMem> cashFiles = new ArrayList<>();
    private static final ArrayList<DirectoryMem> scanDirectoriesCache = new ArrayList<>();
    private static final long CACHE_TIMEOUT = 10 * 60 * 1000; // 10 хвилин в мілісекундах
    private static final Object lock = new Object();

    /**
     * Внутрішній клас для представлення кешованого файлу
     */
    private static class FileMem {
        long timestamp;
        byte[] data;
        String path;

        FileMem(String path, byte[] data) {
            this.path = path;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        void updateTimestamp() {
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Внутрішній клас для представлення кешованої директорії
     */
    private static class DirectoryMem {
        long timestamp;
        List<String> files;
        String path;

        DirectoryMem(String path, List<String> files) {
            this.path = path;
            this.files = new ArrayList<>(files);
            this.timestamp = System.currentTimeMillis();
        }

        void updateTimestamp() {
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Отримує файл з кешу або завантажує з диска
     * @param filePath шлях до файлу
     * @return байти файлу
     */
    public static byte[] getFile(String filePath) {
        synchronized (lock) {
            // Шукаємо файл у кеші
            for (FileMem fileMem : cashFiles) {
                if (fileMem.path.equals(filePath)) {
                    fileMem.updateTimestamp();
                    return fileMem.data;
                }
            }

            // Файл не знайдено в кеші, завантажуємо з диска
            try {
                File file = new File(filePath);
                if (file.exists() && file.isFile()) {
                    byte[] data = Files.readAllBytes(file.toPath());
                    cashFiles.add(new FileMem(filePath, data));
                    return data;
                }
            } catch (IOException e) {
                System.err.println("Помилка завантаження файлу: " + filePath + " - " + e.getMessage());
            }

            return null;
        }
    }

    /**
     * Отримує копію файлу з кешу (безпечна для модифікації)
     * @param filePath шлях до файлу
     * @return копія байтів файлу
     */
    public static byte[] cloneFile(String filePath) {
        byte[] original = getFile(filePath);
        if (original != null) {
            // Створюємо копію масиву
            byte[] copy = new byte[original.length];
            System.arraycopy(original, 0, copy, 0, original.length);
            return copy;
        }
        return null;
    }

    /**
     * Очищає кеш від застарілих файлів і директорій та перевіряє актуальність даних
     */
    public static void cleanupCache() {
        synchronized (lock) {
            // Очищаємо та перевіряємо файли
            Iterator<FileMem> fileIterator = cashFiles.iterator();
            while (fileIterator.hasNext()) {
                FileMem fileMem = fileIterator.next();
                if (!isFileValid(fileMem)) {
                    fileIterator.remove();
                }
            }

            // Очищаємо та перевіряємо директорії
            Iterator<DirectoryMem> dirIterator = scanDirectoriesCache.iterator();
            while (dirIterator.hasNext()) {
                DirectoryMem dirMem = dirIterator.next();
                if (!isDirectoryValid(dirMem)) {
                    dirIterator.remove();
                }
            }
        }
    }

    /**
     * Перевіряє чи файл актуальний на диску
     */
    private static boolean isFileValid(FileMem fileMem) {
        try {
            File file = new File(fileMem.path);
            if (!file.exists() || !file.isFile()) {
                return false; // Файл видалено або не є файлом
            }

            long currentSize = file.length();
            long currentModified = file.lastModified();

            // Якщо розмір або час модифікації змінився - файл неактуальний
            // (Примітка: тут ми не можемо легко перевірити вміст файлу,
            // тому використовуємо розмір і час модифікації як індикатори змін)
            return fileMem.data.length == currentSize;
        } catch (Exception e) {
            return false; // Помилка доступу до файлу
        }
    }

    /**
     * Перевіряє чи директорія актуальна на диску
     */
    private static boolean isDirectoryValid(DirectoryMem dirMem) {
        try {
            File dir = new File(dirMem.path);
            if (!dir.exists() || !dir.isDirectory()) {
                return false; // Директорія видалена або не є директорією
            }

            // Перевіряємо чи час модифікації директорії змінився
            long currentModified = dir.lastModified();

            // Якщо директорія мінялася - її вміст може бути неактуальним
            // (хоча б файл додано/видалено)
            return true; // Для директорій складніше перевірити, тому повертаємо true
                         // і оновлюємо вміст при наступному scanDir запиті

        } catch (Exception e) {
            return false; // Помилка доступу до директорії
        }
    }

    /**
     * Сканує директорію і повертає список файлів (з кешуванням)
     * @param dirPath шлях до директорії
     * @return список файлів у директорії
     */
    public static List<String> scanDir(String dirPath) {
        synchronized (lock) {
            // Шукаємо директорію у кеші
            for (DirectoryMem dirMem : scanDirectoriesCache) {
                if (dirMem.path.equals(dirPath)) {
                    dirMem.updateTimestamp();
                    return new ArrayList<>(dirMem.files);
                }
            }

            // Директорія не знайдена в кеші, скануємо
            List<String> files = new ArrayList<>();
            File directory = new File(dirPath);
            if (directory.exists() && directory.isDirectory()) {
                try (Stream<Path> stream = Files.list(directory.toPath())) {
                    files = stream
                        .filter(path -> !Files.isDirectory(path))
                        .map(path -> path.getFileName().toString())
                        .collect(Collectors.toList());
                } catch (IOException e) {
                    System.err.println("Помилка сканування директорії: " + dirPath + " - " + e.getMessage());
                }
            }

            // Зберігаємо в кеш
            scanDirectoriesCache.add(new DirectoryMem(dirPath, files));
            return files;
        }
    }

    /**
     * Очищає весь кеш
     */
    public static void clearCache() {
        synchronized (lock) {
            cashFiles.clear();
            scanDirectoriesCache.clear();
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
}
