import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


class AvrRele {
	private final Lock lock = new ReentrantLock();
	public int g_id;
	private MyRand myRand;
	public long serialNumberMega;
	public long serialNumberEsp;
	public long lastAccessTime;
	public boolean online;
	public boolean robota;
	public char[] name = new char[33];
	public byte[] dsplbuf = new byte[128];
	public long obscht_r, tek_r;
	public int in, in_mem;
	public byte cory;
	public byte navihacija_menu;
	public byte rezhym_tmr;
	public byte[][] rz = new byte[3][13];
	public byte[][] ri = new byte[3][17];
	public byte[][] rv = new byte[3][19];
	public byte[] arr_golovne_menu = new byte[16];
	private boolean saveToDB;
	private byte upd;
	private short adminpass;
	private byte perev_upd;
	private long[] ri_strt = new long[3];
	private long[] ri_tek = new long[3];
	private int[] ri_imp = new int[3];
	private int[] ri_strt_imp = new int[3];
	private int[] tekimp_mem = new int[3];

	private static final byte[] DEFAULT_RESPONSE = parseHexPlus(
			"45+50+27+49+5D+BA+73+BC+38+4B+A6+87+5A+29+5F+28+47+94+9A+20+CB+2F+25+0B+33+D6+65+26+40+7E+3E+F0+30+68+36+70+AF+C3+00+00+00+00+00+00+00");

	private static byte[] parseHexPlus(String hex) {
		String[] tmp = hex.split("\\+");
		byte[] result = new byte[tmp.length];
		for(int i = 0; i < tmp.length; i++) {
			result[i] = (byte) Integer.parseInt(tmp[i], 16);
		}
		return result;
	}

