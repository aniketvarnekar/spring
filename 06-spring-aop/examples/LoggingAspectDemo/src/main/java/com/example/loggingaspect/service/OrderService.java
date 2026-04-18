/*
 * Simple order service that the logging aspect intercepts.
 * Uses an in-memory map to avoid a database dependency.
 * The findById method throws for unknown IDs to demonstrate exception logging.
 */
package com.example.loggingaspect.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderService {

    private final Map<Long, String> store = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    public OrderService() {
        store.put(1L, "Order-001: Laptop");
        store.put(2L, "Order-002: Monitor");
    }

    public List<String> findAll() {
        return List.copyOf(store.values());
    }

    public String findById(Long id) {
        String order = store.get(id);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + id);
        }
        return order;
    }

    public String create(String description) {
        long id = idGen.incrementAndGet();
        String order = "Order-" + String.format("%03d", id) + ": " + description;
        store.put(id, order);
        return order;
    }
}
