import java.util.HashMap;
import java.util.Map;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Configs {
	private static final Map<String, String> params = new HashMap<>();

	public static void init(String configFilePath) {
		try (BufferedReader br = new BufferedReader(new FileReader(configFilePath))) {
			String line;
			while ((line = br.readLine()) != null) {
				if(line.startsWith("#") || line.startsWith("[") || line.startsWith(";") || line.trim().length() == 0)
					continue;
				String[] parts = line.split("=", 2);
				if (parts.length >= 2) {
					params.put(parts[0].trim(), parts[1].trim());
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void priorityParam(String key, String value) {
		params.put(key, value);
	}

	private static boolean checkParam(String key) {
		if(params.get(key) == null) {
			System.out.println("Missing param: " + key);
			return false;
		}
		else {
			//System.out.println(key + ": " + params.get(key));
			return true;
		}
	}

	public static boolean validate() {
		if(getBoolean("https_run")) {
			String[] requiredKeys = {
				"keyStoreAlias",
				"acme_server_url",
				"acme_contact",
				"acme_account_key_file",
				"acme_domain_key_file",
				"acme_certificate_file",
				"acme_challenge_path"
			};

			for (String key : requiredKeys) {
				if (!checkParam(key)) return false;
			}
		}
		if(getBoolean("avr")) {
			if(getInt("avr_port") == 0) {
				System.out.println("Missing param: avr_port");
				return false;
			}
			if (!checkParam("avr_path")) return false;
			if (!checkParam("avr_user_agent")) return false;
		}
		if(getBoolean("liraCalc")) {
			if(getInt("port_liraCalc_server") == 0) {
				System.out.println("Missing param: port_liraCalc_server");
				return false;
			}
			if (!checkParam("ip_liraCalc_server")) return false;
		}
		if(getBoolean("esp")) {
			if(getInt("port_relay_server") == 0) {
				System.out.println("Missing param: port_relay_server");
				return false;
			}
			if (!checkParam("ip_relay_server")) return false;
		}

		String[] requiredKeys = {
			"invite",
			"www_directory",
			"www80_directory",
			"db_file",
			"keyStoreFile",
			"keyStorePassword",
			"host",
			"db_user",
			"db_password",
			"dbg_post_message_path"
		};

		for (String key : requiredKeys) {
			if (!checkParam(key)) return false;
		}

		return true;
	}

	public static String getParam(String key) {
		return params.get(key);
	}

	public static int getInt(String key) {
		String value = params.get(key);
		if (value == null || value.trim().isEmpty()) return 0;
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public static double getDouble(String key) {
		String value = params.get(key);
		if (value == null || value.trim().isEmpty()) return 0.0;
		try {
			return Double.parseDouble(value.trim());
		} catch (NumberFormatException e) {
			return 0.0;
		}
	}

	public static long getLong(String key) {
		String value = params.get(key);
		if (value == null || value.trim().isEmpty()) return 0L;
		try {
			return Long.parseLong(value.trim());
		} catch (NumberFormatException e) {
			return 0L;
		}
	}

	public static boolean getBoolean(String key) {
		String value = params.get(key);
		if (value == null) return false;
		value = value.trim().toLowerCase();
		return value.equals("true") || value.equals("1") || value.equals("yes") || value.equals("on");
	}

	public static boolean getDefine(String key) {
		return params.containsKey(key);
	}
}
