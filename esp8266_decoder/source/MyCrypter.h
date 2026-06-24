
#include "g_setup.h"
#include "my_time.h"
#include <cstdint>
#include <cstring>

#define USE_CHACHA20

#ifdef USE_CHACHA20

#define CHACHA_ROL32(x,n) (((x)<<(n))|((x)>>(32-(n))))
#define CHACHA_QR(a,b,c,d) \
    (a)+=(b); (d)^=(a); (d)=CHACHA_ROL32(d,16); \
    (c)+=(d); (b)^=(c); (b)=CHACHA_ROL32(b,12); \
    (a)+=(b); (d)^=(a); (d)=CHACHA_ROL32(d, 8); \
    (c)+=(d); (b)^=(c); (b)=CHACHA_ROL32(b, 7);

class MyRand {
    uint32_t state[16];
    uint8_t  buf[64];
    int      buf_pos;

    void nullRand() {
        state[0]=0x61707865; state[1]=0x3320646e;
        state[2]=0x79622d32; state[3]=0x6b206574;
        uint32_t k[4] = {
            (uint32_t)atoi(g_setup.get("myRandKey1").c_str()),
            (uint32_t)atoi(g_setup.get("myRandKey2").c_str()),
            (uint32_t)atoi(g_setup.get("myRandKey3").c_str()),
            (uint32_t)atoi(g_setup.get("myRandKey4").c_str()),
        };
        for (int i = 0; i < 4; i++) { state[4+i] = k[i]; state[8+i] = k[i]; }
        state[12]=0; state[13]=0; state[14]=0; state[15]=0;
        buf_pos = 64;
    }

    void gen_block() {
        uint32_t x[16];
        for (int i = 0; i < 16; i++) x[i] = state[i];
        for (int i = 0; i < 10; i++) {
            CHACHA_QR(x[0],x[4],x[ 8],x[12]);
            CHACHA_QR(x[1],x[5],x[ 9],x[13]);
            CHACHA_QR(x[2],x[6],x[10],x[14]);
            CHACHA_QR(x[3],x[7],x[11],x[15]);
            CHACHA_QR(x[0],x[5],x[10],x[15]);
            CHACHA_QR(x[1],x[6],x[11],x[12]);
            CHACHA_QR(x[2],x[7],x[ 8],x[13]);
            CHACHA_QR(x[3],x[4],x[ 9],x[14]);
        }
        for (int i = 0; i < 16; i++) {
            uint32_t v = x[i] + state[i];
            buf[i*4+0]=v&0xff; buf[i*4+1]=(v>>8)&0xff;
            buf[i*4+2]=(v>>16)&0xff; buf[i*4+3]=(v>>24)&0xff;
        }
        state[12]++;
        buf_pos = 0;
    }

public:
    MyRand() { nullRand(); }

    byte rand() {
        if (buf_pos >= 64) gen_block();
        return buf[buf_pos++];
    }

    void srand(byte* zerno, int len) {
        nullRand();
        int words = len / 4;
        for (int i = 0; i < words; i++) {
            uint32_t word = 0;
            memcpy(&word, zerno + i*4, 4);
            state[4 + (i % 8)] ^= word;
        }
        state[12] = 0;
        buf_pos = 64;
        rand(); rand(); rand(); rand();
    }

    void korr(byte ks) {
        state[13] ^= (uint32_t)ks;
        buf_pos = 64;
        rand(); rand(); rand(); rand();
    }
};

#undef CHACHA_QR
#undef CHACHA_ROL32

#else // USE_CHACHA20

