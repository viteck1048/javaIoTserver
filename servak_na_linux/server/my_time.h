
#ifdef min
	#undef min
#endif


class my_time_cls {
	private:
		unsigned int utc;
		short int s, mi, h, d, mo, e, dw;
		char buff_s[3], buff_mi[3], buff_h[3], buff_d[3], buff_mo[10], buff_e[5], buff_dw[10], buff_pmam[5]; 
		int mmm();
		bool eee();
	public:
		my_time_cls();
		my_time_cls(int);
		void update(int);
		char* sec(int bb = 2);
		char* min(int bb = 2);
		char* hor(int bb = 1);
		char* pmam(int bb = 4);
		char* day(int bb = 1);
		char* mon(int = 10);
		char* ear(int bb = 4);
		char* daytoweek(int bb = 10);
		short int sec_int();
		short int min_int();
		short int hor_int();
		short int day_int();
		short int mon_int();
		short int ear_int();
		short int daytoweek_int();
};