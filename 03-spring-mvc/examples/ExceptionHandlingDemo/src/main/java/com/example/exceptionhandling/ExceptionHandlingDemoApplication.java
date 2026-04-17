/*
 * Entry point for ExceptionHandlingDemo.
 *
 * Demonstrates a complete exception handling strategy:
 *   - Custom exception hierarchy (ApiException base)
 *   - @RestControllerAdvice with ProblemDetail responses
 *   - Handling both domain exceptions and Spring MVC built-in exceptions
 *
 * Test endpoints:
 *   GET /api/orders/123          — triggers OrderNotFoundException → 404
 *   GET /api/orders/conflict     — triggers ConflictException → 409
 *   GET /api/orders/error        — triggers unhandled exception → 500
 */
package com.example.exceptionhandling;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ExceptionHandlingDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExceptionHandlingDemoApplication.class, args);
    }
}
