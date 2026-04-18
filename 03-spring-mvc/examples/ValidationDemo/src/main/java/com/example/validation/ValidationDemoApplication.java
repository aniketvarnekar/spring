/*
 * Entry point for ValidationDemo.
 *
 * Demonstrates:
 *   1. @Valid on @RequestBody — MethodArgumentNotValidException on failure
 *   2. @Validated on controller class — ConstraintViolationException on @PathVariable failure
 *   3. Custom @ConstraintValidator (e.g., @ValidCurrency)
 *   4. Validation groups (OnCreate vs OnUpdate)
 *
 * Test requests:
 *   POST /api/orders           with valid body    → 200
 *   POST /api/orders           with empty name    → 400 with field errors
 *   GET  /api/orders/ORD-001   valid path var     → 200
 *   GET  /api/orders/bad       invalid format     → 400
 */
package com.example.validation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ValidationDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ValidationDemoApplication.class, args);
    }
}
