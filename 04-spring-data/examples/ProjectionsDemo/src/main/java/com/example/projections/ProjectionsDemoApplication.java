/*
 * Entry point for ProjectionsDemo.
 *
 * Compares three approaches to fetching partial data:
 *   1. Full entity — loads all columns into a managed entity
 *   2. Interface projection — Spring Data proxy with only the needed columns
 *   3. DTO (record) projection — constructor expression in @Query, plain object
 *   4. Dynamic projection — caller selects the projection type at invocation time
 *
 * SQL logging is enabled to show the SELECT column list differences between approaches.
 */
package com.example.projections;

import com.example.projections.model.Product;
import com.example.projections.projection.ProductDto;
import com.example.projections.projection.ProductSummary;
import com.example.projections.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;
import java.util.List;

@SpringBootApplication
public class ProjectionsDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectionsDemoApplication.class, args);
    }

    @Bean
    CommandLineRunner demo(ProductRepository productRepository) {
        return args -> {
            productRepository.save(new Product("Widget", new BigDecimal("9.99"), "widgets", "A small widget"));
            productRepository.save(new Product("Gadget", new BigDecimal("49.99"), "gadgets", "An advanced gadget"));
            productRepository.save(new Product("Doohickey", new BigDecimal("4.99"), "widgets", "Indescribable"));

            System.out.println("=== Full entity (all columns) ===");
            productRepository.findByCategory("widgets")
                    .forEach(p -> System.out.println("  " + p.getName() + " / " + p.getDescription()));

            System.out.println("\n=== Interface projection (name + price only) ===");
            List<ProductSummary> summaries = productRepository.findSummariesByCategory("widgets");
            summaries.forEach(s -> System.out.println("  " + s.getName() + " / " + s.getPrice()));

            System.out.println("\n=== DTO projection via @Query constructor expression ===");
            List<ProductDto> dtos = productRepository.findDtosByCategory("widgets");
            dtos.forEach(d -> System.out.println("  " + d.name() + " / " + d.price()));

            System.out.println("\n=== Dynamic projection — caller chooses type ===");
            List<ProductSummary> dynamic = productRepository.findByCategory("gadgets", ProductSummary.class);
            dynamic.forEach(s -> System.out.println("  " + s.getName()));
        };
    }
}
