/*
 * In-memory product service — replaces a real database for the demo.
 * Demonstrates the service layer that the controller delegates to.
 */
package com.example.restcontroller.service;

import com.example.restcontroller.model.Product;
import com.example.restcontroller.model.ProductRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private final ConcurrentHashMap<Long, Product> store = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    public ProductService() {
        // Seed with sample data so the GET /api/products endpoint returns results immediately.
        create(new ProductRequest("Widget A", new java.math.BigDecimal("9.99"), "widgets"));
        create(new ProductRequest("Widget B", new java.math.BigDecimal("14.99"), "widgets"));
        create(new ProductRequest("Gadget X", new java.math.BigDecimal("49.99"), "gadgets"));
    }

    public List<Product> findAll(String category) {
        return store.values().stream()
                .filter(p -> category == null || category.equals(p.category()))
                .collect(Collectors.toList());
    }

    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public Product create(ProductRequest request) {
        long id = idSequence.getAndIncrement();
        Product product = new Product(id, request.name(), request.price(), request.category());
        store.put(id, product);
        return product;
    }

    public Optional<Product> update(Long id, ProductRequest request) {
        if (!store.containsKey(id)) {
            return Optional.empty();
        }
        Product updated = new Product(id, request.name(), request.price(), request.category());
        store.put(id, updated);
        return Optional.of(updated);
    }

    public boolean delete(Long id) {
        return store.remove(id) != null;
    }
}
