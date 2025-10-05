import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;



public final class RegistrUsers {
		
    private RegistrUsers() {
        throw new UnsupportedOperationException("Utility class");
    }
	
	public static HTTPResponse checkAuthorization(HTTPRequest httpRequest) {
		int userID = 0;
		if(httpRequest.X_Session_ID != 0) {
			userID = KeyManager.checkKey(httpRequest.X_Session_ID, httpRequest.clientAddress);
		}
		if(userID == 0)
			return new HTTPResponse(400);
		else
			return new HTTPResponse(200);
	}
	
	public static HTTPResponse reestr(HTTPRequest httpRequest) {
		if(httpRequest.chkZnach("reestr", "true")) {
			String sql = "select c_id from clients WHERE login = ?";
			Connection conn = DatabaseHelper.connect();
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				
				pstmt.setString(1, httpRequest.getZnach("login"));
				ResultSet rs = pstmt.executeQuery();
				if(rs.next() || httpRequest.getZnach("invite").equals(Configs.getParam("invite") + httpRequest.getZnach("login"))) {
					if(httpRequest.getZnach("invite").equals("delete")) {
						sql = "DELETE FROM clients WHERE login = ? and password = ?";
						try (PreparedStatement pstmt2 = conn.prepareStatement(sql)) {
							pstmt2.setString(1, httpRequest.getZnach("login"));
							pstmt2.setString(2, httpRequest.getZnach("password"));
							pstmt2.executeUpdate();
							conn.commit();
							String resp = String.format("""
								{
									"ok": "OK",
									"res": "/"
								}
							""");
							return new HTTPResponse("HTTP/1.1 200 OK\r\nServer: MijServak\r\nContent-Length: " + resp.length() + "\r\nContent-Type: application/json\r\nConnection: Closed\r\n\r\n", resp.getBytes(), "Create New User");
						} catch (SQLException e) {
							System.out.println(e.getMessage());
						}
					}
					return new HTTPResponse(500);
				}
				else {
					sql = "insert into clients(login, password) VALUES(?, ?)";
					try (PreparedStatement pstmt2 = conn.prepareStatement(sql)) {
						pstmt2.setString(1, httpRequest.getZnach("login"));
						pstmt2.setString(2, httpRequest.getZnach("password"));
						pstmt2.executeUpdate();
						conn.commit();
						String resp = String.format("""
							{
								"ok": "OK",
								"res": "/"
							}
						""");
						return new HTTPResponse("HTTP/1.1 200 OK\r\nServer: MijServak\r\nContent-Length: " + resp.length() + "\r\nContent-Type: application/json\r\nConnection: Closed\r\n\r\n", resp.getBytes(), "Create New User");
					} catch (SQLException e) {
						System.out.println(e.getMessage());
					}
				}
			} catch (SQLException e) {
				System.out.println(e.getMessage());
			} finally {
				if (conn != null) {
					try {
						conn.close(); // Always close the connection
					} catch (SQLException e) {
						e.printStackTrace();
					}
				}
			}
		}
		else {
			Connection conn = DatabaseHelper.connect();
			String sql = "select * from clients WHERE login = ? and password = ?";
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				
				pstmt.setString(1, httpRequest.getZnach("login"));
				pstmt.setString(2, httpRequest.getZnach("password"));
				ResultSet rs = pstmt.executeQuery();
				if(rs.next()) {
					int c_id = rs.getInt("c_id");
					long key = KeyManager.genKey(c_id, httpRequest.clientAddress);
					String resp = String.format("""
						{
							"ok": "OK",
							"res": "index.html"
						}
					""", key);
					KeyManager.updateUserDevicesFromDB(c_id);
					return new HTTPResponse("HTTP/1.1 200 OK\r\nSet-Cookie: X-Session-ID=" + key + "; Path=/; HttpOnly; Secure; SameSite=Strict\r\nServer: MijServak\r\nContent-Length: " + resp.length() + "\r\nContent-Type: application/json\r\nConnection: Closed\r\n\r\n", resp.getBytes(), "LogIN User\t" + rs.getString("login"));
				}
			} catch (SQLException e) {
				System.out.println(e.getMessage());
			} finally {
				if (conn != null) {
					try {
						conn.close();
					} catch (SQLException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return new HTTPResponse(500);
	}
}
