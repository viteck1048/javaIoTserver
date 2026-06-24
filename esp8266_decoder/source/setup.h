#pragma once

#include <string>
#include <vector>
#include <utility>

class SetupConfig {
public:
	SetupConfig();
	~SetupConfig();

	int open(const std::string& path, int reserveLines = 0);
	int close();
	int save() const;
	int set(const std::string& id, const std::string& value);
	int get(const std::string& id, std::string& value, int max_len) const;
	int getDefined(const std::string& id) const;
	std::string get(const std::string& id) const;
	int length(const std::string& path) const;

private:
	static std::string trim(const std::string& text);
	static bool is_ignored_line(const std::string& text);
	static void split_line(const std::string& text, std::string& key, std::string& value);

	std::string m_path;
	std::vector<std::pair<std::string, std::string>> m_items;
};