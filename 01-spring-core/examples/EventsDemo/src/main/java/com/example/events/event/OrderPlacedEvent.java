/*
 * Custom event representing a placed order.
 *
 * As of Spring 4.2, events do not need to extend ApplicationEvent — a plain object
 * is sufficient. Extending ApplicationEvent is retained only when compatibility with
 * ApplicationListener<OrderPlacedEvent> is needed alongside @EventListener.
 *
 * The record declaration makes the event immutable, which is the correct model for
 * events: they describe something that already happened and must not be modified.
 */
package com.example.events.event;

public record OrderPlacedEvent(String orderId, String customerId) {
}
