#define JSON_NOEXCEPTION
#include "json.hpp"

using json = nlohmann::json;

#define conv(tt) utf_to_cp1251(tt)
#define ENDL "\r\n"


std::string utf_to_cp1251(const char* txt) {
    if (!txt) return NULL; // Перевіряємо вхідний параметр

    size_t len = strlen(txt);
    char* result = (char*)malloc(len + 1); // Виділяємо пам'ять для результату
    if (!result) return NULL; // Перевіряємо успішність виділення пам'яті

    size_t i = 0, j = 0;
    while (i < len) {
        unsigned char c = txt[i];

        if (c < 0x80) { // ASCII символи
            result[j++] = c;
        } else if ((c & 0xE0) == 0xC0 && i + 1 < len) { // Двобайтовий символ UTF-8
            unsigned char c2 = txt[i + 1];
            unsigned short unicode = ((c & 0x1F) << 6) | (c2 & 0x3F);

            switch (unicode) {
                case 0x0406: result[j++] = 0xB2; break; // І
                case 0x0456: result[j++] = 0xB3; break; // і
                case 0x0407: result[j++] = 0xAF; break; // Ї
                case 0x0457: result[j++] = 0xBF; break; // ї
                case 0x0404: result[j++] = 0xAA; break; // Є
                case 0x0454: result[j++] = 0xBA; break; // є
                case 0x0490: result[j++] = 0xA5; break; // Ґ
                case 0x0491: result[j++] = 0xB4; break; // ґ
                case 0x0402: result[j++] = 0x80; break;
                case 0x0403: result[j++] = 0x81; break;
                case 0x0453: result[j++] = 0x83; break;
                case 0x0409: result[j++] = 0x8A; break;
                case 0x040A: result[j++] = 0x8C; break;
                case 0x040C: result[j++] = 0x8D; break;
                case 0x040B: result[j++] = 0x8E; break;
                case 0x040F: result[j++] = 0x8F; break;
                case 0x0452: result[j++] = 0x90; break;
                case 0x0459: result[j++] = 0x9A; break;
                case 0x045A: result[j++] = 0x9C; break;
                case 0x045C: result[j++] = 0x9D; break;
                case 0x045B: result[j++] = 0x9E; break;
                case 0x045F: result[j++] = 0x9F; break;
                case 0x040E: result[j++] = 0xA1; break;
                case 0x045E: result[j++] = 0xA2; break;
                case 0x0408: result[j++] = 0xA3; break;
                case 0x00B5: result[j++] = 0xB5; break;
                case 0x0458: result[j++] = 0xBC; break;
                case 0x0405: result[j++] = 0xBD; break;
                case 0x0455: result[j++] = 0xBE; break;
                case 0x042B: result[j++] = '?'; break;
                case 0x044B: result[j++] = '?'; break;
                default:
                    if (unicode >= 0x0410 && unicode <= 0x044F) { // Загальна кирилиця
                        result[j++] = unicode - 0x0350;
                    } else {
                        result[j++] = '?'; // Непідтримувані символи
                    }
            }

            i++;
        } else {
            result[j++] = '?'; // Непізнаний байт
        }
        i++;
    }

    result[j] = '\0'; // Завершуємо рядок
	std::string str_result(result);
	free(result);
    return str_result;
}


std::string utf_to_cp1251(std::string txt) {
	return utf_to_cp1251(txt.c_str());
}


std::string dwnldcnf_probnick(int m_id) {
	std::stringstream buff;
	buff << "qwertyqwertyqwerty";
	buff << ENDL << char(0xc1) << char(0xf0) << char(0xee) << char(0xe9) << ENDL;
	buff << "  " << m_id << "  ";
	buff << ENDL;
	buff << utf_to_cp1251("І і Ї ї Є є Ґ ґ ") << ENDL;
	buff << utf_to_cp1251("Цей тестовий згенерований файл") << ENDL;
	buff << utf_to_cp1251("Покликаний перевірити ") << ENDL;
	buff << utf_to_cp1251("Трансляцію тексту з UTF-8 на WinCP1251") << ENDL;
	buff << utf_to_cp1251("І передачу її користувачю") << ENDL;
	buff << utf_to_cp1251("Через HTTPS.") << ENDL;
	buff << utf_to_cp1251("use CP1251") << ENDL;
	buff << ENDL;
	buff << "lkjhgfdsaoiuytrewq";
	return buff.str();
}


