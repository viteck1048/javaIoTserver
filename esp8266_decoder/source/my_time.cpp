#include "my_time.h"
#include <ctime>
#include <string>
#include "date/tz.h"


std::string my_time_str() {
    char buf[128];
    std::time_t t = std::time(nullptr);
    std::tm tm;
#ifdef _WIN32
    localtime_s(&tm, &t);
#else
    localtime_r(&t, &tm);
#endif
    std::strftime(buf, sizeof(buf), "%a, %d %b %Y %H:%M:%S", &tm);
    return std::string(buf);
}

int offsetTimeZone(std::string tz_str) {
    auto tz = date::locate_zone(tz_str.c_str());
    auto info = tz->get_info(std::chrono::system_clock::now());
    return info.offset.count();
}

std::tm my_time_calendar_day(int day_epoch) {
    std::time_t t = static_cast<std::time_t>(day_epoch * 86400 + 1);
    std::tm tm;
#ifdef _WIN32
    gmtime_s(&tm, &t);
#else
    gmtime_r(&t, &tm);
#endif
    return tm;
}

int my_time_get_month_epoch(int day_epoch) {
    std::tm tm = my_time_calendar_day(day_epoch);
    return tm.tm_year * 12 + tm.tm_mon;
}

std::string my_time_str_calendar_day(int day_epoch) {
    char buf[128];
    std::tm tm = my_time_calendar_day(day_epoch);

    std::strftime(buf, sizeof(buf), "%a, %d %b %Y", &tm);
    return std::string(buf);
}

std::string my_time_str_offset(int tzOffset) {
    char buf[128];
    std::time_t t = std::time(nullptr) + tzOffset;
    std::tm tm;
#ifdef _WIN32
    gmtime_s(&tm, &t);
#else
    gmtime_r(&t, &tm);
#endif
    std::strftime(buf, sizeof(buf), "%a, %d %b %Y %H:%M:%S", &tm);
    return std::string(buf);
}

simpl_tm secondsToHMS(int seconds) {
    simpl_tm tm;
    tm.tm_hour = seconds / 3600;
    tm.tm_min = (seconds % 3600) / 60;
    tm.tm_sec = seconds % 60;
    return tm;
}

std::string my_time_str_HMS(int seconds) {
    simpl_tm tm = secondsToHMS(seconds);
    char buf[128];
    std::snprintf(buf, sizeof(buf), "%02d:%02d:%02d", tm.tm_hour, tm.tm_min, tm.tm_sec);
    return std::string(buf);
}