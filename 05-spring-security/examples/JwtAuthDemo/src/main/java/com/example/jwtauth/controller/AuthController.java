/*
 * Authentication endpoint.
 *
 * POST /auth/login: Authenticates the provided credentials via AuthenticationManager
 * (which delegates to DaoAuthenticationProvider → UserDetailsService → PasswordEncoder).
 * On success, issues a signed JWT via JwtService and returns it.
 * On failure, AuthenticationManager throws AuthenticationException, which Spring Security
 * translates to a 401 Unauthorized response.
 */
package com.example.jwtauth.controller;

import com.example.jwtauth.model.LoginRequest;
import com.example.jwtauth.model.TokenResponse;
import com.example.jwtauth.service.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        String token = jwtService.issue(authentication);
        return ResponseEntity.ok(new TokenResponse(token));
    }
}
