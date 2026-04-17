/*
 * Request DTO for creating or updating a product.
 * Separate from the domain model so that the API contract and the domain are decoupled.
 * Bean Validation annotations constrain the incoming data before it reaches the service.
 */
package com.example.restcontroller.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank(message = "Product name is required")
        String name,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than zero")
        BigDecimal price,

        String category) {
}
