import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Статичний клас Firewall для фільтрації мережевих запитів
 * Складається з двох модулів: IP-фільтр та PHP-файрвол
 */
public class Firewall {

    // Конфігурація firewall
    private static final long BLACKLIST_EXPIRATION_SECONDS = Configs.getDefine("ipBanLifeTime") ? Configs.getLong("ipBanLifeTime") : 3600;
    private static final int STATISTICS_COLLECTION_THRESHOLD = Configs.getDefine("quantToTriger") ? Configs.getInt("quantToTriger") : 5;

    // Потокобезпечна мапа для зберігання заблокованих IP-адрес з часом блокування
    private static final Map<InetAddress, Instant> blackList = new ConcurrentHashMap<>();

    // Потокобезпечна мапа для проміжної статистики атак (IP -> кількість атак)
    private static final Map<InetAddress, AtomicInteger> attackStatistics = new ConcurrentHashMap<>();

    // Списки шляхів для фільтрації запитів
    private static final List<String> whitePathList = new ArrayList<>();
    private static final List<String> blackPathList = new ArrayList<>();

    // Дані для блокування по країнах
    private static final List<String> bannedCountries = new ArrayList<>();
    private static final List<IP4Range> ipv4CountryRanges = new ArrayList<>();
    private static final List<IP6Range> ipv6CountryRanges = new ArrayList<>();

    // Статичний блок ініціалізації для завантаження списків шляхів
    static {
        System.out.println("FIREWALL: Ініціалізація firewall...");
        loadPathLists();
        loadBannedCountries();
        System.out.println("FIREWALL: Ініціалізацію завершено");
    }

    /**
     * Завантажує списки білих і чорних шляхів з файлів
     */
    private static void loadPathLists() {
        //System.out.println("FIREWALL: Завантаження списків шляхів...");
        
        if(Configs.getDefine("whitePathList")) {
        // Завантажуємо білий список з файлу
            String whiteFilePath = Configs.getParam("whitePathList");
            if (whiteFilePath != null && !whiteFilePath.trim().isEmpty()) {
                System.out.print("FIREWALL: Завантаження білого списку з файлу: " + whiteFilePath + "...");
                loadPathsFromFile(whiteFilePath, whitePathList);
                System.out.println(" Білий список завантажено, елементів: " + whitePathList.size());
            } else {
                System.out.println("FIREWALL: Білий список не налаштовано");
            }
        } else {
            System.out.println("FIREWALL: Білий список вимкнено");
        }
        
        if(Configs.getDefine("blackPathList")) {
            // Завантажуємо чорний список з файлу
            String blackFilePath = Configs.getParam("blackPathList");
            if (blackFilePath != null && !blackFilePath.trim().isEmpty()) {
                System.out.print("FIREWALL: Завантаження чорного списку з файлу: " + blackFilePath + "...");
                loadPathsFromFile(blackFilePath, blackPathList);
                System.out.println(" Чорний список завантажено, елементів: " + blackPathList.size());
            } else {
                System.out.println("FIREWALL: Чорний список не налаштовано");
            }
        } else {
            System.out.println("FIREWALL: Чорний список вимкнено");
        }
    }

    /**
     * Завантажує список заблокованих країн та їх IP діапазони
     */
    private static void loadBannedCountries() {
        if (!Configs.getDefine("countriesBan")) {
            System.out.println("FIREWALL: Блокування по країнах вимкнено");
            return;
        }

        System.out.print("FIREWALL: Завантаження списку заблокованих країн...");
        String countriesConfig = Configs.getParam("countriesBan");
        if (countriesConfig == null || countriesConfig.trim().isEmpty()) {
            System.out.println("FIREWALL: Список країн не налаштовано");
            return;
        }

        String[] countries = countriesConfig.split(",");
        System.out.println(" Знайдено " + countries.length + " країн для блокування");
        
        for (String country : countries) {
            String trimmedCountry = country.trim().toLowerCase();
            if (!trimmedCountry.isEmpty()) {
                System.out.println("FIREWALL: Завантаження IP діапазонів для країни: " + trimmedCountry.toUpperCase());
                bannedCountries.add(trimmedCountry);
                loadCountryIPRanges(trimmedCountry);
            }
        }
        
        System.out.println("FIREWALL: Завантажено діапазонів IPv4: " + ipv4CountryRanges.size() + ", IPv6: " + ipv6CountryRanges.size());
    }

