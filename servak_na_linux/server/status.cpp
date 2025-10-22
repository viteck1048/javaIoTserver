
long getLocalTimeOffset() {
	time_t localTime;
	time(&localTime);
	struct tm* gmTimeStruct = gmtime(&localTime);
	time_t globalTime = mktime(gmTimeStruct);
	long offset = (long)difftime(localTime, globalTime);
	return offset / 3600;
}

#define INFO_MSG 0
#define ERROR_MSG 1

class Status {
	
	bool type[100];
	std::string messages[100];
	int point;
	public:
		Status() {
			for(point = 0; point < 100; point++)
				type[point] = 0;
			point = 0;
		}
		
		void msg(const char* msg, const bool type) {
			this->type[point] = type,
			messages[point] = msg;
			point++;
		}
		
		void info(const char* msg) {
			this->msg(msg, INFO_MSG);
		}
		
		void error(const char* msg) {
			this->msg(msg, ERROR_MSG);
		}
		
		bool success() {
			for(int i = 0; i < point; i++) {
				if(type[i]) {
					return false;
				}
			}
			return true;
		}
		
		std::string html() {
			std::stringstream out;
			my_time_cls mt;
			mt.update(getLocalTimeOffset());
			for(int i = 0; i < point; i++) {
				out << "<p class='icon " << (type[i] == INFO_MSG ? "ok_icon" : "error_icon") << "'><lable class='" << (type[i] == INFO_MSG ? "msg_ok" : "msg_err") << "'>" << messages[i] << "</lable><lable style='color: #AAAAAA;'>";
				out << mt.hor(2) << ':' << mt.min(2) << ':' << mt.sec(2);
				out << "</lable></p>";
			}
			point = 0;
			return out.str();
		}
};

void debug()
{
	return;
}

#undef INFO_MSG
#undef ERROR_MSG