import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class LogFiles {
    private static class LogEntry {
        private final long timestamp;
        private final String log;

        public LogEntry(long timestamp, String log) {
            this.timestamp = timestamp;
            this.log = log;
        }
    }

    private static class DeviceLogs {
        private final long sn_mega;
        private final List<LogEntry> logs = new ArrayList<>();

        public DeviceLogs(long sn_mega) {
            this.sn_mega = sn_mega;
        }
    }

    private static final Map<Long, DeviceLogs> logsByDevice = new ConcurrentHashMap<>();
    private static final long THREE_DAYS_IN_SECONDS = 3 * 24 * 60 * 60L;

    public static synchronized void pushLog(long sn_mega, int relayIndex, int setimp, int tekimp, long runtime) {
        // Get or create device logs
        DeviceLogs deviceLogs = logsByDevice.computeIfAbsent(sn_mega, DeviceLogs::new);
        
        // Create log entry
        long currentTime = Instant.now().getEpochSecond();
        String timeStr = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM HH:mm:ss"));
        
        String logEntry = String.format("<p>%s - ri_%d, set %d, tek %d, маш.време %dsec</p>", timeStr, relayIndex, setimp, tekimp, runtime);
        
        String logEntry2 = String.format("\r%s G_ID = %d - ri_%d, set %d, tek %d, маш.време %dsec", timeStr, deviceLogs.sn_mega, relayIndex, setimp, tekimp, runtime);
        System.out.println(logEntry2);
        // Add new log entry
        deviceLogs.logs.add(new LogEntry(currentTime, logEntry));
        
        // Clean up old entries (older than 3 days)
        cleanUpOldEntries(deviceLogs, currentTime);
    }

    public static synchronized void pushLog(long sn_mega, long obscht_r, long tek_r, long runtime, String msg) {
        // Get or create device logs
        DeviceLogs deviceLogs = logsByDevice.computeIfAbsent(sn_mega, DeviceLogs::new);
        
        // Create log entry
        long currentTime = Instant.now().getEpochSecond();
        String timeStr = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM HH:mm:ss"));
        
        String logEntry = String.format("<p>%s - %s, obscht_r %d, tek_r %d, obscht_r DB %dsec</p>", timeStr, msg, obscht_r, tek_r, runtime);
        
        String logEntry2 = String.format("\r%s G_ID = %d - %s, obscht_r %d, tek_r %d, obscht_r DB %dsec", timeStr, deviceLogs.sn_mega, msg, obscht_r, tek_r, runtime);
        System.out.println(logEntry2);
        // Add new log entry
        deviceLogs.logs.add(new LogEntry(currentTime, logEntry));
        
        // Clean up old entries (older than 3 days)
        cleanUpOldEntries(deviceLogs, currentTime);
    }

    public static synchronized void pushLog(long sn_mega, String msg) {
        // Get or create device logs
        DeviceLogs deviceLogs = logsByDevice.computeIfAbsent(sn_mega, DeviceLogs::new);

        // Create log entry
        long currentTime = Instant.now().getEpochSecond();
        String timeStr = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM HH:mm:ss"));
        
        String logEntry = String.format("<pre>%s - %s</pre>", timeStr, msg);
        String logEntry2 = String.format("\r%s G_ID = %d - %s", timeStr, deviceLogs.sn_mega, msg);
        System.out.println(logEntry2);
        // Add new log entry
        deviceLogs.logs.add(new LogEntry(currentTime, logEntry));
        
        // Clean up old entries (older than 3 days)
        cleanUpOldEntries(deviceLogs, currentTime);
    }
        
    public static synchronized String getLog(long sn_mega) {
        // Clean up devices with logs older than 2 days
        cleanupOldDevices();
        
        DeviceLogs deviceLogs = logsByDevice.get(sn_mega);
        if (deviceLogs == null || deviceLogs.logs.isEmpty()) {
            return "";
        }
        
        // Clean up old entries before returning
        cleanUpOldEntries(deviceLogs, Instant.now().getEpochSecond());
        
        // Build result string
        StringBuilder result = new StringBuilder();
        for (LogEntry entry : deviceLogs.logs) {
            result.append(entry.log);
        }
        return result.toString();
    }

    private static void cleanUpOldEntries(DeviceLogs deviceLogs, long currentTime) {
        deviceLogs.logs.removeIf(entry -> 
            (currentTime - entry.timestamp) > THREE_DAYS_IN_SECONDS
        );
    }
    
    private static void cleanupOldDevices() {
        long currentTime = Instant.now().getEpochSecond();
        long twoDaysInSeconds = 2 * 24 * 60 * 60L;
        
        // Create a list of device IDs to remove
        List<Long> devicesToRemove = new ArrayList<>();
        
        for (Map.Entry<Long, DeviceLogs> entry : logsByDevice.entrySet()) {
            DeviceLogs deviceLogs = entry.getValue();
            
            // If device has no logs, mark it for removal
            if (deviceLogs.logs.isEmpty()) {
                devicesToRemove.add(entry.getKey());
                continue;
            }
            
            // Get the most recent log entry
            long lastLogTime = deviceLogs.logs.stream()
                .mapToLong(e -> e.timestamp)
                .max()
                .orElse(0);
                
            // If last log is older than 2 days, mark for removal
            if ((currentTime - lastLogTime) > twoDaysInSeconds) {
                devicesToRemove.add(entry.getKey());
            }
        }
        
        // Remove all marked devices
        for (Long deviceId : devicesToRemove) {
            logsByDevice.remove(deviceId);
        }
    }
}
