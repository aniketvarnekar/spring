/*
 * Global exception handler using @RestControllerAdvice.
 *
 * Handles the entire ApiException hierarchy with a single @ExceptionHandler method,
 * and delegates standard Spring MVC exception handling to ResponseEntityExceptionHandler.
 *
 * All responses use ProblemDetail (RFC 7807) for a consistent client API.
 * The "errorCode" extension field allows clients to programmatically distinguish
 * error types without parsing the human-readable "detail" string.
 */
package com.example.exceptionhandling.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Handles the entire ApiException hierarchy — OrderNotFoundException, ConflictException, etc.
    // Spring selects the most specific matching @ExceptionHandler, so more specific types
    // can be handled separately if they need different response structure.
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
        log.warn("API exception: {} — {}", ex.getErrorCode(), ex.getMessage());

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        // errorCode is a machine-readable identifier clients can switch on
        detail.setProperty("errorCode", ex.getErrorCode());

        return ResponseEntity.status(ex.getStatus()).body(detail);
    }

    // Safety net for unexpected exceptions — prevents stack traces leaking to clients.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        // Log the full exception for ops, but return only a generic message to the client.
        log.error("Unexpected exception", ex);

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. The incident has been logged.");
        return ResponseEntity.internalServerError().body(detail);
    }
}
