/*
 * Entry point for SecurityConfigDemo.
 *
 * Demonstrates a complete Spring Security configuration:
 *   - Form login with custom login page path
 *   - In-memory users with BCrypt-encoded passwords
 *   - Role-based HTTP authorization (USER, ADMIN)
 *   - Method-level security with @PreAuthorize
 *   - CSRF enabled (default for session-based apps)
 *
 * Test endpoints:
 *   GET /public/hello     — accessible without authentication
 *   GET /user/profile     — requires ROLE_USER or ROLE_ADMIN
 *   GET /admin/dashboard  — requires ROLE_ADMIN
 *   POST /admin/action    — requires @PreAuthorize("hasRole('ADMIN')")
 */
package com.example.securityconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SecurityConfigDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurityConfigDemoApplication.class, args);
    }
}
