// Порт ChaCha20-варіанта MyRand з esp8266_decoder/source/MyCrypter.h (USE_CHACHA20).
// Drop-in заміна MyRand2: той самий інтерфейс rand/srand/korr, сід — 4 ключі з конструктора.
public class MyRand3 {
	private final int[] state = new int[16];
	private final byte[] buf = new byte[64];
	private int bufPos;
	private final int[] k = new int[4];

	public MyRand3(long k1, long k2, long k3, long k4) {
		k[0] = (int)k1;
		k[1] = (int)k2;
		k[2] = (int)k3;
		k[3] = (int)k4;
		nullRand();
	}

	private void nullRand() {
		state[0] = 0x61707865;
		state[1] = 0x3320646e;
		state[2] = 0x79622d32;
		state[3] = 0x6b206574;
		for(int i = 0; i < 4; i++) {
			state[4 + i] = k[i];
			state[8 + i] = k[i];
		}
		state[12] = 0;
		state[13] = 0;
		state[14] = 0;
		state[15] = 0;
		bufPos = 64;
	}

	private void qr(int[] x, int a, int b, int c, int d) {
		x[a] += x[b]; x[d] ^= x[a]; x[d] = Integer.rotateLeft(x[d], 16);
		x[c] += x[d]; x[b] ^= x[c]; x[b] = Integer.rotateLeft(x[b], 12);
		x[a] += x[b]; x[d] ^= x[a]; x[d] = Integer.rotateLeft(x[d], 8);
		x[c] += x[d]; x[b] ^= x[c]; x[b] = Integer.rotateLeft(x[b], 7);
	}

	private void genBlock() {
		int[] x = new int[16];
		for(int i = 0; i < 16; i++)
			x[i] = state[i];
		for(int i = 0; i < 10; i++) {
			qr(x, 0, 4, 8, 12);
			qr(x, 1, 5, 9, 13);
			qr(x, 2, 6, 10, 14);
			qr(x, 3, 7, 11, 15);
			qr(x, 0, 5, 10, 15);
			qr(x, 1, 6, 11, 12);
			qr(x, 2, 7, 8, 13);
			qr(x, 3, 4, 9, 14);
		}
		for(int i = 0; i < 16; i++) {
			int v = x[i] + state[i];
			buf[i * 4 + 0] = (byte)(v & 0xff);
			buf[i * 4 + 1] = (byte)((v >> 8) & 0xff);
			buf[i * 4 + 2] = (byte)((v >> 16) & 0xff);
			buf[i * 4 + 3] = (byte)((v >> 24) & 0xff);
		}
		state[12]++;
		bufPos = 0;
	}

	public byte rand() {
		if(bufPos >= 64)
			genBlock();
		return buf[bufPos++];
	}

	public void srand(byte[] zerno, int len) {
		nullRand();
		int words = len / 4;
		for(int i = 0; i < words; i++) {
			int word = bytesToInt(zerno, i * 4);
			state[4 + (i % 8)] ^= word;
		}
		state[12] = 0;
		bufPos = 64;
		rand();
		rand();
		rand();
		rand();
	}

	public void korr(byte ks) {
		state[13] ^= (ks & 0xFF);
		bufPos = 64;
		rand();
		rand();
		rand();
		rand();
	}

	private int bytesToInt(byte[] b, int o) {
		return (b[o] & 0xff)
			| ((b[o + 1] & 0xff) << 8)
			| ((b[o + 2] & 0xff) << 16)
			| ((b[o + 3] & 0xff) << 24);
	}
}
