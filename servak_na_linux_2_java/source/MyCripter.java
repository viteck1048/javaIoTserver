// Дослівний порт MyCripter зі скетча ESP-01 (MachineTime). Лишив активний шлях toBase64 (HEX_REQUEST вимкнено).
import java.nio.charset.StandardCharsets;

public class MyCripter {
	private MyRand3 randGen;
	private boolean connected;
	private String zerno;
	private String staticKey;
	private String staticKeyResponce;

	public MyCripter(long k1, long k2, long k3, long k4, String staticKeyRequest, String staticKeyResponce) {
		connected = false;
		staticKey = staticKeyRequest;
		this.staticKeyResponce = staticKeyResponce;
		randGen = new MyRand3(k1, k2, k3, k4);
		randGen.srand(staticKey.getBytes(StandardCharsets.ISO_8859_1), staticKey.length());
	}

	private String toBase64(byte[] data, int len, byte cs) {
		if(len <= 0) {
			return "";
		}
		int reshta = (len + 1) % 3;
		int blocks = (len + 1) / 3 + (reshta == 0 ? 0 : 1);
		StringBuilder result = new StringBuilder();
		for(int ii = 0; ii < blocks; ii++) {
			if(ii * 3 != len) {
				result.append((char)(0x40 | ((data[ii * 3 + 0] & 0xFC) >> 2)));
			}
			else {
				result.append((char)(0x40 | ((cs & 0xFC) >> 2)));
				result.append((char)(0x40 | ((cs & 0x03) << 4)));
				result.append((char)(0x40));
				result.append((char)(0x40));
				break;
			}
			if(ii * 3 + 1 != len) {
				result.append((char)(0x40 | ((data[ii * 3 + 0] & 0x03) << 4) | ((data[ii * 3 + 1] & 0xF0) >> 4)));
			}
			else {
				result.append((char)(0x40 | ((data[ii * 3 + 0] & 0x03) << 4) | ((cs & 0xF0) >> 4)));
				result.append((char)(0x40 | ((cs & 0x0F) << 2)));
				result.append((char)(0x40));
				break;
			}
			if(ii * 3 + 2 != len) {
				result.append((char)(0x40 | ((data[ii * 3 + 1] & 0x0F) << 2) | ((data[ii * 3 + 2] & 0xC0) >> 6)));
				result.append((char)(0x40 | ((data[ii * 3 + 2] & 0x3F) >> 0)));
			}
			else {
				result.append((char)(0x40 | ((data[ii * 3 + 1] & 0x0F) << 2) | ((cs & 0xC0) >> 6)));
				result.append((char)(0x40 | ((cs & 0x3F) >> 0)));
				break;
			}
		}
		String s = result.toString().replace('\\', ',');
		s += (char)(0x20 | reshta);
		return s;
	}

	private String codBuf(byte[] data, int len) {
		if(len <= 0) {
			return "";
		}
		byte[] data2 = new byte[len + 1];
		byte cs = 0;
		for(int ii = 0; ii < len; ii++) {
			cs ^= data[ii];
			data2[ii] = (byte)(data[ii] ^ randGen.rand());
		}
		randGen.korr(cs);
		data2[len] = cs;
		return toBase64(data2, len, cs);
	}

	private byte[] decodBuf(String request) {
		byte ks = 0;
		int ii = 0, jj = request.length();
		if(jj < 5) {
			return new byte[0];
		}
		byte[] requbuf = new byte[jj];
		for(int kk = 0; kk < jj; kk++) {
			requbuf[kk] = (byte)request.charAt(kk);
			if((requbuf[kk] & 0xFF) == 0x2c)
				requbuf[kk] = 0x5c;
		}
		int reshta = requbuf[jj - 1] & 0x03;
		if((jj - 1) % 4 != 0) {
			return new byte[0];
		}
		int blocks = (jj - 1) / 4;
		if(blocks <= 0) {
			return new byte[0];
		}
		if(reshta == 0) {
			jj = blocks * 3;
		}
		else {
			jj = (blocks - 1) * 3 + reshta;
		}
		byte[] byte_requbuf = new byte[blocks * 3];
		for(ii = 0; ii < blocks; ii++) {
			byte_requbuf[ii * 3 + 0] = (byte)(((requbuf[ii * 4 + 0] & 0x3f) << 2) | ((requbuf[ii * 4 + 1] & 0x30) >> 4));
			byte_requbuf[ii * 3 + 1] = (byte)(((requbuf[ii * 4 + 1] & 0x0f) << 4) | ((requbuf[ii * 4 + 2] & 0x3c) >> 2));
			byte_requbuf[ii * 3 + 2] = (byte)(((requbuf[ii * 4 + 2] & 0x03) << 6) | ((requbuf[ii * 4 + 3] & 0x3f) >> 0));
		}
		jj--;
		ks = 0;
		for(ii = 0; ii < jj; ii++) {
			byte_requbuf[ii] ^= randGen.rand();
			ks ^= byte_requbuf[ii];
		}
		if(ks != byte_requbuf[jj]) {
			return new byte[0];
		}
		randGen.korr(ks);
		byte_requbuf[jj] = 0;
		return byte_requbuf;
	}

	private boolean validationResponse(String request) {
		byte[] byte_requbuf = decodBuf(request);
		if(byte_requbuf.length != 0) {
			int n = 0;
			while(n < byte_requbuf.length && byte_requbuf[n] != 0)
				n++;
			String decoded = new String(byte_requbuf, 0, n, StandardCharsets.ISO_8859_1);
			if(decoded.equals(staticKeyResponce))
				return true;
		}
		return false;
	}

	public String encrypt(byte[] data, int len) {
		if(!connected) {
			randGen.korr((byte)((System.nanoTime() / 1000) & 0xff));
			StringBuilder z = new StringBuilder();
			for(int ii = 0; ii < 64; ii++) {
				char c;
				do {
					c = (char)((randGen.rand() & 0x7f) | 0x40);
				} while(c == (char)0x5c);
				z.append(c);
			}
			zerno = z.toString();
			randGen.srand(staticKey.getBytes(StandardCharsets.ISO_8859_1), staticKey.length());
			String criptMsg = codBuf(zerno.getBytes(StandardCharsets.ISO_8859_1), zerno.length());
			randGen.srand(zerno.getBytes(StandardCharsets.ISO_8859_1), zerno.length());
			return criptMsg;
		}
		return codBuf(data, len);
	}

	public String encrypt(long[] data) {
		byte[] bytes = new byte[data.length * 4];
		for(int i = 0; i < data.length; i++) {
			bytes[i * 4 + 0] = (byte)(data[i] & 0xFF);
			bytes[i * 4 + 1] = (byte)((data[i] >> 8) & 0xFF);
			bytes[i * 4 + 2] = (byte)((data[i] >> 16) & 0xFF);
			bytes[i * 4 + 3] = (byte)((data[i] >> 24) & 0xFF);
		}
		return encrypt(bytes, bytes.length);
	}

	public boolean decrypt(String data) {
		connected = validationResponse(data);
		return connected;
	}

	public boolean getConnected() {
		return connected;
	}

	public void resetConnected() {
		connected = false;
	}
}