    /**
     * Завантажує IP діапазони для конкретної країни з GitHub
     * @param countryCode двобуквений код країни
     */
    private static void loadCountryIPRanges(String countryCode) {
        String url = "https://raw.githubusercontent.com/ipverse/rir-ip/master/country/" + countryCode + "/aggregated.json";
        
        try {
            String jsonContent = fetchJsonFromUrl(url);
            parseCountryIPRanges(jsonContent, countryCode);
        } catch (Exception e) {
            System.err.println("FIREWALL: Помилка завантаження IP діапазонів для країни " + countryCode.toUpperCase() + ": " + e.getMessage());
        }
    }

    /**
     * Завантажує JSON контент з URL
     * @param urlString URL для завантаження
     * @return JSON контент як рядок
     */
    private static String fetchJsonFromUrl(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        }
    }

    /**
     * Парсить JSON з IP діапазонами країни
     * @param jsonContent JSON контент
     * @param countryCode код країни для логування помилок
     */
    private static void parseCountryIPRanges(String jsonContent, String countryCode) {
        try {
            // Простий парсинг JSON (можна замінити на повноцінний JSON парсер)
            Pattern ipv4Pattern = Pattern.compile("\"ipv4\"\\s*:\\s*\\[([^\\]]+)\\]");
            Pattern ipv6Pattern = Pattern.compile("\"ipv6\"\\s*:\\s*\\[([^\\]]+)\\]");

            Matcher ipv4Matcher = ipv4Pattern.matcher(jsonContent);
            if (ipv4Matcher.find()) {
                String ipv4Ranges = ipv4Matcher.group(1);
                //System.out.println("FIREWALL: Знайдено IPv4 діапазони для " + countryCode.toUpperCase() + ": " + ipv4Ranges.split(",").length + " діапазонів");
                parseCIDRRanges(ipv4Ranges, true, countryCode);
            } else {
                System.out.println("FIREWALL: IPv4 діапазони не знайдені для " + countryCode.toUpperCase());
            }

            Matcher ipv6Matcher = ipv6Pattern.matcher(jsonContent);
            if (ipv6Matcher.find()) {
                String ipv6Ranges = ipv6Matcher.group(1);
                String[] ipv6RangeArray = ipv6Ranges.split(",");
                //System.out.println("FIREWALL: Знайдено IPv6 діапазони для " + countryCode.toUpperCase() + ": " + ipv6RangeArray.length + " діапазонів");
                //System.out.println("FIREWALL: Перші IPv6 діапазони: " + String.join(", ", Arrays.copyOfRange(ipv6RangeArray, 0, Math.min(3, ipv6RangeArray.length))));
                parseCIDRRanges(ipv6Ranges, false, countryCode);
            } else {
                System.out.println("FIREWALL: IPv6 діапазони не знайдені для " + countryCode.toUpperCase());
            }
        } catch (Exception e) {
            System.err.println("FIREWALL: Помилка парсингу JSON для країни " + countryCode.toUpperCase() + ": " + e.getMessage());
        }
    }

    /**
     * Парсить CIDR діапазони та додає їх до відповідних списків
     * @param ranges рядок з CIDR діапазонами через кому
     * @param isIPv4 true для IPv4, false для IPv6
     * @param countryCode код країни для логування
     */
    private static void parseCIDRRanges(String ranges, boolean isIPv4, String countryCode) {
        if (ranges == null || ranges.trim().isEmpty()) {
            System.out.println("FIREWALL: Порожній рядок діапазонів для " + (isIPv4 ? "IPv4" : "IPv6") + " країни " + countryCode.toUpperCase());
            return;
        }
        //int successCount = 0;
        //int errorCount = 0;

        String[] rangeStrings = ranges.split(",");
        for (String rangeStr : rangeStrings) {
            String trimmed = rangeStr.trim().replace("\"", "");
            if (!trimmed.isEmpty()) {
                try {
                    if (isIPv4) {
                        IP4Range range = parseIPv4CIDRRange(trimmed);
                        if (range != null) {
                            ipv4CountryRanges.add(range);
                        }
                    } else {
                        IP6Range range = parseIPv6CIDRRange(trimmed);
                        if (range != null) {
                            ipv6CountryRanges.add(range);
                            //successCount++;
                        } else {
                            //errorCount++;
                            System.err.println("FIREWALL: Помилка парсингу IPv6 діапазону '" + trimmed + "' для " + countryCode.toUpperCase());
                        }
                    }
                } catch (Exception e) {
                    //errorCount++;
                    System.err.println("FIREWALL: Помилка парсингу CIDR діапазону '" + trimmed + "' для країни " + countryCode + ": " + e.getMessage());
                }
            }
        }

        //System.out.println("FIREWALL: " + (isIPv4 ? "IPv4" : "IPv6") + " для " + countryCode.toUpperCase() + " - успішно: " + successCount + ", помилок: " + errorCount);
    }

    /**
     * Парсить IPv4 CIDR діапазон
     * @param cidr CIDR рядок (наприклад, "192.168.1.0/24")
     * @return IP4Range об'єкт або null при помилці
     */
    private static IP4Range parseIPv4CIDRRange(String cidr) {
        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            return null;
        }

        String ipStr = parts[0];
        int prefixLength;
        try {
            prefixLength = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }

        try {
            InetAddress inetAddr = InetAddress.getByName(ipStr);
            byte[] ipBytes = inetAddr.getAddress();
            if (ipBytes.length != 4) {
                return null; // Не IPv4
            }

            // Конвертуємо IPv4 в long
            long ipLong = ((ipBytes[0] & 0xFFL) << 24) | ((ipBytes[1] & 0xFFL) << 16) |
                         ((ipBytes[2] & 0xFFL) << 8) | (ipBytes[3] & 0xFFL);

            // Обчислюємо мережеву маску
            long mask = (prefixLength == 0) ? 0L : (~0L << (32 - prefixLength));

            // Обчислюємо мережеву адресу (побітове AND з маскою)
            long networkAddress = ipLong & mask;

            return new IP4Range(networkAddress, mask);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Парсить IPv6 CIDR діапазон з обробкою :: компресії
     * @param cidr CIDR рядок (наприклад, "2001:db8::/32")
     * @return IP6Range об'єкт або null при помилці
     */
    private static IP6Range parseIPv6CIDRRange(String cidr) {
        //System.out.println("FIREWALL: Парсинг IPv6 діапазону: " + cidr);

        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            System.out.println("FIREWALL: Неправильний формат IPv6 CIDR: " + cidr);
            return null;
        }

        String ipStr = parts[0];
        int prefixLength;
        try {
            prefixLength = Integer.parseInt(parts[1]);
            //System.out.println("FIREWALL: IPv6 префікс: " + prefixLength);
        } catch (NumberFormatException e) {
            System.out.println("FIREWALL: Неправильний формат префіксу в IPv6 CIDR: " + cidr);
            return null;
        }

        try {
            // Парсимо IPv6 адресу - читаємо послідовно до ::
            String[] ipParts = ipStr.split(":");
            long[] ipLongs = new long[2];
            int partIndex = 0;

            for (String part : ipParts) {
                if (part.isEmpty()) {
                    // Знайшли початок :: - зупиняємося
                    break;
                }

                if (partIndex < 8) {
                    int hexValue = Integer.parseInt(part, 16);

                    // IPv6 адреса в big-endian: блок 0 - найстарші біти
                    // long[1] містить біти 64-127, long[0] містить біти 0-63
                    int longIndex = (partIndex < 4) ? 0 : 1;  // перші 4 блоки в long[0], інші в long[1]
                    int blockInLong = partIndex % 4;

                    // Shift: для long[1] - (3-blockInLong)*16, для long[0] - (3-blockInLong)*16 + 64
                    int shift = (3 - blockInLong) * 16;
                    if (longIndex == 0) {
                        shift += 64;
                    }

                    //System.out.println("FIREWALL: Блок " + partIndex + ": " + part + " -> long[" + longIndex + "] shift=" + shift);

                    if (longIndex == 1) {
                        ipLongs[1] |= ((long) hexValue << shift);
                    } else {
                        ipLongs[0] |= ((long) hexValue << shift);
                    }
                    partIndex++;
                }
            }

            //System.out.println("FIREWALL: Оброблено " + partIndex + " блоків");
            //System.out.println("FIREWALL: Конвертовано в long[]: " + Arrays.toString(ipLongs));

            // Обчислюємо мережеву маску для IPv6
            long[] mask = new long[2];
            int fullWords = prefixLength / 64;
            int remainderBits = prefixLength % 64;

            //System.out.println("FIREWALL: fullWords=" + fullWords + ", remainderBits=" + remainderBits);

            if (fullWords > 0) {
                mask[0] = fullWords >= 1 ? ~0L : 0L;
                mask[1] = fullWords >= 2 ? ~0L : 0L;
            }

            if (remainderBits > 0) {
                if (fullWords == 0) {
                    // Маска тільки в першому long
                    mask[0] = (~0L << (64 - remainderBits));
                } else if (fullWords == 1) {
                    // Маска в другому long
                    mask[1] = (~0L << (64 - remainderBits));
                }
            }

            //System.out.println("FIREWALL: Маска: " + Arrays.toString(mask));

            // Обчислюємо мережеву адресу (побітове AND з маскою)
            long[] networkAddress = new long[2];
            networkAddress[0] = ipLongs[0] & mask[0];
            networkAddress[1] = ipLongs[1] & mask[1];

            //System.out.println("FIREWALL: Мережева адреса: " + Arrays.toString(networkAddress));

            IP6Range result = new IP6Range(networkAddress, mask);
            //System.out.println("FIREWALL: Успішно створено IPv6 діапазон для " + cidr);
            return result;
        } catch (Exception e) {
            System.err.println("FIREWALL: Помилка при парсингу IPv6 діапазону " + cidr + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Перевіряє чи IP адреса знаходиться в заблокованій країні
     * @param clientAddress IP адреса для перевірки
     * @return true якщо IP з заблокованої країни
     */
    private static boolean isIPFromBannedCountry(InetAddress clientAddress) {
        byte[] ipBytes = clientAddress.getAddress();

        if (ipBytes.length == 4) {
            // IPv4 - використовуємо побітові операції
            long ipLong = ((ipBytes[0] & 0xFFL) << 24) | ((ipBytes[1] & 0xFFL) << 16) | ((ipBytes[2] & 0xFFL) << 8) | (ipBytes[3] & 0xFFL);

            return ipv4CountryRanges.stream().anyMatch(range -> range.containsIPv4(ipLong));
        } else if (ipBytes.length == 16) {
            // IPv6 - використовуємо побітові операції
            return ipv6CountryRanges.stream().anyMatch(range -> range.containsIPv6(ipBytes));
        }

        return false;
    }

    /**
     * Клас для представлення IPv4 діапазону з побітовими операціями
     */
    private static class IP4Range {
        private final long networkAddress;
        private final long subnetMask;

        public IP4Range(long networkAddress, long subnetMask) {
            this.networkAddress = networkAddress;
            this.subnetMask = subnetMask;
        }

        /**
         * Перевіряє чи IPv4 адреса знаходиться в цьому діапазоні
         * @param ipLong IPv4 адреса як long
         * @return true якщо адреса в діапазоні
         */
        public boolean containsIPv4(long ipLong) {
            return (ipLong & subnetMask) == networkAddress;
        }

        public long getNetworkAddress() { return networkAddress; }
        public long getSubnetMask() { return subnetMask; }
    }

    /**
     * Клас для представлення IPv6 діапазону з побітовими операціями
     */
    private static class IP6Range {
        private final long[] networkAddress; // 2 longs for 128 bits
        private final long[] subnetMask;    // 2 longs for 128 bits

        public IP6Range(long[] networkAddress, long[] subnetMask) {
            this.networkAddress = networkAddress.clone();
            this.subnetMask = subnetMask.clone();
        }

        /**
         * Перевіряє чи IPv6 адреса знаходиться в цьому діапазоні
         * @param ipBytes IPv6 адреса як byte array (16 bytes)
         * @return true якщо адреса в діапазоні
         */
        public boolean containsIPv6(byte[] ipBytes) {
            if (ipBytes.length != 16) {
                return false;
            }

            // Конвертуємо byte array в long array (big-endian)
            long[] ipLongs = new long[2];
            ipLongs[0] = ((ipBytes[0] & 0xFFL) << 56) | ((ipBytes[1] & 0xFFL) << 48) |
                        ((ipBytes[2] & 0xFFL) << 40) | ((ipBytes[3] & 0xFFL) << 32) |
                        ((ipBytes[4] & 0xFFL) << 24) | ((ipBytes[5] & 0xFFL) << 16) |
                        ((ipBytes[6] & 0xFFL) << 8) | (ipBytes[7] & 0xFFL);

            ipLongs[1] = ((ipBytes[8] & 0xFFL) << 56) | ((ipBytes[9] & 0xFFL) << 48) |
                        ((ipBytes[10] & 0xFFL) << 40) | ((ipBytes[11] & 0xFFL) << 32) |
                        ((ipBytes[12] & 0xFFL) << 24) | ((ipBytes[13] & 0xFFL) << 16) |
                        ((ipBytes[14] & 0xFFL) << 8) | (ipBytes[15] & 0xFFL);

            // Порівнюємо з маскою
            for (int i = 0; i < 2; i++) {
                if ((ipLongs[i] & subnetMask[i]) != networkAddress[i]) {
                    return false;
                }
            }
            return true;
        }

        public long[] getNetworkAddress() { return networkAddress.clone(); }
        public long[] getSubnetMask() { return subnetMask.clone(); }
    }

  
    /**
     * Завантажує шляхи з файлу рядок за рядком
     * @param filePath шлях до файлу
     * @param pathList список для заповнення
     */
    private static void loadPathsFromFile(String filePath, List<String> pathList) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Очищаємо від \r символів (якщо є) і пробілів
                String cleanPath = line.replace("\r", "").trim();
                if (!cleanPath.isEmpty()) {
                    pathList.add(cleanPath);
                }
            }
        } catch (IOException e) {
            System.err.println("Помилка при читанні файлу списку шляхів: " + filePath + " - " + e.getMessage());
        }
    }

    /**
     * Перевіряє чи шлях знаходиться в білому списку (точний збіг)
     * @param path шлях для перевірки
     * @return true якщо шлях в білому списку
     */
    private static boolean isInWhiteList(String path) {
        return whitePathList.contains(path);
    }

    /**
     * Перевіряє чи шлях міститься в чорному списку (частковий збіг)
     * @param path шлях для перевірки
     * @return true якщо шлях міститься в чорному списку
     */
    private static boolean isInBlackList(String path) {
        return blackPathList.stream().anyMatch(path::contains);
    }

    /**
     * Модуль IP-фільтра: збирає статистику хак-запитів та вносить IP до чорного списку
     * @param request HTTP запит для аналізу
     */
    public static void statisticCollection(HTTPRequest request) {
        // Перевіряємо чи шлях в білому списку - якщо так, ігноруємо
        if(Configs.getBoolean("FirewallRun") == false) {
            return;
        }
        
        if (isInWhiteList(request.path)) {
            return;
        }

        // Перевіряємо чи шлях в чорному списку - якщо так, додаємо IP до бану негайно
        if (isInBlackList(request.path)) {
            System.out.println("FIREWALL: IP " + request.clientAddress.getHostAddress() + " внесено в бан-лист через заборонений шлях: " + request.path);
            blackList.put(request.clientAddress, Instant.now());
            attackStatistics.remove(request.clientAddress);
            return;
        }

        // Збираємо статистику атак з потоків
        collectAttackStatistics(request);

        // Аналізуємо запит на підозрілу активність
        //if (request.ban || request.quickBan) {
        //    blackList.put(request.clientAddress, Instant.now());
        //}

        // Додати додаткові перевірки на:
        // - SQL injection спроби
        // - XSS атаки
        // - Швидкі повторні запити з одного IP
        // - Підозрілі User-Agent
        // тощо
    }

    /**
     * Збирає статистику атак для конкретного IP
     * @param request HTTP запит
     */
    private static void collectAttackStatistics(HTTPRequest request) {
        //if (request.ban || request.quickBan) {
            // Збільшуємо лічильник атак для цього IP
            attackStatistics.computeIfAbsent(request.clientAddress, ip -> new AtomicInteger(0)).incrementAndGet();

            // Якщо досягнуто порогу - додаємо до чорного списку
            if (attackStatistics.get(request.clientAddress).get() >= STATISTICS_COLLECTION_THRESHOLD) {
                System.out.println("FIREWALL: IP " + request.clientAddress.getHostAddress() + " внесено в бан-лист через досягнення порогу атак (" + STATISTICS_COLLECTION_THRESHOLD + ")");
                blackList.put(request.clientAddress, Instant.now());
                // Скидаємо статистику після додавання до чорного списку
                attackStatistics.remove(request.clientAddress);
            }
        //}
    }

    /**
     * Модуль IP-фільтра: перевіряє чи знаходиться IP у чорному списку
     * @param clientAddress IP-адреса клієнта
     * @return true якщо IP заблоковано, false якщо дозволено
     */
    public static boolean checkBlackList(InetAddress clientAddress) {
        if(Configs.getBoolean("FirewallRun") == false) {
            return false;
        }

        // Перевіряємо чи IP з заблокованої країни
        if (Configs.getDefine("countriesBan")) {
            if (isIPFromBannedCountry(clientAddress)) {
                System.out.println("FIREWALL: IP " + clientAddress.getHostAddress() + " заблоковано через країну");
                return true;
            }
        }

        Instant blockTime = blackList.get(clientAddress);

        if (blockTime == null) {
            return false; // IP не в чорному списку
        }

        // Перевіряємо чи не минув час блокування
        if (blockTime.plusSeconds(BLACKLIST_EXPIRATION_SECONDS).isBefore(Instant.now())) {
            // Якщо минув - видаляємо з чорного списку
            blackList.remove(clientAddress);
            return false;
        }
        System.out.println("FIREWALL: IP " + clientAddress.getHostAddress() + " заблоковано через знаходження у бан-листі");
        return true; // IP заблоковано
    }

    /**
     * Заглушка для PHP-файрвол модуля: перевіряє коректність запиту до PHP файлу з параметрами
     * @param docName назва PHP документу
     * @param param список параметрів
     * @return true якщо запит дозволено
     */
    public static boolean phpFirewall(String docName, List<String> param) {
        if(Configs.getBoolean("FirewallRun") == false) {
            return true;
        }
        // TODO: Реалізувати перевірку PHP файлів на коректність
        // - Перевірка на дозволені шляхи до файлів
        // - Аналіз параметрів на шкідливий вміст
        // - Перевірка на несанкціонований доступ
        return true; // Тимчасова заглушка - дозволяє всі запити
    }

    /**
     * Заглушка для PHP-файрвол модуля: перевіряє коректність запиту до PHP файлу
     * @param docName назва PHP документу
     * @return true якщо запит дозволено
     */
    public static boolean phpFirewall(String docName) {
        if(Configs.getBoolean("FirewallRun") == false) {
            return true;
        }
        // TODO: Реалізувати базову перевірку PHP файлів
        // - Перевірка на існування файлу
        // - Перевірка на дозволені розширення
        // - Перевірка на доступність директорії
        return true; // Тимчасова заглушка - дозволяє всі запити
    }

    /**
     * Допоміжний метод для очищення застарілих записів з чорного списку
     * Рекомендується викликати періодично
     */
    public static void cleanupBlackList() {
        if(Configs.getBoolean("FirewallRun") == false) {
            return;
        }
        Instant now = Instant.now();
        blackList.entrySet().removeIf(entry -> entry.getValue().plusSeconds(BLACKLIST_EXPIRATION_SECONDS).isBefore(now));
        attackStatistics.entrySet().removeIf(entry -> {
            // Також очищуємо статистику старше певного часу (наприклад, 1 година)
            return entry.getValue().get() == 0; // Видаляємо записи з нульовою статистикою
        });
    }

    /**
     * @return кількість заблокованих IP
     */
    public static int getBlockedIpsCount() {
        return blackList.size();
    }

    /**
     * @return кількість атак з потоків
     */
    public static int getAttackStatisticsCount() {
        return attackStatistics.size();
    }
}
