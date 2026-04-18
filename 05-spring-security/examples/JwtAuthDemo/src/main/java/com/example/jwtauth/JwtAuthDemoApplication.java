/*
 * Entry point for JwtAuthDemo.
 *
 * Demonstrates stateless JWT authentication:
 *   1. POST /auth/login  — authenticate with username/password, receive a JWT
 *   2. GET  /api/me      — protected endpoint; include the JWT in the Authorization header
 *
 * Example flow:
 *   curl -s -X POST http://localhost:8080/auth/login \
 *     -H 'Content-Type: application/json' \
 *     -d '{"username":"alice","password":"password123"}' | jq -r .token
 *
 *   curl -s http://localhost:8080/api/me \
 *     -H 'Authorization: Bearer <token from above>'
 *
 * In-memory users: alice/password123 (ROLE_USER), bob/admin456 (ROLE_ADMIN).
 */
package com.example.jwtauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JwtAuthDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(JwtAuthDemoApplication.class, args);
    }
}
