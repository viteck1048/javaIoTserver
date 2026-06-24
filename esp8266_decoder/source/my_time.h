#ifndef MY_TIME_H
#define MY_TIME_H

#pragma once
#include <string>


struct simpl_tm	{
    int tm_sec;   // seconds after the minute - [0, 60] including leap second
    int tm_min;   // minutes after the hour - [0, 59]
    int tm_hour;  // hours since midnight - [0, 23]
};

std::string my_time_str();
int offsetTimeZone(std::string tz_str);
std::string my_time_str_calendar_day(int day_epoch);
std::string my_time_str_offset(int tzOffset);
simpl_tm secondsToHMS(int seconds);
std::string my_time_str_HMS(int seconds);
int my_time_get_month_epoch(int day_epoch);

#endif // MY_TIME_H