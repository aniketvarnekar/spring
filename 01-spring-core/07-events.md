# Events

## Overview

The Spring event system provides a built-in publish-subscribe mechanism within an `ApplicationContext`. Any bean can publish events to any number of listeners without knowing which listeners exist or what they do. This decouples producers from consumers of notable occurrences — user registration, order placement, configuration changes — and enables clean cross-cutting behavior like audit logging, cache invalidation, and notification dispatch without introducing direct dependencies.

Spring's event model is built on `ApplicationEvent` (extending `java.util.EventObject`) and `ApplicationListener<E extends ApplicationEvent>`. As of Spring 4.2, arbitrary objects can be published as events without extending `ApplicationEvent`, and listeners can be declared with `@EventListener` on any `@Component` method, making the annotation-based style far cleaner than implementing the listener interface.

By default, event dispatch is synchronous and happens in the publisher's thread. The listener completes before `publishEvent()` returns. This synchronous guarantee is useful for transactional scenarios — a listener can participate in the same transaction as the publisher — but it means that a slow or throwing listener directly impacts the publishing code path. Asynchronous listeners are the solution for fire-and-forget notification scenarios.

Spring itself publishes several lifecycle events that allow application code to hook into the container lifecycle:  `ContextRefreshedEvent`, `ContextStartedEvent`, `ContextStoppedEvent`, and `ContextClosedEvent`. Spring Boot extends this with richer startup events: `ApplicationStartingEvent`, `ApplicationEnvironmentPreparedEvent`, `ApplicationContextInitializedEvent`, `ApplicationStartedEvent`, and `ApplicationReadyEvent`.

## Key Concepts

### Defining and Publishing Events

```java
// Custom event — extends ApplicationEvent for compatibility, but not required in Spring 4.2+
public class OrderPlacedEvent {
    private final String orderId;
    private final String customerId;

    public OrderPlacedEvent(String orderId, String customerId) {
        this.orderId = orderId;
        this.customerId = customerId;
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
}

@Service
public class OrderService {

    private final ApplicationEventPublisher eventPublisher;

    public OrderService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void placeOrder(OrderRequest request) {
        Order order = processOrder(request);
        // Event is published synchronously before the transaction commits
        // Listener runs in the same transaction if it participates
        eventPublisher.publishEvent(new OrderPlacedEvent(order.getId(), request.customerId()));
    }
}
```

`ApplicationEventPublisher` is a single-method interface that `ApplicationContext` implements. Injecting it is preferable to injecting `ApplicationContext` when only event publishing is needed.

### @EventListener

```java
@Component
public class NotificationDispatcher {

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        // Called synchronously by the publishing thread
        sendConfirmationEmail(event.getCustomerId(), event.getOrderId());
    }

    // Conditional listener — Spring SpEL expression filters events before dispatch
    @EventListener(condition = "#event.customerId.startsWith('VIP')")
    public void onVipOrderPlaced(OrderPlacedEvent event) {
        prioritizeShipping(event.getOrderId());
    }
}
```

The condition attribute accepts a SpEL expression. The event is bound as `#event` or `#root.event`. The listener method is not called if the expression evaluates to `false`.

### Asynchronous Listeners

Annotate the listener method with `@Async` to run it in a separate thread pool. `@EnableAsync` must be on a `@Configuration` class:

```java
@Configuration
@EnableAsync
public class AsyncConfig { }

@Component
public class AuditLogger {

    @EventListener
    @Async
    public void onOrderPlaced(OrderPlacedEvent event) {
        // Runs in a thread from the default TaskExecutor (SimpleAsyncTaskExecutor by default)
        // The publishing thread is not blocked
        writeAuditRecord(event);
    }
}
```

An async listener runs outside the publisher's transaction. If transactional behavior is needed in the listener, the listener method must be `@Transactional` and the transaction will be a new one. Return values from async `@EventListener` methods are ignored.

For finer control, return a `CompletableFuture` from the listener or configure a custom `TaskExecutor` bean named `taskExecutor`.

