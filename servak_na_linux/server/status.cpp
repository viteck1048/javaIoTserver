
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

// Екранує значення для HTML-атрибута. Аргумент повідомлення -- це дані користувача
// (буква, умова на кшталт "a>b", назва), тож без цього символи <>&"' поламали б розмітку.
static std::string attr_escape(const std::string& s) {
	std::string out;
	for(char c : s) {
		switch(c) {
			case '&': out += "&amp;"; break;
			case '"': out += "&quot;"; break;
			case '\'': out += "&#39;"; break;
			case '<': out += "&lt;"; break;
			case '>': out += "&gt;"; break;
			default: out += c;
		}
	}
	return out;
}

class Status {

	bool type[100];
	std::string messages[100];
	std::string keys[100];			// i18n-ключ повідомлення; порожній -> без перекладу (лише технічні рядки)
	std::string args[100];			// підстановка {v} у переклад: дані користувача, самі не перекладаються
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
			keys[point] = "";
			args[point] = "";
			point++;
		}

		void msg(const char* msg, const char* key, const char* arg, const bool type) {
			this->type[point] = type,
			messages[point] = msg;
			keys[point] = key ? key : "";
			args[point] = arg ? arg : "";
			point++;
		}

		void info(const char* msg) {
			this->msg(msg, INFO_MSG);
		}

		void error(const char* msg) {
			this->msg(msg, ERROR_MSG);
		}

		void info(const char* msg, const char* key, const char* arg = "") {
			this->msg(msg, key, arg, INFO_MSG);
		}

		void error(const char* msg, const char* key, const char* arg = "") {
			this->msg(msg, key, arg, ERROR_MSG);
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
				// Латинський текст лишається як fallback; клієнт (push_my_console) підмінить
				// його на переклад за data-msg-key, а без JS консоль читабельна як є.
				out << "<p class='icon " << (type[i] == INFO_MSG ? "ok_icon" : "error_icon") << "'><lable class='" << (type[i] == INFO_MSG ? "msg_ok" : "msg_err") << "'";
				if(!keys[i].empty())
					out << " data-msg-key='" << keys[i] << "' data-msg-arg=\"" << attr_escape(args[i]) << "\"";
				out << ">" << messages[i] << "</lable><lable style='color: #AAAAAA;'>";
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