/*
 * Request DTO for order creation/update with Bean Validation constraints.
 */
package com.example.validation.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderRequest(
        @NotBlank(message = "Customer ID is required")
        String customerId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be positive")
        BigDecimal amount,

        String notes) {
}
