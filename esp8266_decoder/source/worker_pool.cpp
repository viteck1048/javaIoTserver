#include "worker_pool.h"
#include "http_parser.h"
#include "http_utils.h"
#include <iostream>
#include <cstring>
#include "router.h"
#include "request.h"
#include "response.h"

WorkerPool::WorkerPool(int num_workers, const std::string& port_id)
	: m_running(false), m_num_workers(num_workers), m_port_id(port_id) {
}

WorkerPool::~WorkerPool() {
	stop();
}

void WorkerPool::start() {
	m_running = true;
	for (int i = 0; i < m_num_workers; ++i) {
		m_workers.emplace_back(&WorkerPool::worker_func, this);
	}
}

void WorkerPool::stop() {
	{
		std::unique_lock<std::mutex> lock(m_mutex);
		m_running = false;
	}
	m_cv.notify_all();
	for (auto& t : m_workers) {
		if (t.joinable()) {
			t.join();
		}
	}
}

void WorkerPool::enqueue(socket_t client_fd) {
	{
		std::unique_lock<std::mutex> lock(m_mutex);
		m_queue.push_back(client_fd);
	}
	m_cv.notify_one();
}

void WorkerPool::worker_func() {
	while (true) {
		socket_t client_fd = INVALID_SOCKET_HANDLE;
		
		{
			std::unique_lock<std::mutex> lock(m_mutex);
			m_cv.wait(lock, [this] { return !m_queue.empty() || !m_running; });
			
			if (!m_running && m_queue.empty()) {
				break;
			}
			
			if (!m_queue.empty()) {
				client_fd = m_queue.front();
				m_queue.pop_front();
			}
		}
		
		if (client_fd == INVALID_SOCKET_HANDLE) {
			continue;
		}
		
		// Parse HTTP request
		Request* rq = parse_http_request(client_fd);
		if (rq == nullptr) {
			send_err(client_fd, 400);
			CLOSE_SOCKET(client_fd);
			continue;
		}
		
		// Dispatch to appropriate handler
		Response* result;
		if (rq->method == GET || rq->method == HEAD) {  // GET
			result = obrobka_get(rq);
		} else if (rq->method == POST) {  // POST
			result = obrobka_post(rq);
		} else if (rq->method == PUT) {  // PUT
			result = obrobka_put(rq);
		} else if (rq->method == DELETE) {  // DELETE
			result = obrobka_delete(rq);
		} else {
			result = new Response(405);
		}
		
		sendResponse(client_fd, rq, result);

		delete result;
		delete rq;
		CLOSE_SOCKET(client_fd);
	}
}
