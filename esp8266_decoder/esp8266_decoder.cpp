#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/socket.h>
#include <unistd.h>
#include <cerrno>
#include <cstring>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>
#include <algorithm>
#include <cctype>

static constexpr int LISTEN_PORT = 12354;
static constexpr int BACKLOG = 8;

struct ResponseData {
    uint16_t channels[20];
    uint8_t flag;
    uint8_t checksum;
};

static std::string toHex(const std::vector<uint8_t>& bytes) {
    std::ostringstream out;
    out << std::hex;
    for (uint8_t b : bytes) {
        out.width(2);
        out.fill('0');
        out << static_cast<int>(b);
    }
    return out.str();
}

static bool validateChecksum(const std::vector<uint8_t>& buffer) {
    if (buffer.size() < 40) {
        return false;
    }
    int sum = 0;
    for (size_t i = 0; i < 40; ++i) {
        if (i == 22 || i == 23) {
            continue;
        }
        sum += buffer[i];
    }
    uint8_t expected = 0x25 ^ ((sum % 256) ^ (sum >> 8));
    return buffer[23] == expected;
}

static ResponseData decodeResponse(const std::vector<uint8_t>& buffer) {
    ResponseData result{};
    if (buffer.size() >= 40) {
        for (int i = 0; i < 20; ++i) {
            if (i == 11) {
                result.channels[i] = 0;
                continue;
            }
            size_t pos = i * 2;
            if (pos + 1 < buffer.size()) {
                result.channels[i] = static_cast<uint16_t>((buffer[pos] << 8) | buffer[pos + 1]);
            }
        }
        result.flag = buffer[22];
        result.checksum = buffer[23];
    }
    return result;
}

static bool parseHeaderFields(const std::string& request, std::string& method, std::string& path, std::vector<std::string>& headers, std::string& body) {
    size_t headerEnd = request.find("\r\n\r\n");
    if (headerEnd == std::string::npos) {
        return false;
    }
    std::string headerBlock = request.substr(0, headerEnd);
    body = request.substr(headerEnd + 4);

    std::istringstream stream(headerBlock);
    if (!std::getline(stream, headerBlock)) {
        return false;
    }
    if (headerBlock.back() == '\r') {
        headerBlock.pop_back();
    }
    std::istringstream requestLine(headerBlock);
    requestLine >> method >> path;
    std::string headerLine;
    while (std::getline(stream, headerLine)) {
        if (!headerLine.empty() && headerLine.back() == '\r') {
            headerLine.pop_back();
        }
        if (!headerLine.empty()) {
            headers.push_back(headerLine);
        }
    }
    return true;
}

static std::string headerValue(const std::vector<std::string>& headers, const std::string& name) {
    std::string key = name + ":";
    for (const auto& line : headers) {
        if (line.size() >= key.size()) {
            std::string left = line.substr(0, key.size());
            std::transform(left.begin(), left.end(), left.begin(), [](unsigned char c) { return std::tolower(c); });
            std::string search = key;
            std::transform(search.begin(), search.end(), search.begin(), [](unsigned char c) { return std::tolower(c); });
            if (left == search) {
                std::string value = line.substr(key.size());
                while (!value.empty() && std::isspace(static_cast<unsigned char>(value.front()))) {
                    value.erase(value.begin());
                }
                return value;
            }
        }
    }
    return "";
}

static bool parseJsonBody(const std::string& body, int& flag, std::string& data) {
    auto findKey = [&](const std::string& key) -> size_t {
        std::string token = "\"" + key + "\"";
        return body.find(token);
    };

    size_t flagPos = findKey("flag");
    size_t dataPos = findKey("data");
    if (flagPos == std::string::npos || dataPos == std::string::npos) {
        return false;
    }

    auto parseNumber = [&](size_t pos, int& out) -> bool {
        size_t colon = body.find(':', pos);
        if (colon == std::string::npos) {
            return false;
        }
        size_t start = colon + 1;
        while (start < body.size() && std::isspace(static_cast<unsigned char>(body[start]))) {
            ++start;
        }
        size_t end = start;
        while (end < body.size() && (std::isdigit(static_cast<unsigned char>(body[end])) || body[end] == '-')) {
            ++end;
        }
        if (start == end) {
            return false;
        }
        try {
            out = std::stoi(body.substr(start, end - start));
            return true;
        } catch (...) {
            return false;
        }
    };

    auto parseString = [&](size_t pos, std::string& out) -> bool {
        size_t colon = body.find(':', pos);
        if (colon == std::string::npos) {
            return false;
        }
        size_t quote = body.find('"', colon);
        if (quote == std::string::npos) {
            return false;
        }
        size_t endQuote = quote + 1;
        std::ostringstream result;
        while (endQuote < body.size()) {
            char c = body[endQuote++];
            if (c == '"') {
                out = result.str();
                return true;
            }
            if (c == '\\' && endQuote < body.size()) {
                result << body[endQuote++];
            } else {
                result << c;
            }
        }
        return false;
    };

    if (!parseNumber(flagPos, flag)) {
        return false;
    }
    if (!parseString(dataPos, data)) {
        return false;
    }
    return true;
}

