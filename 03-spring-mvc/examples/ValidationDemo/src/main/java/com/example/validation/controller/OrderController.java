/*
 * Controller demonstrating two validation styles:
 *
 *   1. @Valid on @RequestBody — validates the entire deserialized object graph.
 *      Failure throws MethodArgumentNotValidException (→ 400).
 *
 *   2. @Validated at the class level + constraint on @PathVariable — validates
 *      individual parameters using Bean Validation.
 *      Failure throws ConstraintViolationException (→ 400 via GlobalExceptionHandler).
 *
 * @Validated at the class level is required for path variable / request param validation.
 * @Valid alone does not enable method-level validation for simple types.
 */
package com.example.validation.controller;

import com.example.validation.constraint.ValidOrderId;
import com.example.validation.model.OrderRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@Validated  // enables method-level validation for @PathVariable and @RequestParam
public class OrderController {

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody @Valid OrderRequest request) {
        // If @Valid fails, MethodArgumentNotValidException is thrown before this body executes.
        return ResponseEntity.ok(Map.of(
                "status", "created",
                "customerId", request.customerId(),
                "amount", request.amount()));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Map<String, String>> get(
            // @ValidOrderId is a custom constraint — format must be ORD-{digits}.
            // @Validated on the class activates this constraint via AOP proxy.
            @PathVariable @ValidOrderId String orderId) {
        return ResponseEntity.ok(Map.of("orderId", orderId, "status", "PENDING"));
    }
}
