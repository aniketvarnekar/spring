/*
 * User endpoints — requires ROLE_USER or ROLE_ADMIN (enforced at the URL level in SecurityConfig).
 */
package com.example.securityconfig.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    @GetMapping("/profile")
    public Map<String, Object> profile(@AuthenticationPrincipal UserDetails user) {
        return Map.of(
            "username", user.getUsername(),
            "authorities", user.getAuthorities().stream()
                    .map(Object::toString)
                    .toList()
        );
    }
}
