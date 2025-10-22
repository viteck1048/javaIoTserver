#include <time.h>
#include <stdio.h>
#include "my_time.h"

#pragma warning(disable : 4996)

char* my_time_cls::sec(int bb) {
	sprintf(buff_s, "%.2d", s);
	return buff_s;
}


char* my_time_cls::min(int bb) {
	sprintf(buff_mi, "%.2d", mi);
	return buff_mi;
}


char* my_time_cls::hor(int bb) {
	if(bb == 2)
		sprintf(buff_h, "%.2d", h);
	else
		sprintf(buff_h, "%d", (h % 12) == 0 ? 12 : h % 12);
	return buff_h;
}


char* my_time_cls::pmam(int bb) {
	sprintf(buff_pmam, "%s", ((h < 12) ? ((bb == 2) ? "AM" : "a.m.") : ((bb == 2) ? "PM" : "p.m.")));
	return buff_pmam;
}


char* my_time_cls::day(int bb) {
	if(bb == 2)
		sprintf(buff_d, "%.2d", d);
	else 
		sprintf(buff_d, "%d", d);
	return buff_d;
}


char* my_time_cls::mon(int bb) {
	switch(mo) {
		case 1:{
			sprintf(buff_mo, "%.*s", bb, "January");
			break;
		}
		case 2:{
			sprintf(buff_mo, "%.*s", bb, "February");
			break;
		}
		case 3:{
			sprintf(buff_mo, "%.*s", bb, "March");
			break;
		}
		case 4:{
			sprintf(buff_mo, "%.*s", bb, "April");
			break;
		}
		case 5:{
			sprintf(buff_mo, "%.*s", bb, "May");
			break;
		}
		case 6:{
			sprintf(buff_mo, "%.*s", bb, "June");
			break;
		}
		case 7:{
			sprintf(buff_mo, "%.*s", bb, "July");
			break;
		}
		case 8:{
			sprintf(buff_mo, "%.*s", bb, "August");
			break;
		}
		case 9:{
			sprintf(buff_mo, "%.*s", bb, "September");
			break;
		}
		case 10:{
			sprintf(buff_mo, "%.*s", bb, "October");
			break;
		}
		case 11:{
			sprintf(buff_mo, "%.*s", bb, "November");
			break;
		}
		case 12:{
			sprintf(buff_mo, "%.*s", bb, "December");
			break;
		}
	}
	return buff_mo;
}


char* my_time_cls::ear(int bb) {
	sprintf(buff_e, "%d", bb == 2 ? (e % 100) : e);
	return buff_e;
}


char* my_time_cls::daytoweek(int bb) {
	switch(dw) {
		case 1:{
			sprintf(buff_dw, "%.*s", bb, "Monday");
			break;
		}
		case 2:{
			sprintf(buff_dw, "%.*s", bb, "Tuesday");
			break;
		}
		case 3:{
			sprintf(buff_dw, "%.*s", bb, "Wednesday");
			break;
		}
		case 4:{
			sprintf(buff_dw, "%.*s", bb, "Thursday");
			break;
		}
		case 5:{
			sprintf(buff_dw, "%.*s", bb, "Friday");
			break;
		}
		case 6:{
			sprintf(buff_dw, "%.*s", bb, "Saturday");
			break;
		}
		case 7:{
			sprintf(buff_dw, "%.*s", bb, "Sunday");
			break;
		}
	}
	return buff_dw;
}


short int my_time_cls::sec_int() {
	return s;
}


short int my_time_cls::min_int() {
	return mi;
}


short int my_time_cls::hor_int() {
	return h;
}


short int my_time_cls::day_int() {
	return d;
}


short int my_time_cls::mon_int() {
	return mo;
}


short int my_time_cls::ear_int() {
	return e;
}


short int my_time_cls::daytoweek_int() {
	return dw;
}


my_time_cls::my_time_cls() {
	update(0);
}


my_time_cls::my_time_cls(int ch_p) {
	update(ch_p);
}


void my_time_cls::update(int ch_p) {
	utc = (unsigned int)time(NULL) + ch_p * 3600;
	s = utc % 60;
	utc /= 60;
	mi = utc % 60;
	utc /= 60;
	h = utc % 24;
	utc /= 24;
	dw = 4;
	d = 1;
	mo = 1;
	e = 1970;
	for(; utc; utc--) {
		d++;
		dw++;
		if(dw == 8)
			dw = 1;
		if(d == mmm() + 1) {
			d = 1;
			mo++;
			if(mo == 13) {
				e++;
				mo = 1;
			}
		}
	}
}


int my_time_cls::mmm() {
	switch(mo) {
		case 1:
		case 3:
		case 5:
		case 7:
		case 8:
		case 10:
		case 12: return 31;
		case 4:
		case 6:
		case 9:
		case 11: return 30;
		case 2: {
			if(eee())
				return 29;
			else
				return 28;
		}
		default: printf("\nalarm\n");
	}
	return -1;
}


bool my_time_cls::eee() {
	if(!(e % 400))
		return true;
	if(!(e % 100))
		return false;
	if(!(e % 4))
		return true;
	return false;
}



