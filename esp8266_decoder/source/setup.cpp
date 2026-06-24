#include "setup.h"
#include <fstream>
#include <sstream>

SetupConfig::SetupConfig() = default;
SetupConfig::~SetupConfig() = default;

std::string SetupConfig::trim(const std::string& text) {
    size_t start = 0;
    while (start < text.size() && (text[start] == ' ' || text[start] == '\t' || text[start] == '\r' || text[start] == '\n')) {
        start++;
    }
    size_t end = text.size();
    while (end > start && (text[end - 1] == ' ' || text[end - 1] == '\t' || text[end - 1] == '\r' || text[end - 1] == '\n')) {
        end--;
    }
    return text.substr(start, end - start);
}

bool SetupConfig::is_ignored_line(const std::string& text) {
    std::string trimmed = trim(text);
    return trimmed.empty() || trimmed.front() == '[' || trimmed.front() == '#';
}

std::string SetupConfig::get(const std::string& id) const {
    for (const auto& entry : m_items) {
        if (entry.first == id) {
            return entry.second;
        }
    }
    return {};
}

void SetupConfig::split_line(const std::string& text, std::string& key, std::string& value) {
    std::string trimmed = trim(text);
    size_t eq = trimmed.find('=');
    if (eq != std::string::npos) {
        key = trim(trimmed.substr(0, eq));
        value = trim(trimmed.substr(eq + 1));
        return;
    }

    std::istringstream iss(trimmed);
    iss >> key;
    std::getline(iss, value);
    value = trim(value);
}

int SetupConfig::open(const std::string& path, int reserveLines) {
    m_path = path;
    m_items.clear();

    std::ifstream input(path, std::ios::binary);
    if (!input) {
        return 0;
    }

    if (reserveLines > 0) {
        m_items.reserve(static_cast<size_t>(reserveLines));
    }

    std::string line;
    int count = 0;
    while (std::getline(input, line)) {
        if (!line.empty() && line.back() == '\r') {
            line.pop_back();
        }
        if (is_ignored_line(line)) {
            continue;
        }

        std::string key;
        std::string value;
        split_line(line, key, value);
        if (key.empty()) {
            continue;
        }

        bool found = false;
        for (auto& entry : m_items) {
            if (entry.first == key) {
                entry.second = value;
                found = true;
                break;
            }
        }
        if (!found) {
            m_items.emplace_back(key, value);
        }
        count++;
    }

    return count;
}

int SetupConfig::close() {
    m_items.clear();
    m_path.clear();
    return 0;
}

int SetupConfig::save() const {
    if (m_path.empty()) {
        return 0;
    }

    std::ofstream output(m_path, std::ios::binary | std::ios::trunc);
    if (!output) {
        return 0;
    }

    for (const auto& entry : m_items) {
        output << entry.first << "=" << entry.second << "\n";
    }
    return output ? 1 : 0;
}

int SetupConfig::set(const std::string& id, const std::string& value) {
    if (id.empty()) {
        return -1;
    }
    for (auto& entry : m_items) {
        if (entry.first == id) {
            entry.second = value;
            return 1;
        }
    }
    m_items.emplace_back(id, value);
    return 1;
}

int SetupConfig::get(const std::string& id, std::string& value, int max_len) const {
    for (const auto& entry : m_items) {
        if (entry.first == id) {
            if (max_len == 0) {
                return static_cast<int>(entry.second.size()) + 1;
            }
            if (max_len <= static_cast<int>(entry.second.size())) {
                return -2;
            }
            value = entry.second;
            return static_cast<int>(entry.second.size()) + 1;
        }
    }
    return 0;
}

int SetupConfig::getDefined(const std::string& id) const {
    for (const auto& entry : m_items) {
        if (entry.first == id) {
            return static_cast<int>(entry.second.size()) + 1;
        }
    }
    return 0;
}

int SetupConfig::length(const std::string& path) const {
    std::ifstream input(path, std::ios::binary);
    if (!input) {
        return 0;
    }
    std::string line;
    int count = 0;
    while (std::getline(input, line)) {
        if (!line.empty() && line.back() == '\r') {
            line.pop_back();
        }
        if (is_ignored_line(line)) {
            continue;
        }
        count++;
    }
    return count;
}

