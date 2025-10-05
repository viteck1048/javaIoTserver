
#include <Windows.h>
#include <stdio.h>

#pragma warning(disable : 4996)

wchar_t **mmm;
char *adrr;
DWORD br_param; 
DWORD brbr_mem;
int format;

HANDLE ftf;
//int typ;
DWORD noBB;
wchar_t *wbufff;

wchar_t nxt10();
wchar_t nxt20();
wchar_t nxt30();
wchar_t nxtWCH0();
int readstring0();

void writeF1(unsigned char* buf, wchar_t* wbuf_t, const wchar_t* wbuf_m);
void writeF2(unsigned char* buf, wchar_t* wbuf_t, const wchar_t* wbuf_m);
void writeF3(unsigned char* buf, wchar_t* wbuf_t, const wchar_t* wbuf_m);

int lenght_setup(const char* adr)
{
	char dd;
	int i = 0;
	ftf = CreateFileA(adr, GENERIC_READ, 0, NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
	if(ftf == INVALID_HANDLE_VALUE) {
		return 0;
	}//MessageBoxW(NULL, L"lenght", L"", MB_OK);
	while(true) {
		ReadFile(ftf, &dd, 1, &noBB, NULL);
		if(noBB != 1)
			break;
		if(dd == 10)
			i++;
	}
	CloseHandle(ftf);
	return i;
}


int open_setup(const char* adr, DWORD brbr)
{
	if(brbr == 0)
		brbr = lenght_setup(adr);
	brbr_mem = brbr;
	mmm = new wchar_t*[brbr];
	for(DWORD i = 0; i < brbr; i++)
		mmm[i] = NULL;
	adrr = new char[99];
	wbufff = new wchar_t[999];
	strcpy(adrr, adr);
	ftf = CreateFileA(adrr, GENERIC_READ, 0, NULL, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
	if(ftf == INVALID_HANDLE_VALUE) {
		format = 1;
		br_param = 0;
		delete wbufff;
		return 0;
	}
	format = 0;
	//MessageBoxW(NULL, L"qwer", L"", MB_OK);
	for(br_param = 0; br_param < brbr && readstring0(); br_param++);
	
	if(!format) {
		CloseHandle(ftf);
		delete adrr;
		adrr = NULL;
		delete wbufff;
		return 0;
	}
	CloseHandle(ftf);
	delete wbufff;
	return br_param;
}


int close_setup()
{
	for(DWORD i = 0; i < br_param; i++)
		delete mmm[i];
	delete mmm;
	if(adrr != NULL)
		delete adrr;
	return 0;
}


int save_setup()
{
	if(adrr == NULL)
		return 0;
	ftf = CreateFileA(adrr, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
	
	unsigned char *buf;
	buf = new unsigned char[1999];
	wchar_t *wbuf_t = new wchar_t[999];
	
	switch(format) {
		case 1:{
			buf[0] = 0xef;
			buf[1] = 0xbb;
			buf[2] = 0xbf;
			WriteFile(ftf, buf, 3, &noBB, NULL);
			for(DWORD i = 0; i < br_param; i++)
				writeF1(buf, wbuf_t, mmm[i]);
			break;
		}
		case 2:{
			buf[0] = 0xfe;
			buf[1] = 0xff;
			WriteFile(ftf, buf, 2, &noBB, NULL);
			for(DWORD i = 0; i < br_param; i++)
				writeF2(buf, wbuf_t, mmm[i]);
			break;
		}
		case 3:{
			buf[0] = 0xff;
			buf[1] = 0xfe;
			WriteFile(ftf, buf, 2, &noBB, NULL);
			for(DWORD i = 0; i < br_param; i++)
				writeF3(buf, wbuf_t, mmm[i]);
			break;
		}
	}
	delete wbuf_t;
	delete buf;
	CloseHandle(ftf);
	return 1;
}


void writeF1(unsigned char* buf, wchar_t* wbuf_t, const wchar_t* wbuf_m)
{
	swprintf(wbuf_t, 999, L"%s%c%c", wbuf_m, 13, 10);
	int sz = wcslen(wbuf_t);
	int j = 0;
	for(int i = 0; i < sz; i++) {
		if(wbuf_t[i] < (wchar_t)0x80) {
			buf[j] = (unsigned char)wbuf_t[i];
			j++;
		}else
		if(wbuf_t[i] < (wchar_t)0x800) {
			buf[j] = (unsigned char)0xc0;
			buf[j + 1] = (unsigned char)0x80;
			buf[j + 1] += (unsigned char)(wbuf_t[i] % 0x40);
			wbuf_t[i] >>= 6;
			buf[j] += (unsigned char)(wbuf_t[i] % 0x20);
			j += 2;
		}else
		if(wbuf_t[i] < (wchar_t)0x10000) {
			buf[j] = (unsigned char)0xe0;
			buf[j + 1] = (unsigned char)0x80;
			buf[j + 2] = (unsigned char)0x80;
			buf[j + 2] += (unsigned char)(wbuf_t[i] % 0x40);
			wbuf_t[i] >>= 6;
			buf[j + 1] += (unsigned char)(wbuf_t[i] % 0x40);
			wbuf_t[i] >>= 6;
			buf[j] += (unsigned char)(wbuf_t[i] % 0x10);
			j += 3;
		}
	}
	WriteFile(ftf, buf, j, &noBB, NULL);
	return;
}


void writeF2(unsigned char* buf, wchar_t* wbuf_t, const wchar_t* wbuf_m)
{
	swprintf(wbuf_t, 999, L"%s%c%c", wbuf_m, 13, 10);
	int sz = wcslen(wbuf_t);
	for(int i = 0; i < sz; i++) {
		buf[i + i] = (unsigned char)wbuf_t[i];
		wbuf_t[i] >>= 8;
		buf[i + i + 1] = (unsigned char)wbuf_t[i];
	}
	WriteFile(ftf, buf, sz * 2, &noBB, NULL);
	return;
}


void writeF3(unsigned char* buf, wchar_t* wbuf_t, const wchar_t* wbuf_m)
{
	swprintf(wbuf_t, 999, L"%s%c%c", wbuf_m, 13, 10);
	int sz = wcslen(wbuf_t);
	WriteFile(ftf, wbuf_t, sz * 2, &noBB, NULL);
	return;
}


int set_setup(const wchar_t* id, const wchar_t* param)
{
	wchar_t *bbb;
	int n;//, i;
	DWORD i;
	int sz;
	for(i = 0; i < br_param; i++) {
		sz = wcslen(mmm[i]) + 1;
		bbb = new wchar_t[sz];
		wcscpy_s(bbb, sz, mmm[i]);
		for(n = 0; n < sz; n++)
			if(bbb[n] == 32)
				break;
		if(n == sz)
			return -1;
		bbb[n] = 0;
		if(!wcscmp(bbb, id))
			break;
		delete bbb;
	}
	int rt = 1;
	if(i == br_param) {
		if(i == brbr_mem) {
			brbr_mem++;
			wchar_t **tmp_mmm = new wchar_t*[brbr_mem];
			for(DWORD j = 0; j < i; j++) {
				tmp_mmm[j] = mmm[j];
			}
			tmp_mmm[i] = NULL;
			delete mmm;
			mmm = tmp_mmm;
			rt++;
		}
		br_param++;
		rt++;
	}
	if(mmm[i] != NULL)
		delete mmm[i];
	sz = wcslen(id) + wcslen(param) + 2;
	mmm[i] = new wchar_t[sz];
	swprintf(mmm[i], sz, L"%s %s", id, param);
	return rt;
}


int get_setup(const wchar_t* id, wchar_t* param, int sz_param)
{
	wchar_t *bbb;
	int n;//, i;
	int sz;
	DWORD i;
	for(i = 0; i < br_param; i++) {
		sz = wcslen(mmm[i]) + 1;
		bbb = new wchar_t[sz];
		wcscpy_s(bbb, sz, mmm[i]);
		for(n = 0; n < sz; n++)
			if(bbb[n] == 32)
				break;
		if(n == sz)
			return -1;
		bbb[n] = 0;
		if(!wcscmp(bbb, id))
			break;
		delete bbb;
	}//MessageBoxW(NULL, bbb + n + 1, bbb, MB_OK);
	if(i == br_param)
		return 0;
	sz = wcslen(bbb + n + 1) + 1;//MessageBoxW(NULL, bbb + n + 1, bbb, MB_OK);
	if(sz_param == 0 || param == NULL)
		return sz;
	if(sz > sz_param)
		return -2;
	wcscpy_s(param, sz, bbb + n + 1);//MessageBoxW(NULL, param, bbb, MB_OK);
	delete bbb;
	return sz;
}


int readstring0()
{
	int i = 0;
	wchar_t wbuf0;

	while(true) {
		wbuf0 = nxtWCH0();
		if(wbuf0 == 0) {
			if(i) {
				int sz = wcslen(wbufff) + 1;
				mmm[br_param] = new wchar_t[sz];
				wcscpy_s(mmm[br_param], sz + 1, wbufff);
				br_param++;
			}
			return 0;
		}
		if(wbuf0 == 10) {
			wbufff[i] = 0;
			break;
		}
		if(wbuf0 == 13)
			continue;
		wbufff[i] = wbuf0;
		i++;
	}
	int sz = wcslen(wbufff) + 1;
	mmm[br_param] = new wchar_t[sz];
	wcscpy_s(mmm[br_param], sz + 1, wbufff);//MessageBoxW(NULL, mmm[br_param], wbufff, MB_OK);
	return 1;
}


wchar_t nxtWCH0() {
	char buff[4];
	if(!format) {
		ReadFile(ftf, buff, 2, &noBB, NULL);
		if(noBB != 2)
			return 0;
		switch(buff[0]) {
			case (char)0xef:{
				if(buff[1] == (char)0xbb) {
					ReadFile(ftf, buff, 1, &noBB, NULL);
					if(noBB != 1)
						return 0;
					if(buff[0] == (char)0xbf)
						format = 1;
					else
						return 0;
				}else
					return 0;
				break;
			}
			case (char)0xfe:{
				if(buff[1] == (char)0xff)
					format = 2;
				else
					return 0;
				break;
			}
			case (char)0xff:{
				if(buff[1] == (char)0xfe)
					format = 3;
				else
					return 0;
				break;
			}
			default:
				return 0;
		}
	}
	switch(format) {
		case 1:
			return nxt10();
		case 2:
			return nxt20();
		case 3:
			return nxt30();
	}
	return 0;	
}


wchar_t nxt30()
{
	wchar_t bb, gg;//,
	char dd;
	ReadFile(ftf, &dd, 1, &noBB, NULL);
	if(noBB != 1)
		return 0;
	bb = dd;
	ReadFile(ftf, &dd, 1, &noBB, NULL);
	if(noBB != 1)
		return 0;
	gg = dd;
	bb += gg << 8;
	return bb;
}


wchar_t nxt20()
{
	wchar_t bb;//, dd;
	char dd;
	ReadFile(ftf, &dd, 1, &noBB, NULL);
	if(noBB != 1)
		return 0;
	bb = dd;
	bb <<= 8;
	ReadFile(ftf, &dd, 1, &noBB, NULL);
	if(noBB != 1)
		return 0;
	bb += dd;
	return bb;
}


wchar_t nxt10()
{
	wchar_t bb;
	unsigned char buf0;
	unsigned char buf1[3];
	ReadFile(ftf, &buf0, 1, &noBB, NULL);
	if(noBB != 1)
		return 0;
	if(buf0 < (unsigned char)0x80)
		return bb = (wchar_t)buf0;
	else if(buf0 < (unsigned char)0xc0)
		return 0;
	else if(buf0 < (unsigned char)0xe0) {
		ReadFile(ftf, buf1, 1, &noBB, NULL);
		if(noBB != 1)
			return 0;
		buf1[0] &= (unsigned char)0x3f;
		buf0 &= (unsigned char)0x1f;
		bb = (wchar_t)buf0;
		bb <<= 6;
		bb += (wchar_t)buf1[0];
		return bb;
	}
	else if(buf0 < (unsigned char)0xf0) {
		ReadFile(ftf, buf1, 2, &noBB, NULL);
		if(noBB != 2)
			return 0;
		buf0 &= (unsigned char)0x0f;
		buf1[0] &= (unsigned char)0x3f;
		buf1[1] &= (unsigned char)0x3f;
		bb = (wchar_t)buf0;
		bb <<= 6;
		bb += (wchar_t)buf1[0];
		bb <<= 6;
		bb += (wchar_t)buf1[1];
		return bb;
	}
	else {
		ReadFile(ftf, buf1, 3, &noBB, NULL);
		if(noBB != 3)
			return 0;
		return bb = (wchar_t)32;
	}
	return 0;
}