	public AvrRele(long serialNumber) {
		myRand = new MyRand();
		serialNumberMega = serialNumber;
		lastAccessTime = System.currentTimeMillis();
		online = false;
		robota = false;
		saveToDB = false;
		ri_strt[0] = -1;
		ri_strt[1] = -1;
		ri_strt[2] = -1;
		ri_strt_imp[0] = -1;
		ri_strt_imp[1] = -1;
		ri_strt_imp[2] = -1;
		ri_tek[0] = -1;
		ri_tek[1] = -1;
		ri_tek[2] = -1;
		adminpass = 0;
		perev_upd = 0;
		for(int i = 0; i < 3; i++)
			tekimp_mem[i] = -1;
		Connection conn = DatabaseHelper.connect();
		String findRele = "SELECT g_id FROM gadgets WHERE sn_mega=?;";
		g_id = 0;
		try (PreparedStatement pstmt = conn.prepareStatement(findRele)) {
			pstmt.setInt(1, (int)serialNumber);
			try (ResultSet rs = pstmt.executeQuery()) {
				if(rs.next()) {
					g_id = rs.getInt("g_id");
				}
			} catch (SQLException e) {
				System.out.println(e.getMessage());
			}
			if(g_id == 0) {
				String createRele = "insert into gadgets(name, sn_mega) VALUES(?, ?)";
				try {
					PreparedStatement pstmt2 = conn.prepareStatement(createRele);
					pstmt2.setString(1, "lialiapam");
					saveToDB = true;
					pstmt2.setInt(2, (int)serialNumber);
					pstmt2.executeUpdate();
					System.out.println("Data inserted successfully SN_MEGA = " + serialNumber);
					//conn.commit();
					try {
						PreparedStatement pstmt3 = conn.prepareStatement(findRele);
						pstmt3.setInt(1, (int)serialNumber);
						try (ResultSet rs2 = pstmt3.executeQuery()) {
							if(rs2.next()) {
								g_id = rs2.getInt("g_id");
								System.out.println("G_ID = " + g_id);
							}
						} catch (SQLException e) {
							System.out.println(e.getMessage());
						}
					} catch (SQLException e) {
						System.out.println(e.getMessage());
					}
				} catch (SQLException e) {
					System.out.println(e.getMessage());
				}
			}
			else {
				loadRele();
			}
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		} finally {
			if (conn != null) {
				try {
					//conn.commit();
					conn.close(); // Always close the connection
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private long bytesToInt(byte[] bytes, int offset) {
		return (((long)bytes[offset] & 0xFF)) |
			   (((long)bytes[offset + 1] & 0xFF) << 8) |
			   (((long)bytes[offset + 2] & 0xFF) << 16) |
			   (((long)bytes[offset + 3] & 0xFF) << 24);
	}
	
	private int bytesToShort(byte[] bytes, int offset) {
		return (((int)bytes[offset] & 0xFF)) |
			   (((int)bytes[offset + 1] & 0xFF) << 8);
	}
	
	public long readRuntimeAbsolute() {
		lock.lock();
		try {
			// obscht_r ВЖЕ містить поточну сесію (update(): obscht_r = obscht_r2 + tmpint),
			// і саме він пишеться/читається з БД. Додавати tek_r не можна — це дало б
			// подвійний tek онлайн і розбіжність із baseline, знятим у стані "щойно з БД"
			// (tek_r=0), через що forwarder ловив elapsed == tek.
			return obscht_r;
		} finally {
			lock.unlock();
		}
	}

	public byte[] update(byte requbufByte[]) {
		lock.lock();
		try {
			lastAccessTime = System.currentTimeMillis();
			online = true;
			byte[] result = DEFAULT_RESPONSE;
			if(requbufByte.length == 40) {
//				System.out.print("=============connect\t\t\t");
				serialNumberEsp = 0;
				int i;
				for(i = 32; i < 36; i++) {
					serialNumberEsp += (long)((long)requbufByte[i] & 0xFF) << (8 * (i - 32));
				}
				char[] nameChar = new char[33];
				for(i = 0; i < 28; i++) {
					nameChar[i] = (char)requbufByte[i];
				}
				nameChar[i] = 0;
				if(!nameChar.toString().equals(name.toString())) {
					saveToDB = true;
					System.arraycopy(nameChar, 0, name, 0, nameChar.length);
				}
//				System.out.println(name);
				myRand.srand(requbufByte);
				char[] vidpChar = "ok_chuvak_ja_tebe_pojnjav_davaj_korysni_dani".toCharArray();
				byte[] vidpByte = new byte[vidpChar.length + 1];
				vidpByte[vidpChar.length] = 0;
				for(i = 0; i < vidpChar.length; i++) {
					vidpByte[i] = (byte)((byte)vidpChar[i] ^ myRand.rand());
					vidpByte[vidpChar.length] ^= (byte)vidpChar[i];
				}
				myRand.korr(vidpByte[vidpChar.length]);
				result = vidpByte;
			}
			else if(requbufByte.length == 320) {
				serialNumberEsp = 0;
				int i;
				for(i = 312; i < 316; i++) {
					serialNumberEsp += (long)((long)requbufByte[i] & 0xFF) << (8 * (i - 32));
				}
				byte ks = 0;
				for(i = 0; i < 311; i++) {
					requbufByte[i] ^= myRand.rand();
					ks ^= requbufByte[i];
				}
				if(ks == requbufByte[311] && requbufByte[306] == (byte)0x1a && requbufByte[307] == (byte)0xa1 && requbufByte[308] == (byte)0x1a && requbufByte[309] == (byte)0xa1) {
					perev_upd = requbufByte[310];			// результат превірки adminpass/upd
					if(upd != 0 && perev_upd != 0) {
						upd = 0;
						adminpass = 0;
					}
					myRand.korr(ks);
					for(i = 0; i < 128; i++)
						dsplbuf[i] = requbufByte[i];

					long obscht_r2 = bytesToInt(requbufByte, 0x80);
					long tmpint = bytesToInt(requbufByte, 0x84);
					if(obscht_r2 + tmpint != 0) {
						if(obscht_r != obscht_r2 + tmpint) {
							if(robota == false) {
								LogFiles.pushLog(serialNumberMega, obscht_r2, tmpint, obscht_r, "Start Work");
							}
							robota = true;
						}
						else {
							if(robota == true) {
								LogFiles.pushLog(serialNumberMega, obscht_r2, tmpint, obscht_r, "End Work");
							}
							robota = false;
						}
						
						if(obscht_r2 + tmpint < obscht_r) {
							LogFiles.pushLog(serialNumberMega, obscht_r2, tmpint, obscht_r, "BD time Error");
						}	
						obscht_r = obscht_r2 + tmpint;
						tek_r = tmpint;
						
						in = bytesToShort(requbufByte, 0x88);
						in_mem = bytesToShort(requbufByte, 0x8a);
						cory = requbufByte[0x8c];
						navihacija_menu = requbufByte[0x8d];
					}
					else {
						StringBuilder sb = new StringBuilder();
						sb.append("The incoming package contains incorrect data, despite similar checksums.\n");
						for(int j = 0; j < 20; j++) {
							for(i = 0; i < 16; i++) {
								sb.append(String.format("%02X ", requbufByte[i + (j * 16)]));
							}
							sb.append("\n");
						}
						LogFiles.pushLog(serialNumberMega, "The incoming package contains incorrect data, resv 0x05 mega=>esp zero all.");
					}
					char[] vidpChar = null;
					byte[] vidpByte = null;
					int len = 0;
					if(upd == 0) {
						if(obscht_r2 + tmpint != 0) {
							rezhym_tmr = requbufByte[0x8e];
							for(int j = 0; j < 3; j++) {
								for(i = 0; i < 19; i++) {
									if(i < 13)
										rz[j][i] = requbufByte[i + 0x8f + (j * 13)];
									if(i < 17)
										ri[j][i] = requbufByte[i + 0xb6 + (j * 17)];
									rv[j][i] = requbufByte[i + 0xe9 + (j * 19)];	
								}
								int setimp = bytesToShort(ri[j], 12);
								int tekimp = bytesToShort(ri[j], 14);
								//System.out.printf("====================================================\n%d-----------\nset %d tek %d \n--------------\nri_strt = %d\n", j + 1, setimp, tekimp, ri_strt[j]);
								
								if(ri_strt[j] == -1) {
									if(tekimp < setimp && tekimp == ri_strt_imp[j] - 1) {
										ri_strt[j] = obscht_r;
										ri_imp[j] = tekimp;
										ri_strt_imp[j] = tekimp + 1;
									}
									else if(tekimp < setimp) {
										ri_strt_imp[j] = tekimp;
									}
								}
								else {
									if(setimp == tekimp || tekimp == 0) {
										ri_strt[j] = -1;
										ri_tek[j] = -1;
										ri_strt_imp[j] = setimp;
									}
									else if(ri_imp[j] != tekimp) {
										ri_imp[j] = tekimp;
										ri_tek[j] = obscht_r;
									}
								}
								if(tekimp != tekimp_mem[j] && getWorkElem("Ri", j)) {
									LogFiles.pushLog(serialNumberMega, j + 1, setimp, tekimp, obscht_r);
									tekimp_mem[j] = tekimp;
								}
							}
						}
						for(i = 0; i < 16; i++)
							arr_golovne_menu[i] = requbufByte[i + 0x122];
						
						if(saveToDB == true) {
							saveRele();
							saveToDB = false;
						}
						
						vidpChar = "ok_chuvak_ja_tebe_pojnjav_davaj_korysni_dani".toCharArray();
						len = vidpChar.length;
						vidpByte = new byte[len + 1];
						vidpByte[len] = 0;
						
					}
					else if(upd == 18) {
						len = 4;
						vidpByte = new byte[len + 1];
						vidpByte[len] = 0;
						
						vidpByte[3] = (byte)0xa5;
						vidpByte[2] = (byte)0xa5;
						vidpByte[0] = (byte)(adminpass & 0x00ff);
						vidpByte[1] = (byte)((adminpass >> 8) & 0x00ff);
					}
					else if(upd >= 1 && upd <= 10) {
						len = 4 + 17;
						vidpByte = new byte[len + 1];
						for(byte vbt : vidpByte) {
							vbt = 0;
						}
						vidpByte[16] = (byte)(0xf0 | upd);
						vidpByte[len] = 0;
						vidpByte[3 + 17] = (byte)0xa5;
						vidpByte[2 + 17] = (byte)0xa5;
						vidpByte[0 + 17] = (byte)(adminpass & 0x00ff);
						vidpByte[1 + 17] = (byte)((adminpass >> 8) & 0x00ff);
					}
					else if(upd == 29) {
						int ddd = 20;
						len = 4 + 17 * ddd;
						vidpByte = new byte[len + 1];
						for(byte vbt : vidpByte) {
							vbt = 0;
						}
						for(int dd = 0; dd < ddd; dd++)
							vidpByte[16 + dd * 17] = (byte)(0xf0 | (byte)((dd % 10) + 1));
						vidpByte[len] = 0;
						vidpByte[len - 1] = (byte)0xa5;
						vidpByte[len - 2] = (byte)0xa5;
						vidpByte[len - 4] = (byte)(adminpass & 0x00ff);
						vidpByte[len - 3] = (byte)((adminpass >> 8) & 0x00ff);
					}
					
					for(i = 0; i < len; i++) {
						if(upd == 0) {
							vidpByte[i] = (byte)((byte)vidpChar[i] ^ myRand.rand());
							vidpByte[len] ^= (byte)vidpChar[i];
						}
						else {
							vidpByte[len] ^= vidpByte[i];
							vidpByte[i] = (byte)(vidpByte[i] ^ myRand.rand());
						}
					}
					myRand.korr(vidpByte[len]);
					result = vidpByte;
				}
			}
			else {
				System.out.println("ERROR requbufByte.length != 40/320");
			}
			requbufByte = null;
			return result;
		} finally {
			lock.unlock();
		}
	}

	public long getSerialNumber() {
		return serialNumberMega;
	}

	public long getLastAccessTime() {
		return lastAccessTime;
	}
	
	private byte[] byteArr2to1(byte[][] data) {
		byte[] data2 = new byte[data.length * data[0].length];
		for(int i = 0; i < data.length; i++) {
			for(int j = 0; j < data[i].length; j++) {
				data2[i * data[i].length + j] = data[i][j];
			}
		}
		return data2;
	}
	
	private byte[][] byteArr1to2(byte[] data, int ii) {
		byte[][] data2 = new byte[ii][data.length / ii];
		for(int i = 0; i < ii; i++) {
			for(int j = 0; j < data.length / ii; j++) {
				data2[i][j] = data[i * (data.length / ii) + j];
			}
		}
		return data2;
	}
	
	public synchronized void saveRele() {
		Connection conn = DatabaseHelper.connect();
		if(conn == null) {
			System.out.println("DB Connection is null");
			return;
		}
		String updGad = "UPDATE gadgets SET name = ?, sn_esp = ?, obscht_r = ?, rezhym_tmr = ?, rz = ?, ri = ?, rv = ?, arr_golovne_menu = ?, upd = ? WHERE g_id=?;";
		try (PreparedStatement pstmt = conn.prepareStatement(updGad)) {
			pstmt.setInt(10, g_id);
			pstmt.setString(1, new String(name));
			pstmt.setInt(2, (int)serialNumberEsp);
			pstmt.setInt(3, (int)(obscht_r));
			pstmt.setByte(4, rezhym_tmr);
			pstmt.setBytes(5, byteArr2to1(rz));
			pstmt.setBytes(6, byteArr2to1(ri));
			pstmt.setBytes(7, byteArr2to1(rv));
			pstmt.setBytes(8, arr_golovne_menu);
			pstmt.setInt(9, upd);
			pstmt.executeUpdate();
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
	
	public boolean loadRele() {
		Connection conn = DatabaseHelper.connect();
		String sql = "select * from gadgets where g_id = ?";
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setInt(1, g_id);
			try (ResultSet rs = pstmt.executeQuery()) {
				if(rs.next()) {
					name = rs.getString("name").toCharArray();
					serialNumberEsp = (long)rs.getInt("sn_esp") & 0xFFFFFFFFL;
					obscht_r = (long)rs.getInt("obscht_r");
					rezhym_tmr = rs.getByte("rezhym_tmr");
					rz = byteArr1to2(rs.getBytes("rz"), 3);
					ri = byteArr1to2(rs.getBytes("ri"), 3);
					rv = byteArr1to2(rs.getBytes("rv"), 3);
					arr_golovne_menu = rs.getBytes("arr_golovne_menu");
					upd = (byte)rs.getInt("upd");
					return true;
				}
			} catch (SQLException e) {
				System.out.println(e.getMessage());
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
		return false;
	}
	
	public boolean validPin(String pin) {
		
		lock.lock();
		try {
			if(pin.matches("\\d{4}")) {
				upd = 18;
				char[] pin2 = pin.toCharArray();
				adminpass = 0;
				adminpass += Character.getNumericValue(pin2[0]);
				adminpass <<= 4;
				adminpass += Character.getNumericValue(pin2[1]);
				adminpass <<= 4;
				adminpass += Character.getNumericValue(pin2[2]);
				adminpass <<= 4;
				adminpass += Character.getNumericValue(pin2[3]);
				return true;
			}
			return false;
		} finally {
			lock.unlock();
		}
	}
	/*
		1	dija_vk_return	rz_1
		2	dija_vk_return	rz_2
		3	dija_vk_return	rz_3
		4	dija_vk_return	ri_1
		5	dija_vk_return	ri_2
		6	dija_vk_return	ri_3
		7	dija_vk_return	rv_1
		8	dija_vk_return	rv_2
		9	dija_vk_return	rv_3
		10	dija_vk_return	tmr
	*/
	public boolean sendReset(HTTPRequest httpRequest) {
		String pin = httpRequest.getZnach("pin");
		byte upd = 0;
		if(httpRequest.path.equals("/reset_null_tek"))
			upd = 10;
		else if(httpRequest.path.equals("/reset_res_rz_1"))
			upd = 1;
		else if(httpRequest.path.equals("/reset_res_rz_2"))
			upd = 2;
		else if(httpRequest.path.equals("/reset_res_rz_3"))
			upd = 3;
		else if(httpRequest.path.equals("/reset_res_ri_1"))
			upd = 4;
		else if(httpRequest.path.equals("/reset_res_ri_2"))
			upd = 5;
		else if(httpRequest.path.equals("/reset_res_ri_3"))
			upd = 6;
		else if(httpRequest.path.equals("/reset_res_rv_1"))
			upd = 7;
		else if(httpRequest.path.equals("/reset_res_rv_2"))
			upd = 8;
		else if(httpRequest.path.equals("/reset_res_rv_3"))
			upd = 9;
		else if(httpRequest.path.equals("/reset_all"))
			upd = 29;
		if(upd == 0)
			return false;
		lock.lock();
		try {
			if(pin.matches("\\d{4}")) {
				this.upd = upd;
				char[] pin2 = pin.toCharArray();
				adminpass = 0;
				adminpass += Character.getNumericValue(pin2[0]);
				adminpass <<= 4;
				adminpass += Character.getNumericValue(pin2[1]);
				adminpass <<= 4;
				adminpass += Character.getNumericValue(pin2[2]);
				adminpass <<= 4;
				adminpass += Character.getNumericValue(pin2[3]);
				return true;
			}
			return false;
		} finally {
			lock.unlock();
		}
	}
	
	public byte getPerevUpd() {
		return perev_upd;
	}
	
	public boolean setEnbl(String rr, int indx, char[] set, String pin) {
		byte[] strt = null;
		if(rr.startsWith("Rz"))
			strt = rz[indx];
		if(rr.startsWith("Ri"))
			strt = ri[indx];
		if(rr.startsWith("Rv"))
			strt = rv[indx];
		if(validPin(pin) == false || strt == null) {
			upd = 0;
			return false;
		}
		lock.lock();
		try {
			if(String.valueOf(set).matches("^[01-]{16}$")) {
				
				upd = 17;
				strt[1] = 0;
				strt[3] = 0;
				for(int i = 0; i < 8; i++) {
					strt[1] |= ((set[i] == '-' ? 0 : 1) << (7 - i));
					strt[3] |= ((set[i] == '1' ? 1 : 0) << (7 - i));
				}
				strt[0] = 0;
				strt[2] = 0;
				for(int i = 0; i < 8; i++) {
					strt[0] |= ((set[i + 8] == '-' ? 0 : 1) << (7 - i));
					strt[2] |= ((set[i + 8] == '1' ? 1 : 0) << (7 - i));
				}
				return true;
			}
			else {
				upd = 0;
				return false;
			}
		} finally {
			lock.unlock();
		}
	}
	
	public char[] getEnbl(String rr, int indx) {
		byte[] strt = null;
		if(rr.startsWith("Rz"))
			strt = rz[indx];
		if(rr.startsWith("Ri"))
			strt = ri[indx];
		if(rr.startsWith("Rv"))
			strt = rv[indx];
		if(strt == null) {
			return null;
		}
		char[] rez = new char[16];
						
		for(int i = 0; i < 8; i++) {
			rez[i] = (strt[1] & (1 << (7 - i))) == 0 ? '-' : ((strt[3] & (1 << (7 - i))) == 0 ? '0' : '1');
		}
		for(int i = 0; i < 8; i++) {
			rez[i + 8] = (strt[0] & (1 << (7 - i))) == 0 ? '-' : ((strt[2] & (1 << (7 - i))) == 0 ? '0' : '1');
		}
		if(String.valueOf(rez).matches("^[01-]{16}$"))
			return rez;
		return null;
	}
	
	public boolean setClck(String rr, int indx, char[] set, String pin) {
		byte[] strt = null;
		if(rr.startsWith("Rz"))
			strt = rz[indx];
		if(rr.startsWith("Ri"))
			strt = ri[indx];
		if(rr.startsWith("Rv"))
			strt = rv[indx];
		if(validPin(pin) == false || strt == null) {
			upd = 0;
			return false;
		}
		lock.lock();
		try {
			if(String.valueOf(set).matches("^[01-]{16}$")) {
				
				upd = 17;
				strt[5] = 0;
				strt[7] = 0;
				for(int i = 0; i < 8; i++) {
					strt[5] |= ((set[i] == '-' ? 0 : 1) << (7 - i));
					strt[7] |= ((set[i] == '1' ? 1 : 0) << (7 - i));
				}
				strt[4] = 0;
				strt[6] = 0;
				for(int i = 0; i < 8; i++) {
					strt[4] |= ((set[i + 8] == '-' ? 0 : 1) << (7 - i));
					strt[6] |= ((set[i + 8] == '1' ? 1 : 0) << (7 - i));
				}
				return true;
			}
			else {
				upd = 0;
				return false;
			}
		} finally {
			lock.unlock();
		}
	}
	
	public char[] getClck(String rr, int indx) {
		byte[] strt = null;
		if(rr.startsWith("Rz"))
			strt = rz[indx];
		if(rr.startsWith("Ri"))
			strt = ri[indx];
		if(rr.startsWith("Rv"))
			strt = rv[indx];
		if(strt == null) {
			return null;
		}
		char[] rez = new char[16];
						
		for(int i = 0; i < 8; i++) {
			rez[i] = (strt[5] & (1 << (7 - i))) == 0 ? '-' : ((strt[7] & (1 << (7 - i))) == 0 ? '0' : '1');
		}
		for(int i = 0; i < 8; i++) {
			rez[i + 8] = (strt[4] & (1 << (7 - i))) == 0 ? '-' : ((strt[6] & (1 << (7 - i))) == 0 ? '0' : '1');
		}
		if(String.valueOf(rez).matches("^[01-]{16}$"))
			return rez;
		return null;
	}
	
	public boolean setOut(String rr, int indx, char[] set, String pin, byte autores, byte trmtn, boolean setOrClock, boolean setResWithClock) {
		byte[] strt = null;
		if(rr.startsWith("Rz"))
			strt = rz[indx];
		if(rr.startsWith("Ri"))
			strt = ri[indx];
		if(rr.startsWith("Rv"))
			strt = rv[indx];
		if(validPin(pin) == false || strt == null || autores > 3 || autores < 0 || trmtn > 3 || trmtn < 0) {
			upd = 0;
			return false;
		}
		lock.lock();
		try {
			if(String.valueOf(set).matches("^[01]{26}$")) {
				
				upd = 17;
				strt[9] = 0;
				strt[11] = 0;
				for(int i = 0; i < 5; i++) {
					strt[9] |= ((set[i] == '1' ? 1 : 0) << (4 - i));
					strt[11] |= ((set[i + 13] == '1' ? 1 : 0) << (4 - i));
				}
				strt[9] |= setOrClock ? 0x80 : 0;
				if(rr.startsWith("Rv") && setResWithClock)
					strt[11] |= 0x80;
				strt[9] |= autores << 5;
				strt[11] |= trmtn << 5;
				
				strt[8] = 0;
				strt[10] = 0;
				for(int i = 0; i < 8; i++) {
					strt[8] |= ((set[i + 5] == '1' ? 1 : 0) << (7 - i));
					strt[10] |= ((set[i + 18] == '1' ? 1 : 0) << (7 - i));
				}
				return true;
			}
			else {
				upd = 0;
				return false;
			}
		} finally {
			lock.unlock();
		}
	}
	
	public char[] getOut1(String rr, int indx) {
		byte[] strt = null;
		if(rr.startsWith("Rz"))
			strt = rz[indx];
		if(rr.startsWith("Ri"))
			strt = ri[indx];
		if(rr.startsWith("Rv"))
			strt = rv[indx];
		if(strt == null) {
			return null;
		}
		char[] rez = new char[16];
						
		for(int i = 0; i < 8; i++) {
			rez[i] = (strt[9] & (1 << (7 - i))) == 0 ? (i < 3 ? '0' : '-') : '1';
		}
		for(int i = 0; i < 8; i++) {
			rez[i + 8] = (strt[8] & (1 << (7 - i))) == 0 ? '-' : '1';
		}
		if(String.valueOf(rez).matches("^[-01]{16}$"))
			return rez;
		return null;
	}
	
	public char[] getOut2(String rr, int indx) {
		byte[] strt = null;
		if(rr.startsWith("Rz"))
			strt = rz[indx];
		if(rr.startsWith("Ri"))
			strt = ri[indx];
		if(rr.startsWith("Rv"))
			strt = rv[indx];
		if(strt == null) {
			return null;
		}
		char[] rez = new char[16];
						
		for(int i = 0; i < 8; i++) {
			rez[i] = (strt[11] & (1 << (7 - i))) == 0 ? (i < 3 ? '0' : '-') : '1';
		}
		for(int i = 0; i < 8; i++) {
			rez[i + 8] = (strt[10] & (1 << (7 - i))) == 0 ? '-' : '1';
		}
		if(String.valueOf(rez).matches("^[-01]{16}$"))
			return rez;
		return null;
	}
	
	public byte getTrmtn(String rr, int indx) {
		byte[] strt = null;
		if(rr.startsWith("Rz"))
			strt = rz[indx];
		if(rr.startsWith("Ri"))
			strt = ri[indx];
		if(rr.startsWith("Rv"))
			strt = rv[indx];
		if(strt == null) {
			return -1;
		}
		
		return (byte)((strt[11] & 0b01100000) >> 5);
	}
	
	public byte getAutores(String rr, int indx) {
		byte[] strt = null;
		if(rr.startsWith("Rz"))
			strt = rz[indx];
		if(rr.startsWith("Ri"))
			strt = ri[indx];
		if(rr.startsWith("Rv"))
			strt = rv[indx];
		if(strt == null) {
			return -1;
		}
		
		return (byte)((strt[9] & 0b01100000) >> 5);
	}
	
	public byte getOrClock(String rr, int indx) {
		byte[] strt = null;
		if(rr.startsWith("Rz"))
			strt = rz[indx];
		if(rr.startsWith("Ri"))
			strt = ri[indx];
		if(rr.startsWith("Rv"))
			strt = rv[indx];
		if(strt == null) {
			return -1;
		}
		if((strt[9] & 0x80) == 0)
			return 0;
		return 1;
	}
	
	public byte getResWithClock(String rr, int indx) {
		byte[] strt = null;
		if(rr.startsWith("Rv"))
			strt = rv[indx];
		if(strt == null) {
			return -1;
		}
		
		if((strt[11] & 0x80) == 0)
			return 0;
		return 1;
	}
	
	public boolean getWorkElem(String rr, int indx) {
		byte[] strt = null;
		if(rr.startsWith("Rz"))
			strt = rz[indx];
		if(rr.startsWith("Ri"))
			strt = ri[indx];
		if(rr.startsWith("Rv"))
			strt = rv[indx];
		if(strt == null) {
			return false;
		}
		if(strt[0] == 0 && strt[1] == 0 && strt[4] == 0 && strt[5] == 0)
			return false;
		return true;
	}
	
	public String getSetString(String rr, int indx) {
		if(rr.startsWith("Ri")) {
			return String.format("imp = %d", ((int)(ri[indx][13]) << 8) | ((int)(ri[indx][12]) & 0x00ff));
		}
			
		if(rr.startsWith("Rv")) {
			return String.format("tm = %.1f%c", (float)(((int)(rv[indx][13]) << 8) | ((int)(rv[indx][12]) & 0x00ff)) / 25, (rv[indx][18] == 0 ? 's' : 'm'));
		}
		return "none";
	}
	
	public String getStrImp(int j, String txt) {
		if(ri_strt[j] == -1) {
			return txt;
		}
		else {
			int setimp = bytesToShort(ri[j], 12);
			int tekimp = bytesToShort(ri[j], 14);
			int rizn = ri_strt_imp[j] - tekimp - 1;
			int srartm = 0;
			int zalyshok_chasu = 0;
			if(rizn > 0 && robota == true) {
				srartm = (int)(ri_tek[j] - ri_strt[j]) / rizn;
				zalyshok_chasu = srartm * tekimp - (int)(obscht_r - ri_tek[j]);
				return String.format("<p>%s</p><p style='font-family: Arial, sans-serif; font-size: 18px;'>%02d:%02d / imp</p><p style='font-family: Arial, sans-serif; font-size: 14px;'>до края на цикл:</p><p style='font-family: Arial, sans-serif; font-size: 18px;'>%02d:%02d:%02d</p></tbody>", txt, srartm / 60, srartm % 60, zalyshok_chasu / 3600, (zalyshok_chasu / 60) % 60, zalyshok_chasu % 60);
			}
			return txt;
		}
	}
	
	public void resetUpdBt() {
		upd = 0;
	}
	
	public String getName() {
		return new String(name).trim();
	}
}


/*				
					upd
					
					1	dija_vk_return	rz_1
					2	dija_vk_return	rz_2
					3	dija_vk_return	rz_3
					4	dija_vk_return	ri_1
					5	dija_vk_return	ri_2
					6	dija_vk_return	ri_3
					7	dija_vk_return	rv_1
					8	dija_vk_return	rv_2
					9	dija_vk_return	rv_3
					10	dija_vk_return	tmr
					11	setup			ri_1
					12	setup			ri_2
					13	setup			ri_3
					14	setup			rv_1
					15	setup			rv_2
					16	setup			rv_3
					17	updAll		
					18	validPIN
					
					обо'язкова передача пароля разом з пакетом оновлення 
																		 pin adminpass
																		
					perev_upd
					
					0
					1 OK
					2 NOT OK
	*/
