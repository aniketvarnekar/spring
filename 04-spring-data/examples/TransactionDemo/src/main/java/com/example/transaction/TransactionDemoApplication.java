/*
 * Entry point for TransactionDemo.
 *
 * Demonstrates three transaction scenarios:
 *
 *   1. REQUIRED propagation — the called method joins the existing transaction.
 *      If the outer transaction rolls back, inner work is also rolled back.
 *
 *   2. REQUIRES_NEW propagation — the called method runs in its own transaction.
 *      It commits independently of the outer transaction.
 *
 *   3. Self-invocation problem — calling a @Transactional method from within the
 *      same bean bypasses the proxy, so the @Transactional annotation has no effect.
 *      The demo shows this by verifying that audit records created via self-invocation
 *      are not isolated in their own transaction.
 *
 * SQL logging is enabled to make the transaction boundary visible in the output.
 */
package com.example.transaction;

import com.example.transaction.service.OrderService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TransactionDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionDemoApplication.class, args);
    }

    @Bean
    CommandLineRunner demo(OrderService orderService) {
        return args -> {
            System.out.println("=== Scenario 1: REQUIRED propagation (join outer tx) ===");
            orderService.demonstrateRequired();

            System.out.println("\n=== Scenario 2: REQUIRES_NEW (independent tx) ===");
            orderService.demonstrateRequiresNew();

            System.out.println("\n=== Scenario 3: Self-invocation (proxy bypassed) ===");
            orderService.demonstrateSelfInvocation();
        };
    }
}
