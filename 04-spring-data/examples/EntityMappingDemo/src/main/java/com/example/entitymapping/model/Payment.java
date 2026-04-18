/*
 * Abstract payment entity using SINGLE_TABLE inheritance.
 *
 * SINGLE_TABLE stores all subtypes in one table with a discriminator column.
 * Subtype-specific fields that are not applicable to a given row are stored as NULL.
 * This avoids joins but results in nullable columns for subtype-specific data.
 *
 * Use SINGLE_TABLE when:
 *   - Polymorphic queries are frequent (no joins needed)
 *   - The subtype-specific columns are few
 *   - NULL columns in the table are acceptable
 */
package com.example.entitymapping.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "payments")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "payment_type", discriminatorType = DiscriminatorType.STRING)
public abstract class Payment extends BaseEntity {

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    protected Payment() {}

    protected Payment(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getAmount() { return amount; }
}