std::string dwnldcnf(int m_id) {
	std::stringstream buff;
	std::string tmp = get_json_liry(m_id, NULL);
	if(!tmp.length() || tmp[tmp.length() - 1] != '}') {
		return dwnldcnf_probnick(111);
	}
	json j = json::parse(tmp);
	buff << "//  " << j["name1"].get<std::string>().c_str() << ENDL;
	buff << j["mash"]["M1"].get<std::string>().c_str() << ENDL;
	if(atoi(j["mash"]["M2"].get<std::string>().c_str()) != 0) {
		buff << "M2  " << j["mash"]["M2"].get<std::string>().c_str() << ENDL;
	}
	int i = 0;
	for(const auto& lir : j["liry"]) {
		buff << 'L' << i + 1 << " \"" << conv(lir["NAME"].get<std::string>().c_str()) << "\" " << 'M' << lir["MAGAZ"].get<std::string>().c_str() << ENDL;
		buff << lir["FORM"].get<std::string>().c_str() << ENDL;
		buff << lir["FORM_ZV"].get<std::string>().c_str() << ENDL;
		int l_id = atoi(lir["L_ID"].get<std::string>().c_str());
		for(const auto& zm : j["liry_zm_arr"][i]) {
			if(atoi(zm["NPP_S"].get<std::string>().c_str()) == 0) {
				buff << zm["BUKVA"].get<std::string>().c_str() << '\"' << conv(zm["NAME"].get<std::string>().c_str()) << '\"' << ENDL;
				if(atof(zm["ZNACHENNJA"].get<std::string>().c_str()) != 0) {
					buff << zm["BUKVA"].get<std::string>().c_str() << '=' << zm["ZNACHENNJA"].get<std::string>().c_str() << ENDL;
				}
			}
			else {
				int z_id = atoi(zm["Z_ID"].get<std::string>().c_str());
				buff << zm["BUKVA"].get<std::string>().c_str() << '!';
				if(atoi(zm["NPP_S"].get<std::string>().c_str()) == 2) {
					buff << '!';
				}
				buff << '\"' << conv(zm["NAME"].get<std::string>().c_str()) << '\"' << ENDL;
				tmp = get_json_npp(z_id, l_id);
				if(!tmp.length() || tmp[tmp.length() - 1] != '}') {
					return dwnldcnf_probnick(222);
				}
				json jn = json::parse(tmp);
				for(const auto& n : jn["zm_npp"]) {
					buff << zm["BUKVA"].get<std::string>().c_str() << '=' << n["ZNACHENNJA"].get<std::string>().c_str() << '?' << n["UMOVA"].get<std::string>().c_str() << "?\"" << conv(n["COMENT"].get<std::string>().c_str()) << '\"' << ENDL;
				}
			}
		}
		switch(atoi(lir["BR_KOL_LIR"].get<std::string>().c_str())) {
			case 2: {
				buff << "AB" << ENDL;
				break;
			}
			case 3: {
				buff << "ABC" << ENDL;
				break;
			}
			case 4: {
				buff << "ABCD" << ENDL;
				break;
			}
		}
		tmp = get_json_umovy(l_id);
		if(!tmp.length() || tmp[tmp.length() - 1] != '}') {
			return dwnldcnf_probnick(333);
		}
		json ju = json::parse(tmp);
		for(const auto& u : ju["umovy"]) {
			buff << u["UMOVA"].get<std::string>().c_str() << ENDL;
		}
		i++;
	}
	buff << "L0" << ENDL;
	return buff.str();
}

/*				 j["l_id"].get<int>();
for (const auto& lir : j["liry"]) {
    std::string name = lir["NAME"];
    int br_kol_lir = std::stoi(std::string(lir["BR_KOL_LIR"]));
    std::cout << "Ліра: " << name << ", кількість: " << br_kol_lir << ENDL;
}
for (auto it = j["liry"].begin(); it != j["liry"].end(); ++it) {
    std::cout << (*it)["name"] << ENDL;
}
for (size_t i = 0; i < j["liry"].size(); ++i) {
    std::cout << j["liry"][i]["name"] << ENDL;
}


for (const auto& group : j["liry_zm_arr"]) {
    for (const auto& zm : group) {
        std::string bukva = zm["BUKVA"];
        double val = std::atof(std::string(zm["ZNACHENNJA"]).c_str());
        std::cout << bukva << " = " << val << ENDL;
    }
}

std::string name1 = j["name1"]; // "ZFWVG_250"
std::string mash_id = j["mash"]["M_ID"]; // "1"


#include <nlohmann/json.hpp>
using json = nlohmann::json;

json j = json::parse(json_str);
buff << "[MAIN]\n";
buff << "NAME=" << j["mash"]["NAME"] << "\n";
buff << "[M1]\n" << j["mash"]["M1"] << "\n";
buff << "[M2]\n" << j["mash"]["M2"] << "\n";

for (auto& l : j["liry"]) {
    buff << "[LIRA]\n";
    buff << "ID=" << l["L_ID"] << " FORM=" << l["FORM"] << "\n";
}


int group_id = 0;
for (auto& group : j["liry_zm_arr"]) {
    buff << "[ZMI]" << group_id++ << "\n";
    for (auto& zm : group) {
        buff << zm["BUKVA"] << "=" << zm["ZNACHENNJA"] << "\n";
    }
}


json j = R"({"arr": [ {"x": 1}, {"x": 2} ] })"_json;

for (auto& item : j["arr"]) {
    std::cout << item["x"] << "\n";
}

for (size_t i = 0; i < j["arr"].size(); ++i) {
    json item = j["arr"][i];
    std::cout << item["x"] << "\n";
}


for (const auto& lir : j["liry"]) {
    int l_id = std::stoi(std::string(lir["L_ID"]));
    json extra_data = get_more_data(l_id); // функція, яка вертає додатковий json по id
    std::cout << "Отримали додаткові дані для L_ID=" << l_id << ": " << extra_data.dump() << ENDL;
}



void process_json(const nlohmann::json& j) {
    std::cout << "Назва машини: " << j["mash"]["NAME"] << ENDL;

    for (const auto& lir : j["liry"]) {
        std::cout << "- Ліра: " << lir["NAME"] << ENDL;
    }

    for (const auto& group : j["liry_zm_arr"]) {
        for (const auto& zm : group) {
            std::string zm_name = zm["NAME"];
            std::string bukva = zm["BUKVA"];
            double val = std::atof(std::string(zm["ZNACHENNJA"]).c_str());
            std::cout << "  " << zm_name << " (" << bukva << ") = " << val << ENDL;
        }
    }
}
*/