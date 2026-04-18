/*
 * Product entity with four fields — the demo fetches subsets of these fields
 * via projections to show the SQL column reduction effect.
 */
package com.example.projections.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private String category;

    // Description is deliberately verbose — projections skip it to demonstrate column reduction.
    @Column(length = 2000)
    private String description;

    protected Product() {}

    public Product(String name, BigDecimal price, String category, String description) {
        this.name = name;
        this.price = price;
        this.category = category;
        this.description = description;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
}
