/*
 * Thrown when a resource conflict is detected (e.g., duplicate order).
 * Maps to 409 Conflict.
 */
package com.example.exceptionhandling.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super("CONFLICT", HttpStatus.CONFLICT, message);
    }
}
