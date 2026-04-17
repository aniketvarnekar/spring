/*
 * Publishes an OrderPlacedEvent when an order is placed.
 *
 * ApplicationEventPublisher is a single-method interface implemented by ApplicationContext.
 * Injecting only this interface (rather than the full ApplicationContext) is cleaner —
 * it declares the exact capability the service needs.
 *
 * publishEvent is synchronous by default: all synchronous listeners complete before
 * the method returns. Async listeners (those annotated @Async) are submitted to a
 * thread pool and return immediately.
 */
package com.example.events.service;

import com.example.events.event.OrderPlacedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final ApplicationEventPublisher eventPublisher;

    public OrderService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public void placeOrder(String orderId, String customerId) {
        System.out.println("[OrderService]   Processing order " + orderId);

        // Business logic would go here (persist order, update inventory, etc.)

        // The event captures what happened; listeners decide what to do about it.
        // This decouples order processing from audit logging, email dispatch, etc.
        eventPublisher.publishEvent(new OrderPlacedEvent(orderId, customerId));

        System.out.println("[OrderService]   publishEvent returned");
    }
}
