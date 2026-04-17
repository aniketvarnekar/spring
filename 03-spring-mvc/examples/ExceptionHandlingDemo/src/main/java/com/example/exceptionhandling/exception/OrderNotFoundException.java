/*
 * Thrown when a requested order does not exist.
 * Maps to 404 Not Found via ApiException.status.
 */
package com.example.exceptionhandling.exception;

import org.springframework.http.HttpStatus;

public class OrderNotFoundException extends ApiException {

    private final String orderId;

    public OrderNotFoundException(String orderId) {
        super("ORDER_NOT_FOUND", HttpStatus.NOT_FOUND,
              "Order not found: " + orderId);
        this.orderId = orderId;
    }

    public String getOrderId() { return orderId; }
}
