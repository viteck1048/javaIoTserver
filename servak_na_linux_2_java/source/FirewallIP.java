import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IP-based firewall module for filtering network requests
 * Handles IP blacklisting, country blocking, and attack statistics
 */
public class FirewallIP {

	// Configuration constants
	private static final long BLACKLIST_EXPIRATION_SECONDS = Configs.getDefine("ipBanLifeTime") ? Configs.getLong("ipBanLifeTime") : 3600;
	private static final int STATISTICS_COLLECTION_THRESHOLD = Configs.getDefine("quantToTriger") ? Configs.getInt("quantToTriger") : 5;

	// Thread-safe maps for storing blocked IPs and attack statistics
	private static final Map<InetAddress, Instant> blackList = new ConcurrentHashMap<>();
	private static final Map<InetAddress, AtomicInteger> attackStatistics = new ConcurrentHashMap<>();

	// Path lists for request filtering
	private static final List<String> whitePathList = new ArrayList<>();
	private static final List<String> blackPathList = new ArrayList<>();

	// Country blocking data
	private static final List<String> bannedCountries = new ArrayList<>();
	// Arrays of lists for fast lookup by first byte
	@SuppressWarnings("unchecked")
	private static final List<IP4Range>[] ipv4CountryRanges = new ArrayList[256];
	@SuppressWarnings("unchecked")
	private static final List<IP6Range>[] ipv6CountryRanges = new ArrayList[256];

	/**
	 * Initializes IP firewall
	 */
	public static void initialize() {
		System.out.println("FIREWALL IP: Initializing IP firewall...");

		// Initialize 256 lists for IPv4 and IPv6
		for (int i = 0; i < 256; i++) {
			ipv4CountryRanges[i] = new ArrayList<>();
			ipv6CountryRanges[i] = new ArrayList<>();
		}

		loadPathLists();
		loadBannedCountries();

		System.out.println("FIREWALL IP: Initialization completed");
	}

	/**
	 * Loads white and black path lists from files
	 */
	private static void loadPathLists() {
		// Load white list
		if(Configs.getDefine("whitePathList")) {
			String whiteFilePath = Configs.getParam("whitePathList");
			if (whiteFilePath != null && !whiteFilePath.trim().isEmpty()) {
				System.out.print("FIREWALL IP: Loading white list from file: " + whiteFilePath + "...");
				loadPathsFromFile(whiteFilePath, whitePathList);
				System.out.println(" White list loaded, elements: " + whitePathList.size());
			} else {
				System.out.println("FIREWALL IP: White list not configured");
			}
		} else {
			System.out.println("FIREWALL IP: White list disabled");
		}

		// Load black list
		if(Configs.getDefine("blackPathList")) {
			String blackFilePath = Configs.getParam("blackPathList");
			if (blackFilePath != null && !blackFilePath.trim().isEmpty()) {
				System.out.print("FIREWALL IP: Loading black list from file: " + blackFilePath + "...");
				loadPathsFromFile(blackFilePath, blackPathList);
				System.out.println(" Black list loaded, elements: " + blackPathList.size());
			} else {
				System.out.println("FIREWALL IP: Black list not configured");
			}
		} else {
			System.out.println("FIREWALL IP: Black list disabled");
		}
	}

	/**
	 * Loads banned countries list and their IP ranges
	 */
	private static void loadBannedCountries() {
		if (!Configs.getDefine("countriesBan")) {
			System.out.println("FIREWALL IP: Country blocking disabled");
			return;
		}

		System.out.print("FIREWALL IP: Loading banned countries list...");
		String countriesConfig = Configs.getParam("countriesBan");
		if (countriesConfig == null || countriesConfig.trim().isEmpty()) {
			System.out.println("FIREWALL IP: Countries list not configured");
			return;
		}

		String[] countries = countriesConfig.split(",");
		System.out.println(" Found " + countries.length + " countries for blocking");

		for (String country : countries) {
			String trimmedCountry = country.trim().toLowerCase();
			if (!trimmedCountry.isEmpty()) {
				System.out.println("FIREWALL IP: Loading IP ranges for country: " + trimmedCountry.toUpperCase());
				bannedCountries.add(trimmedCountry);
				loadCountryIPRanges(trimmedCountry);
			}
		}

		// Count total ranges in all lists
		int totalIPv4 = 0;
		int totalIPv6 = 0;
		for (int i = 0; i < 256; i++) {
			totalIPv4 += ipv4CountryRanges[i].size();
			totalIPv6 += ipv6CountryRanges[i].size();
		}
		System.out.println("FIREWALL IP: Loaded IPv4 ranges: " + totalIPv4 + ", IPv6: " + totalIPv6);
	}

