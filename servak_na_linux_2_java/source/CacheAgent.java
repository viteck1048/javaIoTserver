/**
 * Агент періодичного прибирання: витісняє з кешу давно не запитувані файли,
 * застарілі сесії та протухлі записи бан-списку.
 *
 * Актуальністю кешу агент НЕ займається: змінений на диску файл виявляє і
 * перечитує сам FileCacheManager у момент віддачі. Тут — лише повернення пам'яті.
 */
public class CacheAgent implements Runnable {
    private volatile boolean running = true;
    private long cacheTimeoutMs = 10 * 60 * 1000; // 10 хвилин дефолтно
    private boolean enabled = true;
    /** null, коли workerpool=false - тоді reconcile-крок просто пропускається */
    private WorkerPool workerPool;

    public void setWorkerPool(WorkerPool workerPool) {
        this.workerPool = workerPool;
    }

    /**
     * Ініціалізація налаштувань агента з конфігурації
     */
    public void initFromConfig() {
        // Перевіряємо чи ввімкнений агент кешу
        if (Configs.getDefine("runCacheAgent")) {
            enabled = Configs.getBoolean("runCacheAgent");
        }

        // Отримуємо таймаут кешу в секундах і конвертуємо в мілісекунди
        if (Configs.getDefine("timeCacheAgent")) {
            int timeoutSeconds = Configs.getInt("timeCacheAgent");
            if (timeoutSeconds > 0) {
                cacheTimeoutMs = timeoutSeconds * 1000L;
            }
        }

        System.out.println("CacheAgent initialized: enabled=" + enabled + ", timeout=" + (cacheTimeoutMs / 1000) + "s");
    }

    @Override
    public void run() {
        if (!enabled) {
            System.out.println("CacheAgent disabled, skipping execution");
            return;
        }

        while (running) {
            try {
                Thread.sleep(cacheTimeoutMs);
            } catch (InterruptedException e) {
                running = false;
                Thread.currentThread().interrupt();
                break;
            }

            // Кожну задачу ізолюємо: збій в одній не має валити агента цілком
            runQuietly("FileCacheManager.evictStale", FileCacheManager::evictStale);
            runQuietly("KeyManager.cleanUpExpiredKeys", KeyManager::cleanUpExpiredKeys);
            runQuietly("FirewallIP.cleanupBlackList", FirewallIP::cleanupBlackList);
            if (workerPool != null) {
                runQuietly("WorkerPool.reconcile", workerPool::reconcile);
            }
        }
    }

    private static void runQuietly(String name, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            System.err.println("CacheAgent: " + name + " failed - " + e);
        }
    }

    public void stop() {
        running = false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getCacheTimeoutMs() {
        return cacheTimeoutMs;
    }
}
