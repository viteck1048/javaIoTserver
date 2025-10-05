#define ZAPYTIV_BEZ_ZATRYMKY 50


class MyRand {
	uint32_t a;
	
	public:
		MyRand() {
			a = 0xaabbccdd;
		}
		uint32_t rand() {
			uint32_t b = a;
			b ^= b << 13;
			b ^= b >> 17;
			b ^= b << 5;
			return a = b;
		}
		void srand(uint32_t i) {
			a = i ^ 0xaabbccdd;
		}
		void correct(uint32_t i) {
			a = i ^ a;
		}
};


struct Rele {
	char rr_status[9];
	char rr_command[9];
	int acp_status;
	int sn;
	int time_onl;
	bool put;
	Rele* next;
	int online;
	MyRand my_rand;
	Rele() {
		sn = 0;
		next = NULL;
		online = 0;
		put = 0;
	}
};


Rele *rele = NULL;


std::string obrobnyk_relay(int sn, const char* sn_txt, const char* relays, int acp, const char* acp_txt, bool init, int cs, const char* init_txt)
{
	std::stringstream buff;
	static bool hide_log = 1;

	bool f_create_new_struct = 0;
	int contr_sum = 0, contr_sum2 = 0;
	char bbb[200];
	int bbbi = 0;
	bbbi = sprintf(bbb, "sn = %d\tresv = %s\t\tacp = %3d%%\tsend = ", sn, relays, (int)((((double)acp) / 4095) * 100));
	//sprintf
	Rele **rrr = &rele;
	Rele **rr = &rele;
	int t_time = (int)time(NULL);
	int br_rr_all = 0, br_rr_onl = 0;
	while(*rrr != NULL) {
		if((*rr)->sn != sn)
			rr = &((*rr)->next);
		br_rr_all++;
		if((t_time - (*rrr)->time_onl) <= 8)
			br_rr_onl++;
		rrr = &((*rrr)->next);
	}
	if(*rr == NULL) {
		*rr = new Rele;
		(*rr)->sn = sn;
		(*rr)->acp_status = acp;
		strcpy((*rr)->rr_status, relays);
		strcpy((*rr)->rr_command, "XXXXXXXX");
		(*rr)->online = 0;
		f_create_new_struct = 1;
		(*rr)->time_onl = t_time;
		(*rr)->my_rand.srand(sn);
		(*rr)->put = 0;
	}
	else {
		if((*rr)->put == 0 && strcmp(relays, "XXXXXXXX"))
			strcpy((*rr)->rr_status, relays);
		(*rr)->acp_status = acp;
		if(!init)
			(*rr)->time_onl = t_time;
		if(init) {
			(*rr)->my_rand.srand(sn);
		}
		else if(!strcmp(relays, "XXXXXXXX")) {
			strcpy((*rr)->rr_command, (*rr)->rr_status);
			(*rr)->put = 1;
		}
	}
	bbbi += sprintf(bbb + bbbi, "%s", (*rr)->rr_command);
	bbbi += sprintf(bbb + bbbi, "\t\tinit = %s\t\tcs = %d", init_txt, cs);
	
	int ii = strlen(sn_txt);
	for(int iii = 0; iii < ii; iii++)
		contr_sum += (int)((unsigned char)sn_txt[iii] ^ (unsigned char)((*rr)->my_rand.rand() & (int)0xff));
	ii = strlen(relays);
	for(int iii = 0; iii < ii; iii++)
		contr_sum += (int)((unsigned char)relays[iii] ^ (unsigned char)((*rr)->my_rand.rand() & (int)0xff));
	ii = strlen(acp_txt);
	for(int iii = 0; iii < ii; iii++)
		contr_sum += (int)((unsigned char)acp_txt[iii] ^ (unsigned char)((*rr)->my_rand.rand() & (int)0xff));
	ii = strlen(init_txt);
	for(int iii = 0; iii < ii; iii++)
		contr_sum += (int)((unsigned char)init_txt[iii] ^ (unsigned char)((*rr)->my_rand.rand() & (int)0xff));
	
	if(cs != contr_sum) {
		init = 1;
		bbbi += sprintf(bbb + bbbi, "\t\t\t\t\t\t\tcs = %d\tcontr = %d", cs, contr_sum);
		buff << "{\"relays\":\"XXXXXXXX\",\"online\":\"0\",\"cs\":\"0\"}";
		puts(bbb);
		return buff.str();
	}
	(*rr)->my_rand.correct(contr_sum);
	char buff_cs[50];
	buff << "{\"relays\":\"";
	if(init || !((*rr)->put)) {
		strcpy((*rr)->rr_command, "XXXXXXXX");
		buff << "XXXXXXXX";
	}
	else {
		buff << (*rr)->rr_command;
	}
	sprintf(buff_cs, "%s%d", ((init || !((*rr)->put)) ? "XXXXXXXX" : (*rr)->rr_command), (*rr)->online);
	ii = strlen(buff_cs);
	bbbi += sprintf(bbb + bbbi, "  %s  ", buff_cs);
	for(int iii = 0; iii < ii; iii++) {
		contr_sum2 += (int)((unsigned char)buff_cs[iii] ^ (unsigned char)((*rr)->my_rand.rand() & (int)0xff));
	}
	
	buff << "\",\"online\":\"" << (*rr)->online << "\",\"cs\":\"" << contr_sum2 << "\"}";
	bbbi += sprintf(bbb + bbbi, "\t\tcs2 = %d", contr_sum2);
	(*rr)->my_rand.correct(contr_sum2);
	if(!init) {
		(*rr)->put = 0;
		strcpy((*rr)->rr_command, "XXXXXXXX");
	}
	(*rr)->online = 0;
	if(f_create_new_struct)
		bbbi += sprintf(bbb + bbbi, "\t\tcreate new struct");
	
	if(init || !hide_log)
		puts(bbb);
	else
		printf("%d relays all,\t%d relays online\t", br_rr_all, br_rr_onl);
	fflush(stdout);
	
	if(kbhit()) {
		if(_getch() == 'r') {
			hide_log = !hide_log;
			printf("\n---=== Print log - %s ===---\n", hide_log == 0 ? "ON" : "OFF");
		}
		int c;
		while ((c = (int)_getch()) != '\n' && c != EOF);
	}
	
	return buff.str();
}


