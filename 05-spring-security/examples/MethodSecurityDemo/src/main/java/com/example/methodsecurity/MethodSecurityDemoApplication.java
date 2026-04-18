/*
 * Entry point for MethodSecurityDemo.
 *
 * Demonstrates method-level security:
 *   - @PreAuthorize with SpEL role and ownership checks
 *   - @PostFilter to trim a returned list based on the caller's identity
 *   - A custom security bean (@Component("documentSecurity")) referenced in SpEL
 *   - A meta-annotation (@AdminOnly) that wraps @PreAuthorize
 *
 * In-memory users: alice/pass (ROLE_USER, owner of doc-1 and doc-2),
 *                  bob/pass  (ROLE_USER, owner of doc-3),
 *                  carol/pass (ROLE_ADMIN).
 *
 * Test the behavior with @WithMockUser in MethodSecurityTest.
 */
package com.example.methodsecurity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MethodSecurityDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MethodSecurityDemoApplication.class, args);
    }
}