static bool decodeStandardBase64(const std::string& input, std::vector<uint8_t>& output) {
    static const char* table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    int mapping[256];
    std::fill(std::begin(mapping), std::end(mapping), -1);
    for (int i = 0; table[i]; ++i) {
        mapping[static_cast<unsigned char>(table[i])] = i;
    }

    std::vector<uint8_t> temp;
    temp.reserve((input.size() * 3) / 4);
    int value = 0;
    int bits = 0;
    for (char c : input) {
        if (std::isspace(static_cast<unsigned char>(c)) || c == '=') {
            continue;
        }
        int d = mapping[static_cast<unsigned char>(c)];
        if (d < 0) {
            return false;
        }
        value = (value << 6) | d;
        bits += 6;
        if (bits >= 8) {
            bits -= 8;
            temp.push_back(static_cast<uint8_t>((value >> bits) & 0xFF));
        }
    }
    output.swap(temp);
    return !output.empty();
}

static bool decodeEspCustomBase64(const std::string& input, std::vector<uint8_t>& output) {
    if (input.size() < 5u) {
        return false;
    }
    std::string data = input;
    for (char& c : data) {
        if (c == ',') {
            c = '\\';
        }
    }
    size_t payloadLength = data.size() - 1;
    if (payloadLength % 4 != 0) {
        return false;
    }
    uint8_t reshta = static_cast<uint8_t>(data.back()) & 0x03u;
    size_t blocks = payloadLength / 4;
    if (blocks == 0) {
        return false;
    }
    size_t totalBytes = (reshta == 0) ? blocks * 3 : (blocks - 1) * 3 + reshta;
    std::vector<uint8_t> temp(blocks * 3);
    for (size_t i = 0; i < blocks; ++i) {
        size_t base = i * 4;
        uint8_t v0 = static_cast<uint8_t>(data[base + 0]) & 0x3Fu;
        uint8_t v1 = static_cast<uint8_t>(data[base + 1]) & 0x3Fu;
        uint8_t v2 = static_cast<uint8_t>(data[base + 2]) & 0x3Fu;
        uint8_t v3 = static_cast<uint8_t>(data[base + 3]) & 0x3Fu;
        temp[i * 3 + 0] = static_cast<uint8_t>((v0 << 2) | ((v1 & 0x30u) >> 4));
        temp[i * 3 + 1] = static_cast<uint8_t>(((v1 & 0x0Fu) << 4) | ((v2 & 0x3Cu) >> 2));
        temp[i * 3 + 2] = static_cast<uint8_t>(((v2 & 0x03u) << 6) | (v3 & 0x3Fu));
    }
    if (totalBytes > temp.size()) {
        return false;
    }
    output.assign(temp.begin(), temp.begin() + totalBytes);
    return true;
}

static std::string makeJsonResponse(bool ok, const std::string& message) {
    std::ostringstream out;
    out << "{\"status\":\"" << (ok ? "ok" : "error") << "\",\"message\":\"";
    for (char c : message) {
        if (c == '"') {
            out << "\\\"";
        } else if (c == '\\') {
            out << "\\\\";
        } else {
            out << c;
        }
    }
    out << "\"}";
    return out.str();
}

static void sendHttpResponse(int clientFd, const std::string& status, const std::string& body, const std::string& contentType = "application/json") {
    std::ostringstream response;
    response << "HTTP/1.1 " << status << "\r\n";
    response << "Content-Type: " << contentType << "; charset=UTF-8\r\n";
    response << "Content-Length: " << body.size() << "\r\n";
    response << "Connection: close\r\n";
    response << "\r\n";
    response << body;
    std::string packet = response.str();
    ssize_t total = 0;
    while (total < static_cast<ssize_t>(packet.size())) {
        ssize_t sent = send(clientFd, packet.data() + total, packet.size() - total, 0);
        if (sent <= 0) {
            break;
        }
        total += sent;
    }
}