### @TransactionalEventListener

This is the most important event listener variant for database-backed applications. It delays listener invocation until a specific transaction phase:

```java
@Component
public class SearchIndexUpdater {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        // Only called if the publishing transaction committed successfully
        // If the transaction rolled back, this method is never invoked
        updateSearchIndex(event.getOrderId());
    }
}
```

| Phase | When the listener is invoked |
|-------|------------------------------|
| `BEFORE_COMMIT` | Before the transaction commits |
| `AFTER_COMMIT` | After the transaction commits |
| `AFTER_ROLLBACK` | After the transaction rolls back |
| `AFTER_COMPLETION` | After the transaction completes (commit or rollback) |

If no transaction is active when the event is published, `@TransactionalEventListener` methods are not invoked by default. Set `fallbackExecution = true` to invoke them regardless.

### Ordered Listeners

When multiple listeners respond to the same event type, `@Order` controls their relative invocation sequence. Lower values run first:

```java
@Component
public class ValidationListener {
    @EventListener
    @Order(1)
    public void validate(OrderPlacedEvent event) { ... }
}

@Component
public class PersistenceListener {
    @EventListener
    @Order(2)
    public void persist(OrderPlacedEvent event) { ... }
}
```

Ordering applies within a single synchronous dispatch. Async listeners run concurrently and `@Order` has no effect on their relative scheduling.

### Generic Events

```java
// Generic event type
public class EntityCreatedEvent<T> {
    private final T entity;
    public EntityCreatedEvent(T entity) { this.entity = entity; }
    public T getEntity() { return entity; }
}

// Listener for a specific parameterization — Spring resolves the generic parameter
@EventListener
public void onUserCreated(EntityCreatedEvent<User> event) {
    User user = event.getEntity();
    // ...
}
```

For generic event resolution to work, the event type must retain generic type information at runtime. If published as `new EntityCreatedEvent<>(user)`, Java's type erasure means the generic parameter is lost. Implement `ResolvableTypeProvider` in the event class to restore it:

```java
public class EntityCreatedEvent<T> implements ResolvableTypeProvider {
    private final T entity;

    public EntityCreatedEvent(T entity) { this.entity = entity; }

    @Override
    public ResolvableType getResolvableType() {
        // Provides the actual generic type at runtime, bypassing erasure
        return ResolvableType.forClassWithGenerics(getClass(),
                ResolvableType.forInstance(entity));
    }
}
```

## Gotchas

A synchronous `@EventListener` that throws an unchecked exception propagates the exception to the publisher's call stack. If the publisher is inside a `@Transactional` method, an unchecked exception from a listener can trigger an unexpected rollback. Either wrap listener calls in a try-catch or use an async listener to isolate listener failures.

`@TransactionalEventListener` with `AFTER_COMMIT` is often used to trigger out-of-band side effects (email, search index, external API call) after a database write. The listener does not participate in the committed transaction — it runs after it. If the side-effect listener itself requires database access, it needs its own transaction (`REQUIRES_NEW` propagation). Without this, the listener runs in a transaction-less context and any write it performs is auto-committed.

Spring Boot's early application lifecycle events (`ApplicationStartingEvent`, `ApplicationEnvironmentPreparedEvent`) fire before the `ApplicationContext` is created. `@EventListener` beans cannot receive these events because no beans exist yet. To listen for these events, implement `ApplicationListener` and register it via `META-INF/spring.factories` or `SpringApplication.addListeners()`.

If `@EnableAsync` is not configured, `@Async` on an `@EventListener` method is silently ignored — the method runs synchronously. There is no warning in the logs. Always verify async behavior with an integration test that checks thread names if async dispatch is critical to correctness.

Publishing events inside `@PostConstruct` works, but few listeners will be registered at that point because bean initialization is still in progress. Any `@EventListener` on a bean that has not yet been initialized will miss the event. Use `SmartInitializingSingleton.afterSingletonsInstantiated()` or `ApplicationRunner.run()` to publish events after the entire container is ready.
