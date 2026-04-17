/*
 * Synchronous event listener that records an audit entry.
 *
 * Runs in the publisher's thread before publishEvent returns. @Order(1) ensures
 * this listener executes before the EmailListener (@Order(2)) when both are
 * synchronous listeners for the same event type.
 *
 * Because this runs synchronously, any unchecked exception thrown here propagates
 * to the publisher's call stack and would abort the placeOrder method. Design
 * synchronous listeners to be resilient or use @Async to isolate failures.
 */
package com.example.events.listener;

import com.example.events.event.OrderPlacedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class AuditListener {

    @EventListener
    @Order(1)
    public void onOrderPlaced(OrderPlacedEvent event) {
        System.out.printf("[AuditListener]  [thread=%s] Recording audit for order %s%n",
                Thread.currentThread().getName(), event.orderId());
    }
}
