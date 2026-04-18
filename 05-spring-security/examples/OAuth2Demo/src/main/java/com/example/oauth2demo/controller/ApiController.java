/*
 * Resource server API endpoints.
 *
 * The authenticated principal after JWT validation is a Jwt object, accessible via
 * @AuthenticationPrincipal Jwt jwt. Claims are read directly from the validated token.
 *
 * /api/public  — no authentication required
 * /api/me      — returns token subject and all granted authorities
 * /api/admin   — restricted to tokens with SCOPE_admin; enforced in SecurityConfig
 */
package com.example.oauth2demo.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    @GetMapping("/public")
    public Map<String, String> publicEndpoint() {
        return Map.of("message", "This endpoint requires no authentication.");
    }

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
            "subject",     jwt.getSubject(),
            "authorities", SecurityContextHolder.getContext().getAuthentication()
                               .getAuthorities().stream().map(Object::toString).toList(),
            "claims",      jwt.getClaims()
        );
    }

    @GetMapping("/admin")
    public Map<String, String> admin(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
            "message", "Admin-only endpoint. Subject: " + jwt.getSubject()
        );
    }
}
