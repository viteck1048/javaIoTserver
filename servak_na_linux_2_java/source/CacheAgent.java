/**
 * Агент для автоматичної очистки кешу файлів та директорій
 * Очищає кеш кожні N хвилин та застарілі сесії користувачів
 */
public class CacheAgent implements Runnable {
    private volatile boolean running = true;
    private long cacheTimeoutMs = 10 * 60 * 1000; // 10 хвилин дефолтно
    private boolean enabled = true;

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

                // Очищаємо кеш файлів
                FileCacheManager.cleanupCache();

                // Очищаємо застарілі ключі в KeyManager
                KeyManager.cleanUpExpiredKeys();

            } catch (InterruptedException e) {
                running = false;
                Thread.currentThread().interrupt();
            }
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
