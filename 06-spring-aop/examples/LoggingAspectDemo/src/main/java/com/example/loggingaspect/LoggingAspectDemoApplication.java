/*
 * Entry point for LoggingAspectDemo.
 *
 * Demonstrates an @Around logging aspect that intercepts all service and
 * repository methods, logging:
 *   - Method name and arguments on entry
 *   - Duration and return value on successful exit
 *   - Duration and exception details on failure
 *
 * Make requests to observe aspect output in the console:
 *   GET  http://localhost:8080/orders       — succeeds
 *   GET  http://localhost:8080/orders/999   — triggers a simulated exception
 *   POST http://localhost:8080/orders       — creates an order
 */
package com.example.loggingaspect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LoggingAspectDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoggingAspectDemoApplication.class, args);
    }
}