class MyRand{
	unsigned long q, w, e, r, t;
	byte y;
	void nullRand() {
		q = atoi(g_setup.get("myRandKey1").c_str());
		w = atoi(g_setup.get("myRandKey2").c_str());
		e = atoi(g_setup.get("myRandKey3").c_str());
		r = atoi(g_setup.get("myRandKey4").c_str());
	}
	public:
		MyRand() {
			nullRand();
		}
		byte rand() {
			t = q ^ (q << 11);
			q = w;
			w = e;
			e = r;
			r = (r ^ (r >> 19)) ^ (t ^ (t >> 8));
			y = (byte)(r ^ (r >> 8) ^ (r >> 16) ^ (r >> 24));
			return y;
		}
		void srand(byte* zerno, int len) {
			len /= 4;
			nullRand();
			for(byte ii = 0; ii < len; ii += 2) {
				q ^= ((unsigned long*)zerno)[ii];
				e ^= ((unsigned long*)zerno)[ii + 1];
			}
			rand();
			rand();
			rand();
			rand();
		}
		void korr(byte ks) {
			q ^= (unsigned long)ks;
			rand();
			rand();
			rand();
			rand();
		}
};

#endif // USE_CHACHA20


class MyCripter {
	private:

		MyRand randGen;
		bool connected;
		std::string zerno;
		std::string staticKey = g_setup.get("myStaticKeyRequest");

		inline char HiHex(byte bb) {
			return LoHex(bb >> 4);
		}


		char LoHex(byte bb) {
			bb &= 0x0f;
			if(bb < 10) {
				return (char)(bb + 48);
			}
			else {
				return (char)((byte)'A' + (bb - 10));
			}
		}

		std::string toHex(byte* data, int len, byte cs) {
			std::string result = "";
			for(int i = 0; i < len; i++) {
				result += HiHex(data[i]);
				result += LoHex(data[i]);
			}
			result += HiHex(cs);
			result += LoHex(cs);
			return result;
		}
		
		std::string toBase64(byte* data, int len, byte cs) {
			if (len <= 0) {
				return "";
			}
			byte reshta = (len + 1) % 3;
			short blocks = (len + 1) / 3 + (reshta == 0 ? 0 : 1);
			short length2 = blocks * 4 + 1;
			std::string result = "";
			for(short ii = 0; ii < blocks; ii++) {
				if(ii * 3 != len) {
					result += (char)(0x40 | ((data[ii * 3 + 0] & 0b11111100) >> 2));
				}
				else {
					result += (char)(0x40 | ((cs & 0b11111100) >> 2));
					result += (char)(0x40 | ((cs & 0b00000011) << 4));
					result += (char)(0x40);
					result += (char)(0x40);
					break;
				}
				if(ii * 3 + 1 != len) {
					result += (char)(0x40 | ((data[ii * 3 + 0] & 0b00000011) << 4) | ((data[ii * 3 + 1] & 0b11110000) >> 4));
				}
				else {
					result += (char)(0x40 | ((data[ii * 3 + 0] & 0b00000011) << 4) | ((cs & 0b11110000) >> 4));
					result += (char)(0x40 | ((cs & 0b00001111) << 2));
					result += (char)(0x40);
					break;
				}
				if(ii * 3 + 2 != len) {
					result += (char)(0x40 | ((data[ii * 3 + 1] & 0b00001111) << 2) | ((data[ii * 3 + 2] & 0b11000000) >> 6));
					result += (char)(0x40 | ((data[ii * 3 + 2] & 0b00111111) >> 0));
				}
				else {
					result += (char)(0x40 | ((data[ii * 3 + 1] & 0b00001111) << 2) | ((cs & 0b11000000) >> 6));
					result += (char)(0x40 | ((cs & 0b00111111) >> 0));
					break;
				}
			}
			//result.replace("\\", ",");
			for(int i = 0; i < result.length(); i++) {
				if(result[i] == '\\') {
					result[i] = ',';
				}
			}
			result += (char)(0x20 | reshta);
			return result;
		}

		std::string codBuf(byte* data, int len) {
			if(len <= 0) {
				return "";
			}
			std::vector<uint8_t> data2(len + 1);
			byte cs = 0;
			for(int ii = 0; ii < len; ii++) {
				cs ^= data[ii];
				data2[ii] = data[ii] ^ randGen.rand();
			}
			randGen.korr(cs);
			data2[len] = cs;
		#ifndef HEX_REQUEST
			return toBase64(data2.data(), len, cs);
		#else
			return toHex(data2.data(), len, cs);
		#endif
		}

