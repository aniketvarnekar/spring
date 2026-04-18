/*
 * Protected API endpoints.
 *
 * Requires a valid JWT in the Authorization: Bearer header.
 * JwtAuthenticationFilter populates the SecurityContext before these handlers run.
 * @AuthenticationPrincipal resolves to the username string stored as the principal
 * in the UsernamePasswordAuthenticationToken constructed by the filter.
 */
package com.example.jwtauth.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal String username) {
        var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream().map(Object::toString).toList();
        return Map.of(
            "username",    username,
            "authorities", authorities
        );
    }
}
