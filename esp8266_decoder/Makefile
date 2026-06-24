CXX = g++
CXXFLAGS = -std=c++17 -O2 -Wall -Wextra
TARGET = esp8266_decoder
SRC = esp8266_decoder.cpp

all: $(TARGET)

$(TARGET): $(SRC)
	$(CXX) $(CXXFLAGS) -o $(TARGET) $(SRC)

clean:
	rm -f $(TARGET)
