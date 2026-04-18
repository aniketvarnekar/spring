/*
 * Response payload returned by the login endpoint.
 * Contains the signed JWT that the client must include in subsequent requests.
 */
package com.example.jwtauth.model;

public record TokenResponse(String token) {
}
