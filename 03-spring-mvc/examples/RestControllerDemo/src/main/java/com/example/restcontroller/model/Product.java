/*
 * Domain model for a product resource.
 * Record provides immutability and compact syntax appropriate for a simple domain object.
 */
package com.example.restcontroller.model;

import java.math.BigDecimal;

public record Product(Long id, String name, BigDecimal price, String category) {
}
