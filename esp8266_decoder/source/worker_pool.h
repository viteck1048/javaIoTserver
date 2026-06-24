#pragma once

#include "platform.h"
#include "request.h"
#include <deque>
#include <mutex>
#include <condition_variable>
#include <thread>
#include <vector>

class WorkerPool {
private:
	std::deque<socket_t> m_queue;
	std::mutex m_mutex;
	std::condition_variable m_cv;
	std::vector<std::thread> m_workers;
	bool m_running;
	int m_num_workers;
	std::string m_port_id;
	
	void worker_func();
	
public:
	WorkerPool(int num_workers, const std::string& port_id = "");
	~WorkerPool();
	
	void start();
	void stop();
	void enqueue(socket_t client_fd);
};
