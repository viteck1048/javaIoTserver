//import java.nio.ByteBuffer;


public class MyRand {
	private long q, w, e, r, t;
	private byte y;
	
	private void nullRand() {
		q = Configs.getLong("private_key_1") & 0xFFFFFFFFL;
		w = Configs.getLong("private_key_2") & 0xFFFFFFFFL;
		e = Configs.getLong("private_key_3") & 0xFFFFFFFFL;
		r = Configs.getLong("private_key_4") & 0xFFFFFFFFL;
	}
	
	public MyRand() {
		nullRand();
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

	public void srand(byte[] zerno) {
		if(zerno.length != 40) {
			throw new IllegalArgumentException("Seed array must be exactly 40 bytes long");
		}
		else
			nullRand();
		for (int ii = 0; ii < 10; ii += 2) {
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
		return (((long)bytes[offset] & 0xFFL)) |
			   (((long)bytes[offset + 1] & 0xFFL) << 8) |
			   (((long)bytes[offset + 2] & 0xFFL) << 16) |
			   (((long)bytes[offset + 3] & 0xFFL) << 24) & 0xFFFFFFFFL;
	}

	private long leftShift(long value, int shift) {
		return (value << shift) & 0xFFFFFFFFL;
	}

	private long rightShift(long value, int shift) {
		return (value & 0xFFFFFFFFL) >>> shift;
	}
	
	
}

/*
public class MyRand {
    long q, w, e, r, t;
    byte y;

    void nullRand() {
        q = 11111;
        w = 22222;
        e = 33333;
        r = 44444;
    }

    public MyRand() {
        nullRand();
    }

    public byte rand() {
        t = q ^ (q << 11);
        q = w;
        w = e;
        e = r;
        r = (r ^ (r >> 19)) ^ (t ^ (t >> 8));
        y = (byte) (r ^ (r >> 8) ^ (r >> 16) ^ (r >> 24));
        return y;
    }

    public void srand(byte[] zerno) {
        nullRand();
        for (int ii = 0; ii < 10; ii += 2) {
            long zi1 = ((long) (zerno[ii * 4] & 0xFF)) |
                    ((long) (zerno[ii * 4 + 1] & 0xFF) << 8) |
                    ((long) (zerno[ii * 4 + 2] & 0xFF) << 16) |
                    ((long) (zerno[ii * 4 + 3] & 0xFF) << 24);
            long zi2 = ((long) (zerno[ii * 4 + 4] & 0xFF)) |
                    ((long) (zerno[ii * 4 + 5] & 0xFF) << 8) |
                    ((long) (zerno[ii * 4 + 6] & 0xFF) << 16) |
                    ((long) (zerno[ii * 4 + 7] & 0xFF) << 24);
            q ^= zi1;
            e ^= zi2;
        }
        for (int i = 0; i < 4; i++) rand();
    }
	
	public void korr(byte ks) {
		q ^= ((int)ks & 0xFF);
		rand();
		rand();
		rand();
		rand();
	}

	private int bytesToInt(byte[] bytes, int offset) {
		return (((int)bytes[offset] & 0xFF)) |
			   (((int)bytes[offset + 1] & 0xFF) << 8) |
			   (((int)bytes[offset + 2] & 0xFF) << 16) |
			   (((int)bytes[offset + 3] & 0xFF) << 24);
	}
}
*/