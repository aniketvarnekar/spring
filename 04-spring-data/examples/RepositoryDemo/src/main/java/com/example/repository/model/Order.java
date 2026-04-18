/*
 * Simple Order entity with a status enum and a BigDecimal amount.
 * Serves as the subject for all repository demonstration queries.
 */
package com.example.repository.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_ref", nullable = false, unique = true)
    private String externalRef;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    // EnumType.STRING stores the name ("PENDING", "COMPLETED") rather than the ordinal.
    // This is resilient to enum value reordering.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Order() {}

    public Order(String externalRef, BigDecimal amount, OrderStatus status) {
        this.externalRef = externalRef;
        this.amount = amount;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getExternalRef() { return externalRef; }
    public BigDecimal getAmount() { return amount; }
    public OrderStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setStatus(OrderStatus status) { this.status = status; }
}
