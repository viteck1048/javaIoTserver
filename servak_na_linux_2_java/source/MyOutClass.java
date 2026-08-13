import java.io.*;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MyOutClass extends PrintStream {
    private static MyOutClass outInstance;
    private static MyOutClass errInstance;
    private static boolean printToConsole = true;  // За замовчуванням виводимо в консоль
    private static boolean printToFile = false;     // За замовчуванням не виводимо у файл
    private static String logFileDir = "logs";
	private static String logFileName = "Servak.log";
    private static String logFilePath = logFileDir + "/" + logFileName;
    private static PrintWriter fileWriter;
    private static long maxFileSize = 3 * 1024 * 1024; // 3MB за замовчуванням
    private static int maxBackupIndex = 5; // Максимальна кількість файлів бекапу

    private MyOutClass(OutputStream out, boolean isErr) {
		super(out, true); // autoFlush = true
		if (isErr) {
			// Only initialize file writer for System.err to avoid duplicates
			if (!isInitialized) {
				initializeLogFile();
			}
		} else {
			initializeLogFile();
		}
	}
    
	// Add these class fields
	private static volatile boolean isInitialized = false;
	private static final Object initLock = new Object();

	private static void initializeLogFile() {
		if (!isInitialized) {
			synchronized (initLock) {
				if (!isInitialized) {
					try {
						File logDir = new File(logFileDir);
						if (!logDir.exists() && !logDir.mkdirs()) {
							System.err.println("Failed to create log directory: " + logFileDir);
							return;
						}
						
						File logFile = new File(logFilePath);
						if (logFile.exists()) {
							rotateLogsOnStartup();
						}
						fileWriter = new PrintWriter(new FileWriter(logFile, true));
						isInitialized = true;
					} catch (IOException e) {
						System.err.println("Error initializing log file: " + e.getMessage());
					}
				}
			}
		}
	}
    
    private static void rotateLogsOnStartup() {
        try {
            // Видаляємо найстаріший файл, якщо він існує
            File oldestFile = new File(logFilePath + "." + maxBackupIndex);
            if (oldestFile.exists()) {
                oldestFile.delete();
            }
            
            // Зсуваємо всі файли на 1 вгору
            for (int i = maxBackupIndex - 1; i >= 1; i--) {
                File oldFile = new File(logFilePath + "." + i);
                File newFile = new File(logFilePath + "." + (i + 1));
                if (oldFile.exists()) {
                    if (newFile.exists()) {
                        newFile.delete();
                    }
                    oldFile.renameTo(newFile);
                }
            }
            
            // Перейменовуємо поточний файл в .1
            File currentFile = new File(logFilePath);
            File backupFile = new File(logFilePath + ".1");
            if (currentFile.exists()) {
                if (backupFile.exists()) {
                    backupFile.delete();
                }
                currentFile.renameTo(backupFile);
            }
        } catch (Exception e) {
            System.err.println("Помилка при ротації логів при старті: " + e.getMessage());
        }
    }

    /**
     * Встановлює, чи потрібно виводити лог у консоль
     * @param enable true - виводити, false - не виводити
     */
    public static void setPrintToConsole(boolean enable) {
        printToConsole = enable;
    }
    
    /**
     * Встановлює, чи потрібно записувати лог у файл
     * @param enable true - записувати, false - не записувати
     */
    public static void setPrintToFile(boolean enable) {
        printToFile = enable;
    }

    public static void setMaxFileSize(long maxSizeMB) {
        maxFileSize = maxSizeMB * 1024 * 1024; // Конвертуємо МБ в байти
    }

    public static void setMaxBackupIndex(int maxIndex) {
        maxBackupIndex = maxIndex;
    }

    private static void rotateLogs() {
        File logFile = new File(logFilePath);
        if (logFile.length() < maxFileSize) {
            return;
        }

        // Закриваємо поточний файл
        if (fileWriter != null) {
            fileWriter.close();
        }

        try {
            // Ренеймімо існуючі файли
            for (int i = maxBackupIndex - 1; i >= 1; i--) {
                File oldFile = new File(logFilePath + "." + i);
                File newFile = new File(logFilePath + "." + (i + 1));
                if (oldFile.exists()) {
                    if (newFile.exists()) {
                        newFile.delete();
                    }
                    oldFile.renameTo(newFile);
                }
            }

            // Перейменовуємо поточний файл в .1
            File currentFile = new File(logFilePath);
            File backupFile = new File(logFilePath + ".1");
            if (currentFile.exists()) {
                if (backupFile.exists()) {
                    backupFile.delete();
                }
                currentFile.renameTo(backupFile);
            }

            // Відкриваємо новий файл для логування
            fileWriter = new PrintWriter(new FileWriter(logFilePath, true));
        } catch (IOException e) {
            System.err.println("Помилка при ротації логів: " + e.getMessage());
        }
    }

    public static void setLogFilePath(String path) {
        logFilePath = path;
        // Перевідкриваємо файловий потік з новим шляхом
        if (fileWriter != null) {
            fileWriter.close();
        }
        try {
            fileWriter = new PrintWriter(new FileWriter(logFilePath, true));
        } catch (IOException e) {
            System.err.println("Помилка при зміні шляху до файлу логу: " + e.getMessage());
        }
    }

    public static void init() {
		if (outInstance == null) {
			outInstance = new MyOutClass(System.out, false);
			System.setOut(outInstance);
		}
		if (errInstance == null) {
			errInstance = new MyOutClass(System.err, true);
			System.setErr(errInstance);
		}
	}
    /**
     * Єдина точка часової відмітки й імені потоку - раніше кожен виклик логування сам
     * форматував SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z") в місці виклику; тепер
     * це робиться тут, один раз, для всього, що йде через System.out/System.err.
     * Повідомлення довжиною <=2 символи (одиночні "тік"-маркери AVR-логування типу "."
     * чи "i") лишаються недоторканими - той самий поріг, що вже застосовує writeToFile()
     * до "." (компактний індикатор прогресу, не повноцінний рядок логу).
     * Провідний '\r' (консольні виклики так повертають курсор на початок рядка) лишається
     * першим символом результату - інакше він одразу зітре щойно доданий префікс.
     */
    private static String decorate(String s) {
        if (s == null || s.length() <= 2) {
            return s;
        }
        boolean hadCR = s.charAt(0) == '\r';
        String body = hadCR ? s.substring(1) : s;
        SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z");
        String prefix = "[" + formatter.format(new Date()) + "][" + Thread.currentThread().getName() + "] ";
        return (hadCR ? "\r" : "") + prefix + body;
    }

    private void writeToConsole(String s) {
        if (printToConsole) {
            try {
                byte[] bytes = s.getBytes();
                out.write(bytes, 0, bytes.length);
                out.flush();
            } catch (IOException e) {
                // Ігноруємо помилки виводу в консоль
            }
        }
    }
    
    // Допоміжний метод для безпечного запису у файл
    private void writeToFile(String s) {
        if (printToFile && fileWriter != null) {
			if(s.equals("."))
				return;
			else if(s.startsWith("\r"))
				s = s.substring(1);
            try {
                if (new File(logFilePath).length() >= maxFileSize) {
                    rotateLogs();
                }
                fileWriter.write(s);
                fileWriter.flush();
            } catch (Exception e) {
                // Виводимо помилку в консоль, але не в файл, щоб уникнути рекурсії
                System.err.println("Помилка запису в файл: " + e.getMessage());
            }
        }
    }

    @Override
    public void print(String s) {
		s = decorate(s);
		writeToConsole(s);
		writeToFile(s);
    }

    @Override
    public void println() {
        String s = decorate("\n");
        writeToConsole(s);
		writeToFile(s);
    }

    @Override
    public void println(String x) {
        String s = decorate(x + "\n");
        writeToConsole(s);
		writeToFile(s);
    }

    @Override
    public void println(Object x) {
        String s = decorate(x.toString() + "\n");
        writeToConsole(s);
		writeToFile(s);
    }

    @Override
    public PrintStream printf(String format, Object... args) {
        String s = decorate(String.format(format, args));
        writeToConsole(s);
		writeToFile(s);
		return this;
    }
/*    
    @Override
    public PrintStream printf(Locale l, String format, Object... args) {
        if (printToConsole) {
            super.printf(l, format, args);
        }
        if (printToFile && fileWriter != null) {
            try {
                if (new File(logFilePath).length() >= maxFileSize) {
                    rotateLogs();
                }
                fileWriter.printf(l, format, args);
                fileWriter.flush();
            } catch (Exception e) {
                System.err.println("Error writing to log file: " + e.getMessage());
            }
        }
        return this;
    }

    @Override
    public PrintStream format(String format, Object... args) {
        if (printToConsole) {
            super.format(format, args);
        }
        if (printToFile && fileWriter != null) {
            fileWriter.format(format, args);
            fileWriter.flush();
        }
        return this;
    }
    
    @Override
    public PrintStream format(Locale l, String format, Object... args) {
        if (printToConsole) {
            super.format(l, format, args);
        }
        if (printToFile && fileWriter != null) {
            fileWriter.format(l, format, args);
            fileWriter.flush();
        }
        return this;
    }
*/
    @Override
    public void close() {
        if (fileWriter != null) {
            fileWriter.close();
        }
        super.close();
    }

    /**
     * Встановлює максимальний розмір лог-файлу в мегабайтах
     * @param maxSizeMB максимальний розмір в МБ
     */
    public static void setMaxLogSizeMB(long maxSizeMB) {
        maxFileSize = maxSizeMB * 1024 * 1024;
    }

    /**
     * Встановлює кількість файлів бекапу
     * @param maxIndex кількість файлів бекапу
     */
    public static void setMaxBackupFiles(int maxIndex) {
        maxBackupIndex = Math.max(1, maxIndex); // Мінімум 1 файл бекапу
    }

    // Додаткові методи для зручності
    /**
     * Вмикає/вимикає вивід у консоль
     * @param enable true - увімкнути, false - вимкнути
     */
    public static void setConsoleOutput(boolean enable) {
        printToConsole = enable;
    }
    
    /**
     * Вмикає/вимикає запис у файл
     * @param enable true - увімкнути, false - вимкнути
     */
    public static void setFileOutput(boolean enable) {
        printToFile = enable;
    }
    
    /**
     * Зручний метод для налаштування виводу
     * @param console true - виводити в консоль
     * @param file true - записувати у файл
     */
    public static void setOutput(boolean console, boolean file) {
        printToConsole = console;
        printToFile = file;
    }
    
    // Метод для закриття ресурсів
    public static void closeAll() {
        if (outInstance != null) {
            outInstance.close();
        }
        if (errInstance != null) {
            errInstance.close();
        }
    }
}
