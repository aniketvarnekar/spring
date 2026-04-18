/*
 * Entry point for RetryAspectDemo.
 *
 * Demonstrates an @Around aspect that implements a retry policy driven by
 * a custom @Retryable annotation.
 *
 * The FlakeyExternalService simulates a service that fails a configurable
 * number of times before succeeding, allowing the retry aspect to be
 * verified by counting how many attempts were made.
 *
 * Key concepts shown:
 *   - Reading annotation attributes (maxAttempts, backoffMs, retryable exception types)
 *   - Calling pjp.proceed() in a loop
 *   - Distinguishing retryable from non-retryable exceptions
 *   - Linear back-off between attempts
 */
package com.example.retryaspect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RetryAspectDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(RetryAspectDemoApplication.class, args);
    }
}
