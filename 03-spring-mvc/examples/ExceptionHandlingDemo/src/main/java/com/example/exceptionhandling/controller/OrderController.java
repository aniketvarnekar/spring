/*
 * Demo controller that throws different exception types to exercise GlobalExceptionHandler.
 */
package com.example.exceptionhandling.controller;

import com.example.exceptionhandling.exception.ConflictException;
import com.example.exceptionhandling.exception.OrderNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping("/{id}")
    public Map<String, String> getOrder(@PathVariable String id) {
        switch (id) {
            case "conflict" -> throw new ConflictException("An order with a duplicate reference already exists");
            case "error"    -> throw new RuntimeException("Simulated unexpected failure");
            default         -> throw new OrderNotFoundException(id);
        }
    }
}
