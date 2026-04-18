/*
 * Entry point for OAuth2Demo.
 *
 * Demonstrates a JWT resource server using spring-boot-starter-oauth2-resource-server.
 * The application validates incoming Bearer tokens using a symmetric secret key
 * configured in application.yaml (spring.security.oauth2.resourceserver.jwt.secret-key).
 *
 * For a production resource server, replace the symmetric key with a JWKS URI pointing
 * to the authorization server:
 *   spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://auth.example.com/.well-known/jwks.json
 *
 * Test endpoints:
 *   GET /api/public   — permitAll, no token required
 *   GET /api/me       — requires a valid JWT; returns subject and authorities
 *   GET /api/admin    — requires SCOPE_admin claim in the JWT
 *
 * The test class generates tokens in-process using a shared secret so no live
 * authorization server is required.
 */
package com.example.oauth2demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OAuth2DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(OAuth2DemoApplication.class, args);
    }
}
