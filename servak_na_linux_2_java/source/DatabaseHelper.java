import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public class DatabaseHelper {
	private static final String URL = "jdbc:sqlite:" + Configs.getParam("db_file");
	private static final String USER = Configs.getParam("db_user");
	private static final String PASSWORD = Configs.getParam("db_password");

	public static Connection connect() {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(URL, USER, PASSWORD);
            // Enable WAL journal mode for concurrent reads and serialized writes
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA synchronous=NORMAL");
                stmt.execute("PRAGMA busy_timeout = 5000");
            }
        } catch (SQLException e) {
            System.out.println("DB connection error: " + e.getMessage());
        }
        return conn;
    }

    private static boolean tableExists(Connection conn, String tableName) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return false;
    }

	    // Call this once on application startup to ensure tables exist
    public static void initializeTables() {
        try (Connection conn = connect()) {
            if (conn == null) return;
            try (Statement stmt = conn.createStatement()) {
                if (!tableExists(conn, "clients")) {
                    stmt.execute("CREATE TABLE clients (" +
                            " c_id integer PRIMARY KEY," +
                            " login text NOT NULL," +
                            " password text NOT NULL" +
                            ");");
                }
                if (!tableExists(conn, "gadgets")) {
                    stmt.execute("CREATE TABLE gadgets (" +
                            " g_id integer PRIMARY KEY," +
                            " name text," +
                            " sn_mega integer NOT NULL," +
                            " sn_esp integer," +
                            " obscht_r integer," +
                            " rezhym_tmr BLOB," +
                            " rz BLOB," +
                            " ri BLOB," +
                            " rv BLOB," +
                            " arr_golovne_menu BLOB," +
                            " upd BLOB" +
                            ");");
                }
                if (!tableExists(conn, "connections")) {
                    stmt.execute("CREATE TABLE connections (" +
                            " id integer PRIMARY KEY," +
                            " c_id integer NOT NULL," +
                            " g_id integer NOT NULL" +
                            ");");
                }
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }
}

