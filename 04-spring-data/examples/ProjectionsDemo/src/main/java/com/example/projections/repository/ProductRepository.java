/*
 * Repository demonstrating interface projection, DTO projection, and dynamic projection.
 */
package com.example.projections.repository;

import com.example.projections.model.Product;
import com.example.projections.projection.ProductDto;
import com.example.projections.projection.ProductSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // Full entity — loads all columns including description
    List<Product> findByCategory(String category);

    // Interface projection — Spring Data infers SELECT p.name, p.price ... (no description)
    List<ProductSummary> findSummariesByCategory(String category);

    // DTO projection via constructor expression — explicit JPQL, no description column
    @Query("SELECT new com.example.projections.projection.ProductDto(p.name, p.price) " +
           "FROM Product p WHERE p.category = :category")
    List<ProductDto> findDtosByCategory(@Param("category") String category);

    // Dynamic projection — caller specifies the type at call time
    <T> List<T> findByCategory(String category, Class<T> type);
}
