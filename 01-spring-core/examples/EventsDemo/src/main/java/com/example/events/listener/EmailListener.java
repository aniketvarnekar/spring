/*
 * Asynchronous event listener that sends a confirmation email.
 *
 * @Async causes Spring to submit this method to the configured TaskExecutor
 * rather than running it in the publisher's thread. The publisher's call to
 * publishEvent returns without waiting for this listener to complete.
 *
 * @Order has no effect on async listeners because they run concurrently —
 * the @Order(2) annotation here documents the intended conceptual order but
 * does not enforce sequencing relative to AuditListener.
 *
 * @EnableAsync must be present in a @Configuration class for @Async to take effect.
 * Without it, @Async is silently ignored and the method runs synchronously.
 */
package com.example.events.listener;

import com.example.events.event.OrderPlacedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class EmailListener {

    @EventListener
    @Async
    @Order(2)
    public void onOrderPlaced(OrderPlacedEvent event) {
        // The thread name will be from the TaskExecutor pool, not the main thread.
        System.out.printf("[EmailListener]  [thread=%s] Sending confirmation email for order %s to customer %s%n",
                Thread.currentThread().getName(), event.orderId(), event.customerId());
    }
}
