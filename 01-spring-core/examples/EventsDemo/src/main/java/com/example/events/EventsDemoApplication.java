/*
 * Entry point for EventsDemo.
 *
 * Demonstrates:
 *   1. Publishing a custom event via ApplicationEventPublisher.
 *   2. Synchronous @EventListener — listener runs before publishEvent returns.
 *   3. Asynchronous @EventListener + @Async — listener runs in a separate thread.
 *   4. @Order to control listener invocation sequence.
 *
 * The async listener may print after the main thread continues because it runs
 * in a background thread. Observe thread names in the output to confirm.
 */
package com.example.events;

import com.example.events.event.OrderPlacedEvent;
import com.example.events.service.OrderService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class EventsDemoApplication {

    public static void main(String[] args) throws InterruptedException {
        ConfigurableApplicationContext context =
                SpringApplication.run(EventsDemoApplication.class, args);

        OrderService orderService = context.getBean(OrderService.class);

        System.out.println("--- Placing order ORD-001 ---");
        orderService.placeOrder("ORD-001", "CUST-42");

        System.out.println("--- publishEvent returned; async listener may still be running ---");

        // Brief pause to let the async listener thread complete before the JVM exits.
        // In production code this coordination is handled by the framework shutdown sequence.
        Thread.sleep(500);

        context.close();
    }
}
