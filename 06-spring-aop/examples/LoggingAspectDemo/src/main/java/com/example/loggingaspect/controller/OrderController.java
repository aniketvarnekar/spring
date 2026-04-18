/*
 * REST controller for orders.
 * Intercepted by LoggingAspect because the class is annotated with @RestController.
 * Exceptions from OrderService propagate here and are also caught by the logging aspect.
 */
package com.example.loggingaspect.controller;

import com.example.loggingaspect.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public List<String> list() {
        return orderService.findAll();
    }

    @GetMapping("/{id}")
    public String get(@PathVariable Long id) {
        return orderService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public String create(@RequestBody Map<String, String> body) {
        return orderService.create(body.get("description"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleNotFound(IllegalArgumentException ex) {
        return Map.of("error", ex.getMessage());
    }
}
