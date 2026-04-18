/*
 * Entry point for RepositoryDemo.
 *
 * Demonstrates:
 *   1. Derived query methods (findByStatus, findTop5By...)
 *   2. @Query with JPQL (JOIN FETCH to avoid N+1)
 *   3. @Query with @Modifying for bulk update
 *   4. Interface-based projections
 *   5. Custom repository fragment (OrderRepositoryCustom)
 *
 * The H2 in-memory database is used so no external database setup is needed.
 * SQL logs are enabled in application.yaml to show the generated queries.
 */
package com.example.repository;

import com.example.repository.model.Order;
import com.example.repository.model.OrderStatus;
import com.example.repository.projection.OrderSummary;
import com.example.repository.repository.OrderRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.util.List;

@SpringBootApplication
public class RepositoryDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RepositoryDemoApplication.class, args);
    }

    @Bean
    CommandLineRunner demo(OrderRepository orderRepository) {
        return args -> {
            // Seed data
            orderRepository.save(new Order("EXT-001", new BigDecimal("150.00"), OrderStatus.PENDING));
            orderRepository.save(new Order("EXT-002", new BigDecimal("75.00"), OrderStatus.PENDING));
            orderRepository.save(new Order("EXT-003", new BigDecimal("320.00"), OrderStatus.COMPLETED));
            orderRepository.save(new Order("EXT-004", new BigDecimal("45.00"), OrderStatus.CANCELLED));

            System.out.println("=== Derived query: findByStatus ===");
            List<Order> pending = orderRepository.findByStatus(OrderStatus.PENDING);
            pending.forEach(o -> System.out.println("  " + o.getExternalRef() + " / " + o.getAmount()));

            System.out.println("\n=== Interface projection: findSummariesByStatus ===");
            List<OrderSummary> summaries = orderRepository.findSummariesByStatus(OrderStatus.PENDING);
            summaries.forEach(s -> System.out.println("  " + s.getExternalRef() + " / " + s.getDisplayLabel()));

            System.out.println("\n=== @Modifying bulk update ===");
            int updated = orderRepository.cancelOldPendingOrders(new BigDecimal("100.00"));
            System.out.println("  Cancelled " + updated + " low-value pending orders");

            System.out.println("\n=== Custom fragment: findHighValueOrders ===");
            List<Order> highValue = orderRepository.findHighValueOrders(new BigDecimal("200.00"));
            highValue.forEach(o -> System.out.println("  " + o.getExternalRef() + " / " + o.getAmount()));
        };
    }
}
