/*
 * Base exception for all API-level errors.
 *
 * Centralizing status codes and error codes in the base class allows
 * GlobalExceptionHandler to handle the entire hierarchy with a single
 * @ExceptionHandler(ApiException.class) method, avoiding repetition.
 */
package com.example.exceptionhandling.exception;

import org.springframework.http.HttpStatus;

public abstract class ApiException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    protected ApiException(String errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() { return errorCode; }
    public HttpStatus getStatus() { return status; }
}
