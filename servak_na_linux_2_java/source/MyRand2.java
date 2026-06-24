// Дослівний порт MyRand зі скетча ESP-01 (MachineTime). Сід — 4 ключі з конструктора.
public class MyRand2 {
	private long q, w, e, r, t;
	private byte y;
	private final long k1, k2, k3, k4;

	public MyRand2(long k1, long k2, long k3, long k4) {
		this.k1 = k1;
		this.k2 = k2;
		this.k3 = k3;
		this.k4 = k4;
		nullRand();
	}

	private void nullRand() {
		q = k1 & 0xFFFFFFFFL;
		w = k2 & 0xFFFFFFFFL;
		e = k3 & 0xFFFFFFFFL;
		r = k4 & 0xFFFFFFFFL;
	}

	public byte rand() {
		t = q ^ leftShift(q, 11);
		q = w;
		w = e;
		e = r;
		r = (r ^ rightShift(r, 19)) ^ (t ^ rightShift(t, 8));
		y = (byte)(r ^ rightShift(r, 8) ^ rightShift(r, 16) ^ rightShift(r, 24));
		return y;
	}

	public void srand(byte[] zerno, int len) {
		len /= 4;
		nullRand();
		for(int ii = 0; ii < len; ii += 2) {
			q ^= bytesToInt(zerno, ii * 4);
			e ^= bytesToInt(zerno, (ii + 1) * 4);
		}
		rand();
		rand();
		rand();
		rand();
	}

	public void korr(byte ks) {
		q ^= ((long)ks & 0xFFL);
		rand();
		rand();
		rand();
		rand();
	}

	private long bytesToInt(byte[] bytes, int offset) {
		return ((long)bytes[offset] & 0xFFL)
			| (((long)bytes[offset + 1] & 0xFFL) << 8)
			| (((long)bytes[offset + 2] & 0xFFL) << 16)
			| (((long)bytes[offset + 3] & 0xFFL) << 24);
	}

	private long leftShift(long value, int shift) {
		return (value << shift) & 0xFFFFFFFFL;
	}

	private long rightShift(long value, int shift) {
		return (value & 0xFFFFFFFFL) >>> shift;
	}
}
