import java.net.InetAddress;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


public class KeyManager {
	//public record SnInfo(long sn_mega, int g_id, String name) {}

	public static class SnInfo {
		public long sn_mega;
		public int g_id;
		public String name;
		public static SnInfo create(long sn_mega, int g_id, String name) {
			SnInfo snInfo = new SnInfo();
			snInfo.sn_mega = sn_mega;
			snInfo.g_id = g_id;
			snInfo.name = name;
			return snInfo;
		}
	}


    private static class KeyInfo {
        long key;
        int userID;
        long sn_mega;
        InetAddress ip;
        LocalDateTime lastAccessTime;
        ArrayList<SnInfo> sn_megaList;
		String userName;

        KeyInfo(long key, int userID, InetAddress ip) {
            this.key = key;
            this.userID = userID;
            this.ip = ip;
            this.lastAccessTime = LocalDateTime.now();
            sn_megaList = new ArrayList<>();
			
            // Заповнення sn_megaList з БД для цього користувача
            try (Connection conn = DatabaseHelper.connect()) {
                if (conn != null) {
					String sql = "select login from clients u where c_id=?";
					try (PreparedStatement ps = conn.prepareStatement(sql)) {
						ps.setInt(1, this.userID);
						try (ResultSet rs = ps.executeQuery()) {
							if (rs.next()) {
								userName = rs.getString("login");
							}
						}
					}
					sql = "select c.g_id, g.sn_mega, g.name from connections c left join gadgets g on c.g_id=g.g_id where c.c_id=? order by g.name";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        ps.setInt(1, this.userID);
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                int g_id = rs.getInt("g_id");
                                long snMega = rs.getLong("sn_mega") & 0xFFFFFFFFL;
								String name = rs.getString("name").trim();
                                sn_megaList.add(SnInfo.create(snMega, g_id, name));
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            sn_mega = 0;
        }
    }
    
    private static final long EXPIRATION_TIME_MINUTES = Configs.getInt("key_expiration_time");
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Map<Long, KeyInfo> keys = new ConcurrentHashMap<>();
	
	public static String getUserName(int userID) {
		for (KeyInfo keyInfo : keys.values()) {
			if (keyInfo.userID == userID) {
				return keyInfo.userName;
			}
		}
		return null;
	}
	
	public static long genKey(int userID, InetAddress ip) {
		cleanUpExpiredKeys();

		// Перевіряємо, чи є вже існуючий ключ для цієї пари userID - ip
		for (KeyInfo keyInfo : keys.values()) {
			if (keyInfo.userID == userID && equals2(keyInfo.ip, ip, 1)) {
				// Оновлюємо час останнього доступу
				keyInfo.lastAccessTime = LocalDateTime.now();
				return keyInfo.key; // Повертаємо існуючий ключ
			}
		}

		// Якщо ключа немає, генеруємо новий
		long key = secureRandom.nextLong();
		keys.put(key, new KeyInfo(key, userID, ip));
		return key;
	}
	
	public static boolean updateUserDevicesFromDB(int userId) {
		ArrayList<SnInfo> newList = new ArrayList<>();
		
		// Зчитуємо нові дані з БД
		try (Connection conn = DatabaseHelper.connect()) {
			if (conn != null) {
				String sql = "select c.g_id, g.sn_mega, g.name from connections c left join gadgets g on c.g_id=g.g_id where c.c_id=? order by g.name";
				try (PreparedStatement ps = conn.prepareStatement(sql)) {
					ps.setInt(1, userId);
					try (ResultSet rs = ps.executeQuery()) {
						while (rs.next()) {
							int g_id = rs.getInt("g_id");
							long snMega = rs.getLong("sn_mega") & 0xFFFFFFFFL;
							String name = rs.getString("name").trim();
							newList.add(SnInfo.create(snMega, g_id, name));
						}
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
		
		// Оновлюємо список у всіх активних сесіях користувача
		boolean updated = false;
		for (KeyInfo keyInfo : keys.values()) {
			if (keyInfo.userID == userId) {
				synchronized (keyInfo) {
					keyInfo.sn_megaList.clear();
					keyInfo.sn_megaList.addAll(newList);
					updated = true;
				}
			}
		}
		
		return updated;
	}

	public static String addGad(int userId, long snMega, int gId, String name) {
		// Спочатку перевіряємо sn_megaList користувача (з кінця списку для виявлення недавніх дублікатів)
		for (KeyInfo keyInfo : keys.values()) {
			if (keyInfo.userID == userId) {
				synchronized (keyInfo) {
					// Перевіряємо з кінця списку, щоб виявити недавні дублікати
					for (int i = keyInfo.sn_megaList.size() - 1; i >= 0; i--) {
						SnInfo existingSnInfo = keyInfo.sn_megaList.get(i);
						if (existingSnInfo.sn_mega == snMega && existingSnInfo.g_id == gId) {
							String str = "{\"msg\":\"устройството вече присьтства във вашия списък\",\"status\":\"error\"}";
							updateUserDevicesFromDB(userId);
							return str;
						}
					}
					// Якщо пари не знайдено - додаємо до списку
					keyInfo.sn_megaList.add(SnInfo.create(snMega, gId, name));
				}
				break;
			}
		}

		// First add to database
		try (Connection conn = DatabaseHelper.connect()) {
			String sql2 = "select id from connections where g_id=? and c_id=?";
			PreparedStatement pstmt2 = conn.prepareStatement(sql2);
			pstmt2.setInt(1, gId);
			pstmt2.setInt(2, userId);
			ResultSet rs2 = pstmt2.executeQuery();
			if(rs2.next()) {
				// Якщо в БД вже існує - видаляємо з пам'яті те, що щойно додали
				for (KeyInfo keyInfo : keys.values()) {
					if (keyInfo.userID == userId) {
						synchronized (keyInfo) {
							keyInfo.sn_megaList.removeIf(snInfo -> snInfo.sn_mega == snMega && snInfo.g_id == gId);
						}
						break;
					}
				}
				String str = "{\"msg\":\"устройството вече присьтства във вашия списък\",\"status\":\"error\"}";
				updateUserDevicesFromDB(userId);
				return str;
			}
			String sql = "insert into connections(g_id, c_id) values(?, ?)";
			PreparedStatement pstmt = conn.prepareStatement(sql);
			pstmt.setInt(1, gId);
			pstmt.setInt(2, userId);
			pstmt.executeUpdate();
			updateUserDevicesFromDB(userId);
			String str = "{\"msg\":\"устройство е добавено към вашия списък\",\"status\":\"ok\"}";
			return str;
		} catch (SQLException e) {
			System.out.println(e.getMessage());
			// Якщо помилка БД - видаляємо з пам'яті те, що додали
			for (KeyInfo keyInfo : keys.values()) {
				if (keyInfo.userID == userId) {
					synchronized (keyInfo) {
						keyInfo.sn_megaList.removeIf(snInfo -> snInfo.sn_mega == snMega && snInfo.g_id == gId);
					}
					break;
				}
			}
			String str = "{\"msg\":\"грешка при добавянето на устройството\",\"status\":\"error\"}";
			return str;
		}
	}
	

	public static boolean delGad(int userId, int gId) {
		// First delete from database
		try (Connection conn = DatabaseHelper.connect()) {
			String sql = "DELETE FROM connections WHERE c_id = ? AND g_id = ?";
			try (PreparedStatement ps = conn.prepareStatement(sql)) {
				ps.setInt(1, userId);
				ps.setInt(2, gId);
				int rowsAffected = ps.executeUpdate();
				
				if (rowsAffected == 0) {
					return false; // No rows were deleted
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return false;
		}
	
		// Then update in-memory cache
		boolean removed = false;
		for (KeyInfo keyInfo : keys.values()) {
			if (keyInfo.userID == userId) {
				synchronized (keyInfo) {
					if (keyInfo.sn_megaList.removeIf(snInfo -> snInfo.g_id == gId)) {
						removed = true;
					}
				}
			}
		}
		return removed;
	}

	private static KeyInfo findKey(long key, InetAddress ip) {
		KeyInfo keyInfo = keys.get(key);
		if(keyInfo == null) {
//          System.out.println("// Ключ не знайдено");
			return null;
		}
		LocalDateTime now = LocalDateTime.now();
		if(equals2(ip, keyInfo.ip, 3) && (EXPIRATION_TIME_MINUTES == 0 || keyInfo.lastAccessTime.plusMinutes(EXPIRATION_TIME_MINUTES).isAfter(now))) {
			keyInfo.lastAccessTime = now;
			return keyInfo;
		} 
		else {
//          System.out.println("// час очікування вичерпано");
			keys.remove(key);
			return null;
		}
	}
	
	public static int checkKey(long key, InetAddress ip) {
		KeyInfo keyInfo = findKey(key, ip);
		if(keyInfo == null) {
			return 0;
		}
		return keyInfo.userID;
	}
	
	public static ArrayList<SnInfo> getSnMegaList(long key, InetAddress ip) {
		KeyInfo keyInfo = findKey(key, ip);
		if(keyInfo == null) {
			return null;
		}
		return new ArrayList<>(keyInfo.sn_megaList);
	}
	
	public static ArrayList<SnInfo> getSnMegaList(int userId) {
		for (KeyInfo keyInfo : keys.values()) {
			if (keyInfo.userID == userId) {
				return new ArrayList<>(keyInfo.sn_megaList);
			}
		}
		return null;
	}

	private static boolean equals2(InetAddress ip, InetAddress ip2, int stupin) {
		for(int i = 0; i < (ip.getAddress().length) - stupin; i++) {
			if(ip.getAddress()[i] != ip2.getAddress()[i]) {
//              System.out.println("// ip not equals\n" + ip + "\n" + ip2);
				return false;
			}
		}
		return true;
	}
	
	public static void setGadget(long key, InetAddress ip, long sn_mega) {
		KeyInfo keyInfo = findKey(key, ip);
		if(keyInfo != null) {
			keyInfo.sn_mega = sn_mega;
		}
	}
	
	public static long getGadget(long key, InetAddress ip) {
		KeyInfo keyInfo = findKey(key, ip);
		if(keyInfo != null) {
			return keyInfo.sn_mega;
		}
		return 0;
	}
	
	public static void logout(long X_Session_ID, InetAddress ip) {
		KeyInfo keyInfo = findKey(X_Session_ID, ip);
		if(keyInfo != null) {
			keys.remove(X_Session_ID);
		}
	}
	
	public static void cleanUpExpiredKeys() {
		if(EXPIRATION_TIME_MINUTES == 0)
			return;
		LocalDateTime now = LocalDateTime.now();
		Iterator<Map.Entry<Long, KeyInfo>> iterator = keys.entrySet().iterator();

		while(iterator.hasNext()) {
			Map.Entry<Long, KeyInfo> entry = iterator.next();
			KeyInfo keyInfo = entry.getValue();
			if(keyInfo.lastAccessTime.plusMinutes(EXPIRATION_TIME_MINUTES).isBefore(now)) {
				iterator.remove();
			}
		}

	}
}