static bool readRequest(int clientFd, std::string& request) {
    constexpr size_t bufferSize = 4096;
    char buffer[bufferSize];
    ssize_t bytesRead;
    while ((bytesRead = recv(clientFd, buffer, bufferSize, 0)) > 0) {
        request.append(buffer, bytesRead);
        if (request.find("\r\n\r\n") != std::string::npos) {
            break;
        }
    }
    if (bytesRead < 0) {
        return false;
    }
    if (request.empty()) {
        return false;
    }
    std::vector<std::string> headers;
    std::string method, path, body;
    if (!parseHeaderFields(request, method, path, headers, body)) {
        return false;
    }
    std::string lengthValue = headerValue(headers, "Content-Length");
    if (!lengthValue.empty()) {
        size_t contentLength = static_cast<size_t>(std::stoul(lengthValue));
        while (body.size() < contentLength) {
            bytesRead = recv(clientFd, buffer, bufferSize, 0);
            if (bytesRead <= 0) {
                break;
            }
            body.append(buffer, bytesRead);
        }
        request = request.substr(0, request.find("\r\n\r\n") + 4) + body;
    }
    return true;
}

int main() {
    int serverFd = socket(AF_INET, SOCK_STREAM, 0);
    if (serverFd < 0) {
        std::cerr << "Failed to create socket: " << strerror(errno) << "\n";
        return 1;
    }

    int opt = 1;
    setsockopt(serverFd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));

    sockaddr_in serverAddr{};
    serverAddr.sin_family = AF_INET;
    serverAddr.sin_addr.s_addr = INADDR_ANY;
    serverAddr.sin_port = htons(LISTEN_PORT);

    if (bind(serverFd, reinterpret_cast<sockaddr*>(&serverAddr), sizeof(serverAddr)) < 0) {
        std::cerr << "Failed to bind port " << LISTEN_PORT << ": " << strerror(errno) << "\n";
        close(serverFd);
        return 1;
    }

    if (listen(serverFd, BACKLOG) < 0) {
        std::cerr << "Listen failed: " << strerror(errno) << "\n";
        close(serverFd);
        return 1;
    }

    std::cout << "ESP8266 decoder service listening on port " << LISTEN_PORT << "\n";

    while (true) {
        sockaddr_in clientAddr{};
        socklen_t clientLen = sizeof(clientAddr);
        int clientFd = accept(serverFd, reinterpret_cast<sockaddr*>(&clientAddr), &clientLen);
        if (clientFd < 0) {
            std::cerr << "Accept failed: " << strerror(errno) << "\n";
            continue;
        }

        std::string request;
        if (!readRequest(clientFd, request)) {
            sendHttpResponse(clientFd, "400 Bad Request", makeJsonResponse(false, "Invalid HTTP request"));
            close(clientFd);
            continue;
        }

        std::string method, path, body;
        std::vector<std::string> headers;
        if (!parseHeaderFields(request, method, path, headers, body)) {
            sendHttpResponse(clientFd, "400 Bad Request", makeJsonResponse(false, "Invalid HTTP header parse"));
            close(clientFd);
            continue;
        }

        if (method != "POST") {
            if (method == "GET") {
                std::string info = "{\"service\":\"esp8266_decoder\",\"status\":\"ready\"}";
                sendHttpResponse(clientFd, "200 OK", info);
            } else {
                sendHttpResponse(clientFd, "405 Method Not Allowed", makeJsonResponse(false, "Use POST or GET"));
            }
            close(clientFd);
            continue;
        }

        int flag = 0;
        std::string dataString;
        if (!parseJsonBody(body, flag, dataString)) {
            sendHttpResponse(clientFd, "400 Bad Request", makeJsonResponse(false, "Missing JSON fields 'flag' or 'data'"));
            close(clientFd);
            continue;
        }

        std::vector<uint8_t> payload;
        std::string decodeMode;
        if (decodeEspCustomBase64(dataString, payload)) {
            decodeMode = "custom_esp_base64";
        } else if (decodeStandardBase64(dataString, payload)) {
            decodeMode = "standard_base64";
        } else {
            sendHttpResponse(clientFd, "400 Bad Request", makeJsonResponse(false, "Unable to decode ESP payload"));
            close(clientFd);
            continue;
        }

        std::ostringstream result;
        result << "{\"status\":\"ok\",\"flag\":" << flag;
        result << ",\"payloadLength\":" << payload.size();
        result << ",\"decodeMode\":\"" << decodeMode << "\"";
        result << ",\"checksumValid\":" << (validateChecksum(payload) ? "true" : "false");
        result << ",\"rawHex\":\"" << toHex(payload) << "\"";

        if (payload.size() >= 40) {
            ResponseData parsed = decodeResponse(payload);
            result << ",\"decodedFlag\":" << static_cast<int>(parsed.flag);
            result << ",\"checksum\":" << static_cast<int>(parsed.checksum);
            result << ",\"channels\": [";
            for (int i = 0; i < 20; ++i) {
                result << parsed.channels[i];
                if (i + 1 < 20) {
                    result << ",";
                }
            }
            result << "]";
        }
        result << "}";

        sendHttpResponse(clientFd, "200 OK", result.str());
        close(clientFd);
    }

    close(serverFd);
    return 0;
}