std::string rr_get_status(int sn)
{
	printf("rr_get_status json sn = %d\n", sn);
	std::stringstream buff;
	int tmm = (int)time(NULL);
	if(sn == 0) {
		Rele **rr = &rele, **rr_tmp = &rele, *rr_tmp2;
		bool fl = 1;
		buff << "{\"rr\":[";
		while(*rr != NULL) {
			if(tmm - (*rr)->time_onl > 8) {
				(*rr)->acp_status = -4095;
				if(tmm - (*rr)->time_onl > 172800) {
					rr_tmp2 = (*rr)->next;
					
					if(fl)
						rele = rr_tmp2;
					else
						(*rr_tmp)->next = rr_tmp2;
					
					printf("\t\tDELETE sn = %d\n", (*rr)->sn);
					delete *rr;
					
					rr = &rr_tmp2;
					continue;
				}
			}
			if(fl)
				fl = 0;
			else
				buff << ",";
			buff << "\"" << (*rr)->sn << "\"";
			rr_tmp = rr;
			rr = &((*rr)->next);
		}
		buff << "]}";
	}
	else {
		Rele **rr = &rele;
		while(*rr != NULL) {
			if(tmm - (*rr)->time_onl > 60)
				(*rr)->acp_status = -4095;
			if((*rr)->sn == sn)
				break;
			rr = &((*rr)->next);
		}
		if(*rr == NULL) {
			buff << "{\"buttons\":\"XX0110XX\",\"output\":\"-1%\"}";
		}
		else {
			if((*rr)->acp_status != -4095)
				buff << "{\"buttons\":\"" << (*rr)->rr_status << "\",\"output\":\"" << (int)((((double)((*rr)->acp_status)) / 4095) * 100) << "%\"}";
			else
				buff << "{\"buttons\":\"XXXXXXXX\",\"output\":\"offline\"}";
			(*rr)->online = ZAPYTIV_BEZ_ZATRYMKY;
		}
	}	
	return buff.str();
}


std::string rr_put_status(int sn, int button) {
	printf("rr_put_status json sn = %d\t\tbutton = %d\n", sn, button);
	std::stringstream buff;
	button--;
	int tmm = (int)time(NULL);
	Rele **rr = &rele;
	while(*rr != NULL) {
		if(tmm - (*rr)->time_onl > 60)
			(*rr)->acp_status = -4095;
		if((*rr)->sn == sn)
			break;
		rr = &((*rr)->next);
	}
	if(*rr == NULL) {
		buff << "{\"buttons\":\"XX0110XX\",\"output\":\"-1%\"}";
	}
	else {
		if((*rr)->acp_status != -4095) {
			if((*rr)->rr_status[button] == 48) {
				(*rr)->rr_status[button] = 'X';
				(*rr)->rr_command[button] = 49;
			}
			else {
				(*rr)->rr_status[button] = 'X';
				(*rr)->rr_command[button] = 48;
			}
			(*rr)->put = 1;
			buff << "{\"buttons\":\"" << (*rr)->rr_status << "\",\"output\":\"" << (int)((((double)((*rr)->acp_status)) / 4095) * 100) << "%\"}";
		}
		else
			buff << "{\"buttons\":\"XXXXXXXX\",\"output\":\"offline\"}";
		(*rr)->online = ZAPYTIV_BEZ_ZATRYMKY;
	}
	
	return buff.str();
}


/*

------------------------------------------------------------------------------------------------------------------------------------------------
POST /lialiapam HTTP/1.1
Host: 11.22.33.44
User-Agent: ESP32
Content-Length: 68
Accept-Language: uk-ua
X-Forwarded-For: 44.33.22.11
X-Forwarded-Host: 11.22.33.44
X-Forwarded-Proto: http
Accept-Encoding: gzip

sn=0987654321&relays=00000000&acp=0000000000&init=true&cs=0000003904
------------------------------------------------------------------------
param = 5
sn = 0987654321
relays = 00000000
acp = 0000000000
init = true
cs = 0000003904
------------------------------------------------------------------------------------------------------------------------------------------------



HTTP/1.1 200 OK
Cache-Control: no-cache
Content-Length: 47
Content-Type: text/html; charset=UTF-8
Date: Sun, 05 May 2024 16:17:20 GMT
Server: My_Kursach_rskk
Connection: close

{"relays":"XXXXXXXX","online":"40","cs":"1527"}


*/
