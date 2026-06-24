#include "setup.h"
#include "request.h"
#include "router.h"
#include "http_utils.h"
#include "platform.h"
#include "worker_pool.h"
#include "MachineTime.h"
#include <iostream>
#include <vector>
#include <string>
#include <cstring>
#include <thread>
#include <cstdlib>


struct PortConfig {
	int port;
	int workers;
	std::string id;
};

SetupConfig g_setup;

void print_usage() {
    std::cout << "Usage: server_8266_decoder [-c <config_file>] [-p <id>=<param>]...\n";
    std::cout << "Options:\n";
    std::cout << "  -c, --config <file>   Specify configuration file (default: conf.ini)\n";
    std::cout << "  -p, --param <id=val> Override configuration parameter (can be used multiple times)\n";
    std::cout << "  -h, --help            Show this help message\n";
}
// Parse command-line arguments for -c <config> and -p <id>=<param>
bool parse_arguments(int argc, char* argv[], std::string& config_file, std::vector<std::pair<std::string, std::string>>& overrides) {
	config_file = "conf.ini";  // default
	
	for (int i = 1; i < argc; ++i) {
		if ((strcmp(argv[i], "-c") == 0 || strcmp(argv[i], "--config") == 0) && i + 1 < argc) {
			config_file = argv[++i];
		}
		else if ((strcmp(argv[i], "-p") == 0 || strcmp(argv[i], "--param") == 0) && i + 1 < argc) {
			std::string param_str = argv[++i];
			size_t eq_pos = param_str.find('=');
			if (eq_pos != std::string::npos) {
				std::string id = param_str.substr(0, eq_pos);
				std::string val = param_str.substr(eq_pos + 1);
				overrides.push_back({id, val});
			}
		}
        else if (strcmp(argv[i], "-h") == 0 || strcmp(argv[i], "--help") == 0) {
            print_usage();
            return false;
        }
        else {
            std::cout << "Unknown argument: " << argv[i] << "\n";
            print_usage();
            return false;
        }
	}
    return true;
}

// Read port and workers configuration from setup (UTF-8 string API)
void read_port_configs(std::vector<PortConfig>* g_port_configs) {

	for(int i = 0; i < 256; i++) {
		std::string port_key = "port_" + std::to_string(i);
		if(g_setup.getDefined(port_key)) {
			if(!strcmp("true", g_setup.get(port_key).c_str())) {
				int port = 0, workers = 0;
				std::string port_key_port = port_key + "_port";
				std::string port_key_workers = port_key + "_workers";
				if(g_setup.getDefined(port_key_port)) {
					std::string port_str = g_setup.get(port_key_port);
					if(!(port_str.c_str()[0] >= 48 && port_str.c_str()[0] <= 57)) 
						continue;
					port = std::stoi(port_str);
				}
				if(g_setup.getDefined(port_key_workers)) {
					std::string workers_str = g_setup.get(port_key_workers);
					if(!(workers_str.c_str()[0] >= 48 && workers_str.c_str()[0] <= 57)) 
						continue;
					workers = std::stoi(workers_str);
				}
				if(port && workers) {
					std::cout << "config detect " << port_key << " " << port << ", workers=" << workers << std::endl;
					g_port_configs->push_back({port, workers, port_key});
				}
			}
		}
	}
}

// Apply command-line parameter overrides
void apply_overrides(const std::vector<std::pair<std::string, std::string>>& overrides) {
	for (const auto& override : overrides) {
		g_setup.set(override.first, override.second);
	}
}

// Port listener thread function (receives its config id)
void porter_thread_func(int port, int num_workers, std::string port_id) {
	std::cout << "Starting porter " << port_id << " on port " << port << " with " << num_workers << " workers\n";
	
	socket_t server_fd = socket(AF_INET, SOCK_STREAM, 0);
	if (server_fd == INVALID_SOCKET_HANDLE) {
		std::cerr << "Failed to create socket for port " << port << "\n";
		return;
	}
	
	int opt = 1;
	setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, (const char*)&opt, sizeof(opt));
	
	sockaddr_in server_addr{};
	server_addr.sin_family = AF_INET;
	server_addr.sin_addr.s_addr = htonl(INADDR_ANY);
	server_addr.sin_port = htons(port);
	
	if (bind(server_fd, (struct sockaddr*)&server_addr, sizeof(server_addr)) == SOCKET_ERROR_HANDLE) {
		std::cerr << "Failed to bind port " << port << "\n";
		CLOSE_SOCKET(server_fd);
		return;
	}
	
	if (listen(server_fd, 8) == SOCKET_ERROR_HANDLE) {
		std::cerr << "Listen failed on port " << port << "\n";
		CLOSE_SOCKET(server_fd);
		return;
	}
	
	// Create worker pool for this port
	WorkerPool pool(num_workers);
	pool.start();
	
	std::cout << "Porter listening: " << port_id << " (port " << port << ")\n";
	
	// Accept loop
	while (true) {
		sockaddr_in client_addr{};
		socklen_t client_len = sizeof(client_addr);
		socket_t client_fd = accept(server_fd, (struct sockaddr*)&client_addr, &client_len);
		
		if (client_fd == INVALID_SOCKET_HANDLE) {
			std::cerr << "Accept failed on port " << port << ": " << GET_LAST_ERROR << "\n";
			continue;
		}
		
		// Submit to worker pool
		pool.enqueue(client_fd);
	}
	
	CLOSE_SOCKET(server_fd);
}

int main(int argc, char* argv[]) {
	std::string config_file;
	std::vector<std::pair<std::string, std::string>> overrides;
	std::vector<PortConfig> g_port_configs;

	// Parse command-line arguments
	if (!parse_arguments(argc, argv, config_file, overrides)) {
		return 1;
	}
	
	std::cout << "Loading config from: " << config_file << "\n";
	
	// Open config file
	int config_lines = g_setup.open(config_file.c_str(), 0);
	if (config_lines <= 0) {
		std::cerr << "Failed to open config file: " << config_file << "\n";
		return 1;
	}
	
	std::cout << "Config loaded: " << config_lines << " lines\n";
	
	// Apply command-line parameter overrides
	apply_overrides(overrides);

	// Ініціалізація БД для MachineTime
	std::string db_path = g_setup.get("db_path");
	if (!db_path.empty())
		init_machine_time_db(db_path.c_str());
	start_machine_time_agent_and_signals();

	// Read port and workers configuration
	read_port_configs(&g_port_configs);
	
	if (g_port_configs.empty()) {
		std::cerr << "No ports configured. Exiting.\n";
		g_setup.close();
		return 1;
	}
	
	std::cout << "Configured ports:\n";
	for (const auto& cfg : g_port_configs) {
		std::cout << "  " << cfg.id << ": port " << cfg.port << " with " << cfg.workers << " workers\n";
	}
	
	// Start porter thread for each port
	std::vector<std::thread> porter_threads;
	for (const auto& cfg : g_port_configs) {
		porter_threads.emplace_back(porter_thread_func, cfg.port, cfg.workers, cfg.id);
	}
	
	// Wait for all porter threads (they run forever)
	for (auto& t : porter_threads) {
		t.join();
	}
	
	g_setup.close();
	return 0;
}
