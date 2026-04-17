/*
 * Entry point for RestControllerDemo.
 *
 * Demonstrates a full CRUD REST API:
 *   - GET    /api/products         list all products (with optional filtering)
 *   - GET    /api/products/{id}    get one product
 *   - POST   /api/products         create a product
 *   - PUT    /api/products/{id}    replace a product
 *   - DELETE /api/products/{id}    delete a product
 *
 * Run the app and use curl or an HTTP client:
 *   curl -X POST http://localhost:8080/api/products \
 *        -H "Content-Type: application/json" \
 *        -d '{"name":"Widget","price":9.99}'
 */
package com.example.restcontroller;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RestControllerDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestControllerDemoApplication.class, args);
    }
}