	/**
	 * Loads IP ranges for a specific country from GitHub
	 * @param countryCode two-letter country code
	 */
	private static void loadCountryIPRanges(String countryCode) {
		String url = "https://raw.githubusercontent.com/ipverse/rir-ip/master/country/" + countryCode + "/aggregated.json";

		try {
			String jsonContent = fetchJsonFromUrl(url);
			parseCountryIPRanges(jsonContent, countryCode);
		} catch (Exception e) {
			System.err.println("FIREWALL IP: Error loading IP ranges for country " + countryCode.toUpperCase() + ": " + e.getMessage());
		}
	}

	/**
	 * Fetches JSON content from URL
	 * @param urlString URL to fetch from
	 * @return JSON content as string
	 */
	private static String fetchJsonFromUrl(String urlString) throws IOException {
		java.net.URL url = new java.net.URL(urlString);
		java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET");
		connection.setConnectTimeout(5000);
		connection.setReadTimeout(10000);

		try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(connection.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
			StringBuilder content = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				content.append(line).append("\n");
			}
			return content.toString();
		}
	}

	/**
	 * Parses JSON with country IP ranges
	 * @param jsonContent JSON content
	 * @param countryCode country code for error logging
	 */
	private static void parseCountryIPRanges(String jsonContent, String countryCode) {
		try {
			// Simple JSON parsing (can be replaced with proper JSON parser)
			java.util.regex.Pattern ipv4Pattern = java.util.regex.Pattern.compile("\"ipv4\"\\s*:\\s*\\[([^\\]]+)\\]");
			java.util.regex.Pattern ipv6Pattern = java.util.regex.Pattern.compile("\"ipv6\"\\s*:\\s*\\[([^\\]]+)\\]");

			java.util.regex.Matcher ipv4Matcher = ipv4Pattern.matcher(jsonContent);
			if (ipv4Matcher.find()) {
				String ipv4Ranges = ipv4Matcher.group(1);
				parseCIDRRanges(ipv4Ranges, true, countryCode);
			} else {
				System.out.println("FIREWALL IP: IPv4 ranges not found for " + countryCode.toUpperCase());
			}

			java.util.regex.Matcher ipv6Matcher = ipv6Pattern.matcher(jsonContent);
			if (ipv6Matcher.find()) {
				String ipv6Ranges = ipv6Matcher.group(1);
				String[] ipv6RangeArray = ipv6Ranges.split(",");
				parseCIDRRanges(ipv6Ranges, false, countryCode);
			} else {
				System.out.println("FIREWALL IP: IPv6 ranges not found for " + countryCode.toUpperCase());
			}
		} catch (Exception e) {
			System.err.println("FIREWALL IP: Error parsing JSON for country " + countryCode.toUpperCase() + ": " + e.getMessage());
		}
	}

	/**
	 * Optimizes IPv4 range before adding to list
	 * Removes duplicates and merges overlapping ranges
	 * @param newIp new network address
	 * @param newMask new mask
	 * @param firstByte first byte of address for list selection
	 * @return optimized range or null if completely included in existing
	 */
	private static IP4Range optimizeIPv4Range(int newIp, int newMask, int firstByte) {
		int currentIp = newIp;
		int currentMask = newMask;
		boolean needRecheck = true;

		List<IP4Range> targetList = ipv4CountryRanges[firstByte];

		while (needRecheck) {
			needRecheck = false;
			for (int i = 0; i < targetList.size(); i++) {
				IP4Range existing = targetList.get(i);

				// Check for overlap/inclusion through mutual mask overlay
				if ((currentIp & existing.getSubnetMask()) == (existing.getNetworkAddress() & currentMask)) {
					targetList.remove(i);

					// Modify new range
					currentIp &= currentMask & existing.getSubnetMask();
					currentMask &= existing.getSubnetMask();

					needRecheck = true;
					break;
				}
			}
		}

		return new IP4Range(currentIp, currentMask);
	}

	/**
	 * Optimizes IPv6 range before adding to list
	 * Removes duplicates and merges overlapping ranges
	 * @param newIp new network address (long[2])
	 * @param newMask new mask (long[2])
	 * @param firstByte first byte of address for list selection
	 * @return optimized range or null if completely included in existing
	 */
	private static IP6Range optimizeIPv6Range(long[] newIp, long[] newMask, int firstByte) {
		long[] currentIp = newIp.clone();
		long[] currentMask = newMask.clone();
		boolean needRecheck = true;

		List<IP6Range> targetList = ipv6CountryRanges[firstByte];

		while (needRecheck) {
			needRecheck = false;
			for (int i = 0; i < targetList.size(); i++) {
				IP6Range existing = targetList.get(i);

				// Check for overlap/inclusion through mutual mask overlay
				boolean match = true;
				for (int j = 0; j < 2; j++) {
					if ((currentIp[j] & existing.getSubnetMask()[j]) != (existing.getNetworkAddress()[j] & currentMask[j])) {
						match = false;
						break;
					}
				}

				if (match) {
					targetList.remove(i);

					// Modify new range
					for (int j = 0; j < 2; j++) {
						currentIp[j] &= currentMask[j] & existing.getSubnetMask()[j];
						currentMask[j] &= existing.getSubnetMask()[j];
					}

					needRecheck = true;
					break;
				}
			}
		}

		return new IP6Range(currentIp, currentMask);
	}

	/**
	 * Parses CIDR ranges and adds them to appropriate lists
	 * @param ranges string with CIDR ranges separated by comma
	 * @param isIPv4 true for IPv4, false for IPv6
	 * @param countryCode country code for logging
	 */
	private static void parseCIDRRanges(String ranges, boolean isIPv4, String countryCode) {
		if (ranges == null || ranges.trim().isEmpty()) {
			System.out.println("FIREWALL IP: Empty range string for " + (isIPv4 ? "IPv4" : "IPv6") + " country " + countryCode.toUpperCase());
			return;
		}

		String[] rangeStrings = ranges.split(",");
		for (String rangeStr : rangeStrings) {
			String trimmed = rangeStr.trim().replace("\"", "");
			if (!trimmed.isEmpty()) {
				try {
					if (isIPv4) {
						IP4Range range = parseIPv4CIDRRange(trimmed);
						if (range != null) {
							// Determine first byte of address for list selection
							int firstByte = (range.getNetworkAddress() >> 24) & 0xFF;
							IP4Range optimized = optimizeIPv4Range(range.getNetworkAddress(), range.getSubnetMask(), firstByte);
							if (optimized != null) {
								ipv4CountryRanges[firstByte].add(optimized);
							}
						}
					} else {
						IP6Range range = parseIPv6CIDRRange(trimmed);
						if (range != null) {
							// Determine first byte of address for list selection (most significant byte of long[1])
							int firstByte = (int) ((range.getNetworkAddress()[0] >> 56) & 0xFF);
							IP6Range optimized = optimizeIPv6Range(range.getNetworkAddress(), range.getSubnetMask(), firstByte);
							if (optimized != null) {
								ipv6CountryRanges[firstByte].add(optimized);
							}
						}
					}
				} catch (Exception e) {
					System.err.println("FIREWALL IP: Error parsing CIDR range '" + trimmed + "' for country " + countryCode + ": " + e.getMessage());
				}
			}
		}
	}

	/**
	 * Parses IPv4 CIDR range
	 * @param cidr CIDR string (e.g., "192.168.1.0/24")
	 * @return IP4Range object or null on error
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
				return null; // Not IPv4
			}

			// Convert IPv4 to int
			int ipInt = ((ipBytes[0] & 0xFF) << 24) | ((ipBytes[1] & 0xFF) << 16) |
						((ipBytes[2] & 0xFF) << 8) | (ipBytes[3] & 0xFF);

			// Calculate network mask (32 bits)
			int mask = (prefixLength == 0) ? 0 : (~0 << (32 - prefixLength));

			// Calculate network address (bitwise AND with mask)
			int networkAddress = ipInt & mask;

			return new IP4Range(networkAddress, mask);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Parses IPv6 CIDR range with :: compression handling
	 * @param cidr CIDR string (e.g., "2001:db8::/32")
	 * @return IP6Range object or null on error
	 */
	private static IP6Range parseIPv6CIDRRange(String cidr) {
		String[] parts = cidr.split("/");
		if (parts.length != 2) {
			System.out.println("FIREWALL IP: Invalid IPv6 CIDR format: " + cidr);
			return null;
		}

		String ipStr = parts[0];
		int prefixLength;
		try {
			prefixLength = Integer.parseInt(parts[1]);
		} catch (NumberFormatException e) {
			System.out.println("FIREWALL IP: Invalid prefix format in IPv6 CIDR: " + cidr);
			return null;
		}

		try {
			// Parse IPv6 address - read sequentially until ::
			String[] ipParts = ipStr.split(":");
			long[] ipLongs = new long[2];
			int partIndex = 0;

			for (String part : ipParts) {
				if (part.isEmpty()) {
					// Found start of :: - stop
					break;
				}

				if (partIndex < 8) {
					int hexValue = Integer.parseInt(part, 16);

					// IPv6 address in big-endian: block 0 - most significant bits
					// long[1] contains bits 64-127, long[0] contains bits 0-63
					int longIndex = (partIndex < 4) ? 0 : 1;  // first 4 blocks in long[0], others in long[1]
					int blockInLong = partIndex % 4;

					// Shift: for long[1] - (3-blockInLong)*16, for long[0] - (3-blockInLong)*16 + 64
					int shift = (3 - blockInLong) * 16;
					if (longIndex == 0) {
						shift += 64;
					}

					if (longIndex == 1) {
						ipLongs[1] |= ((long) hexValue << shift);
					} else {
						ipLongs[0] |= ((long) hexValue << shift);
					}
					partIndex++;
				}
			}

			// Calculate network mask for IPv6
			long[] mask = new long[2];
			int fullWords = prefixLength / 64;
			int remainderBits = prefixLength % 64;

			if (fullWords > 0) {
				mask[0] = fullWords >= 1 ? ~0L : 0L;
				mask[1] = fullWords >= 2 ? ~0L : 0L;
			}

			if (remainderBits > 0) {
				if (fullWords == 0) {
					// Mask only in first long
					mask[0] = (~0L << (64 - remainderBits));
				} else if (fullWords == 1) {
					// Mask in second long
					mask[1] = (~0L << (64 - remainderBits));
				}
			}

			// Calculate network address (bitwise AND with mask)
			long[] networkAddress = new long[2];
			networkAddress[0] = ipLongs[0] & mask[0];
			networkAddress[1] = ipLongs[1] & mask[1];

			return new IP6Range(networkAddress, mask);
		} catch (Exception e) {
			System.err.println("FIREWALL IP: Error parsing IPv6 range " + cidr + ": " + e.getMessage());
			return null;
		}
	}

	/**
	 * Checks if IP address is from banned country
	 * @param clientAddress IP address to check
	 * @return true if IP from banned country
	 */
	private static boolean isIPFromBannedCountry(InetAddress clientAddress) {
		byte[] ipBytes = clientAddress.getAddress();

		if (ipBytes.length == 4) {
			// IPv4 - use bitwise operations
			// Determine first byte for list selection
			int firstByte = ipBytes[0] & 0xFF;
			return ipv4CountryRanges[firstByte].stream().anyMatch(range -> range.containsIPv4(ipBytes));
		} else if (ipBytes.length == 16) {
			// IPv6 - use bitwise operations
			// Determine first byte for list selection
			int firstByte = ipBytes[0] & 0xFF;
			return ipv6CountryRanges[firstByte].stream().anyMatch(range -> range.containsIPv6(ipBytes));
		}

		return false;
	}

	/**
	 * Class for representing IPv4 range with bitwise operations
	 */
	private static class IP4Range {
		private final int networkAddress;
		private final int subnetMask;

		public IP4Range(int networkAddress, int subnetMask) {
			this.networkAddress = networkAddress;
			this.subnetMask = subnetMask;
		}

		/**
		 * Checks if IPv4 address is in this range
		 * @param ipBytes IPv4 address as byte array (4 bytes)
		 * @return true if address in range
		 */
		public boolean containsIPv4(byte[] ipBytes) {
			if (ipBytes.length != 4) {
				return false;
			}

			// Convert byte array to int (big-endian)
			int ipInt = ((ipBytes[0] & 0xFF) << 24) | ((ipBytes[1] & 0xFF) << 16) |
						((ipBytes[2] & 0xFF) << 8) | (ipBytes[3] & 0xFF);

			return (ipInt & subnetMask) == networkAddress;
		}

		public int getNetworkAddress() { return networkAddress; }
		public int getSubnetMask() { return subnetMask; }
	}

	/**
	 * Class for representing IPv6 range with bitwise operations
	 */
	private static class IP6Range {
		private final long[] networkAddress; // 2 longs for 128 bits
		private final long[] subnetMask;    // 2 longs for 128 bits

		public IP6Range(long[] networkAddress, long[] subnetMask) {
			this.networkAddress = networkAddress.clone();
			this.subnetMask = subnetMask.clone();
		}

		/**
		 * Checks if IPv6 address is in this range
		 * @param ipBytes IPv6 address as byte array (16 bytes)
		 * @return true if address in range
		 */
		public boolean containsIPv6(byte[] ipBytes) {
			if (ipBytes.length != 16) {
				return false;
			}

			// Convert byte array to long array (big-endian)
			long[] ipLongs = new long[2];
			ipLongs[0] = ((ipBytes[0] & 0xFFL) << 56) | ((ipBytes[1] & 0xFFL) << 48) |
						((ipBytes[2] & 0xFFL) << 40) | ((ipBytes[3] & 0xFFL) << 32) |
						((ipBytes[4] & 0xFFL) << 24) | ((ipBytes[5] & 0xFFL) << 16) |
						((ipBytes[6] & 0xFFL) << 8) | (ipBytes[7] & 0xFFL);

			ipLongs[1] = ((ipBytes[8] & 0xFFL) << 56) | ((ipBytes[9] & 0xFFL) << 48) |
						((ipBytes[10] & 0xFFL) << 40) | ((ipBytes[11] & 0xFFL) << 32) |
						((ipBytes[12] & 0xFFL) << 24) | ((ipBytes[13] & 0xFFL) << 16) |
						((ipBytes[14] & 0xFFL) << 8) | (ipBytes[15] & 0xFFL);

			// Compare with mask
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
	 * Loads paths from file line by line
	 * @param filePath path to file
	 * @param pathList list to populate
	 */
	private static void loadPathsFromFile(String filePath, List<String> pathList) {
		try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
			String line;
			while ((line = reader.readLine()) != null) {
				// Clean from \r characters (if any) and spaces
				String cleanPath = line.replace("\r", "").trim();
				if (!cleanPath.isEmpty()) {
					pathList.add(cleanPath);
				}
			}
		} catch (IOException e) {
			System.err.println("Error reading path list file: " + filePath + " - " + e.getMessage());
		}
	}

	/**
	 * Checks if path is in white list (exact match)
	 * @param path path to check
	 * @return true if path in white list
	 */
	private static boolean isInWhiteList(String path) {
		return whitePathList.contains(path);
	}

	/**
	 * Checks if path is in black list (partial match)
	 * @param path path to check
	 * @return true if path contained in black list
	 */
	private static boolean isInBlackList(String path) {
		return blackPathList.stream().anyMatch(path::contains);
	}

	/**
	 * IP filter module: collects attack statistics and adds IP to blacklist
	 * @param request HTTP request for analysis
	 */
	public static void statisticCollection(HTTPRequest request) {
		// Check if path in white list - if yes, ignore
		if(Configs.getBoolean("FirewallRun") == false) {
			return;
		}

		if (isInWhiteList(request.path) || request.isInSubnet()) {
			return;
		}

		// Check if path in black list - if yes, add IP to ban immediately
		if (isInBlackList(request.path)) {
			System.out.println("FIREWALL IP: IP " + request.clientAddress.getHostAddress() + " added to ban list due to forbidden path: " + request.path);
			blackList.put(request.clientAddress, Instant.now());
			attackStatistics.remove(request.clientAddress);
			return;
		}

		// Collect attack statistics from streams
		collectAttackStatistics(request);
	}

	/**
	 * Collects attack statistics for specific IP
	 * @param request HTTP request
	 */
	private static void collectAttackStatistics(HTTPRequest request) {
		// Increase attack counter for this IP
		attackStatistics.computeIfAbsent(request.clientAddress, ip -> new AtomicInteger(0)).incrementAndGet();

		// If threshold reached - add to blacklist
		if (attackStatistics.get(request.clientAddress).get() >= STATISTICS_COLLECTION_THRESHOLD) {
			System.out.println("FIREWALL IP: IP " + request.clientAddress.getHostAddress() + " added to ban list due to attack threshold (" + STATISTICS_COLLECTION_THRESHOLD + ")");
			blackList.put(request.clientAddress, Instant.now());
			// Reset statistics after adding to blacklist
			attackStatistics.remove(request.clientAddress);
		}
	}

	/**
	 * IP filter module: checks if IP is in blacklist
	 * @param clientAddress client IP address
	 * @return true if IP blocked, false if allowed
	 */
	public static boolean checkBlackList(InetAddress clientAddress) {
		if(Configs.getBoolean("FirewallRun") == false) {
			return false;
		}

		Instant blockTime = blackList.get(clientAddress);

		if (blockTime != null) {
			System.out.println("FIREWALL IP: IP " + clientAddress.getHostAddress() + " blocked due to blacklist");
			return true; // IP in blacklist
		}

		// Check if IP from banned country
		if (Configs.getDefine("countriesBan")) {
			if (isIPFromBannedCountry(clientAddress)) {
				System.out.println("FIREWALL IP: IP " + clientAddress.getHostAddress() + " blocked due to country");
				return true;
			}
		}

		return false; // IP allowed
	}

	/**
	 * Helper method for cleaning outdated records from blacklist
	 * Recommended to call periodically
	 */
	public static void cleanupBlackList() {
		if(Configs.getBoolean("FirewallRun") == false) {
			return;
		}
		Instant now = Instant.now();
		blackList.entrySet().removeIf(entry -> entry.getValue().plusSeconds(BLACKLIST_EXPIRATION_SECONDS).isBefore(now));
		attackStatistics.entrySet().removeIf(entry -> {
			// Also clean statistics older than certain time (e.g., 1 hour)
			return entry.getValue().get() == 0; // Remove records with zero statistics
		});
	}

	/**
	 * @return number of blocked IPs
	 */
	public static int getBlockedIpsCount() {
		return blackList.size();
	}

	/**
	 * @return number of attack statistics
	 */
	public static int getAttackStatisticsCount() {
		return attackStatistics.size();
	}
}
