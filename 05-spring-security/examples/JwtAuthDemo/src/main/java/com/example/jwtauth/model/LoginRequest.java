/*
 * Login request payload containing credentials.
 * Both fields are required; blank values are rejected before the authentication attempt.
 */
package com.example.jwtauth.model;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) {
}
