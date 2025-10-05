#include "my_time.h"
#include <ctime>
#include <string>

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