		std::vector<uint8_t> decodBuf(std::string request) {
			byte ks = 0;
			short ii = 0, jj = request.length();
			if(jj < 5) {
				return {};
			}
			std::vector<uint8_t> requbuf(jj);
			for(short kk = 0; kk < jj; kk++) {
				requbuf[kk] = request.c_str()[kk];
				if(requbuf[kk] == 0x2c)
					requbuf[kk] = 0x5c;
			}
			
			short blocks;
			byte reshta = requbuf[jj - 1] & 0x03;
			if((jj - 1) % 4 != 0) {
				return {};
			}
			blocks = (jj - 1) / 4;
			if (blocks <= 0) {
				return {};
			}
			if(reshta == 0) {
				jj = blocks * 3;
			}
			else {
				jj = (blocks - 1) * 3 + reshta;
			}
			std::vector<uint8_t> byte_requbuf(blocks * 3);
			for(ii = 0; ii < blocks; ii++) {
				byte_requbuf[ii * 3 + 0] = ((requbuf[ii * 4 + 0] & 0x3f) << 2) | ((requbuf[ii * 4 + 1] & 0x30) >> 4);
				byte_requbuf[ii * 3 + 1] = ((requbuf[ii * 4 + 1] & 0x0f) << 4) | ((requbuf[ii * 4 + 2] & 0x3c) >> 2);
				byte_requbuf[ii * 3 + 2] = ((requbuf[ii * 4 + 2] & 0x03) << 6) | ((requbuf[ii * 4 + 3] & 0x3f) >> 0);
			}
			jj--;
		
			ks = 0;
			for(ii = 0; ii < jj; ii++) {
				byte_requbuf[ii] ^= randGen.rand();
				ks ^= byte_requbuf[ii];
			}

			if(ks != byte_requbuf[jj]) {
				return {};
			}

			randGen.korr(ks);

			std::vector<uint8_t> result(jj);
			for(ii = 0; ii < jj; ii++) {
				result[ii] = byte_requbuf[ii];
			}

			return result;
		}

	
	public:
		std::string encrypt(byte* data, int len) {
			return codBuf(data, len);
		}

		inline std::string encrypt(std::string data) {
			return encrypt((byte*)data.c_str(), data.length());
		}

		std::vector<uint8_t> decrypt(std::string data) {
			if(!connected) {
				randGen.srand((byte*)staticKey.c_str(), staticKey.length());
			}
			auto result = decodBuf(data);
			if(result.size() != 0) {
				if(!connected) {
					connected = true;
					randGen.srand((byte*)result.data(), result.size());
					printf("%s\nConnected with key: \n", my_time_str().c_str());
					for (int i = 0; i < result.size(); i++) {
						printf("%02x ", result[i]);
						if((i + 1) % 16 == 0) {
							printf("\n");
						}
					}
					if(result.size() % 16 != 0) {
						printf("\n");
					}
					return result;
				}
				else {
					/*printf("%s\nDecrypted data: \n", my_time_str().c_str());
					for (int i = 0; i < result.size() / 4; i++) {
						int ch = result[i * 4 + 0];
						int tm = result[i * 4 + 3] << 16 | result[i * 4 + 2] << 8 | result[i * 4 + 1];
						int h, m, s;
						h = tm / 3600;
						m = (tm % 3600) / 60;
						s = tm % 60;
						printf("Channel: %d,\t Time: %02d:%02d:%02d\n", ch, h, m, s);
					}*/
					return result;
				}
			}
			else {
				printf("Decryption failed\n");
				connected = false;
				return {};
			}
		}

		MyCripter() {
			connected = false;
		}

		bool getConnected() {
			return connected;
		}

};