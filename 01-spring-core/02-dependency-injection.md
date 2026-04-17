# Dependency Injection

## Overview

Dependency Injection (DI) is the mechanism by which the Spring container satisfies a bean's declared dependencies at creation time. Rather than a class instantiating its collaborators with `new`, the container supplies them, making the class's dependencies explicit in its API and swappable without modifying the class itself. Spring supports three injection styles — constructor, setter, and field — each with distinct semantics and trade-offs.

Constructor injection is the style recommended by the Spring team and the broader community. Dependencies declared as constructor parameters are mandatory by definition: the object cannot be instantiated without them, they are set exactly once, and they can be declared `final`, making the object effectively immutable after construction. This forces the dependency graph to be explicit and detectable at application startup rather than at first use.

Setter injection is appropriate for optional dependencies that have a reasonable default value. Because the setter can be called multiple times or not at all, setter-injected fields cannot be `final`, and there is no compile-time guarantee that the dependency is present. Spring's historical preference for setter injection (predating annotation-based configuration) is no longer recommended for most cases.

Field injection — placing `@Autowired` directly on a field — is the most concise form but the most problematic. It hides the dependency from the public API, breaks plain instantiation for unit testing, and is impossible to make `final`. The Spring team has deprecated its use in favor of constructor injection for exactly these reasons, though it remains widely seen in existing codebases.

## Key Concepts

### Constructor Injection

When a class has exactly one constructor, Spring uses it automatically without requiring an explicit `@Autowired` annotation (as of Spring 4.3):

```java
@Service
public class OrderService {

    private final InventoryRepository inventoryRepository;
    private final PaymentGateway paymentGateway;

    // No @Autowired needed with a single constructor
    public OrderService(InventoryRepository inventoryRepository,
                        PaymentGateway paymentGateway) {
        this.inventoryRepository = inventoryRepository;
        this.paymentGateway = paymentGateway;
    }
}
```

With multiple constructors, `@Autowired` designates which one the container should use. If no constructor is annotated, the no-arg constructor is used as a fallback.

### Setter Injection

```java
@Component
public class NotificationService {

    private AuditLogger auditLogger;

    // The dependency is optional; the service functions without it
    @Autowired(required = false)
    public void setAuditLogger(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }
}
```

The `required = false` attribute prevents a `NoSuchBeanDefinitionException` when no bean of the required type is present.

### @Autowired Resolution Order

Spring resolves `@Autowired` injection points in this order:

```
1. Match by type  →  find all beans assignable to the injection point type
2. Match by name  →  if multiple candidates, narrow by field/parameter name
3. @Qualifier    →  narrow further by qualifier value
4. @Primary      →  among remaining candidates, prefer the one marked @Primary
```

If exactly one candidate remains after all steps, it is injected. If zero remain and `required = true` (the default), a `NoSuchBeanDefinitionException` is thrown. If more than one remains, a `NoUniqueBeanDefinitionException` is thrown.

```java
@Component
@Qualifier("fast")
public class FastPaymentGateway implements PaymentGateway { ... }

@Component
@Qualifier("reliable")
@Primary
public class ReliablePaymentGateway implements PaymentGateway { ... }

@Service
public class CheckoutService {

    private final PaymentGateway defaultGateway;   // receives ReliablePaymentGateway (@Primary)
    private final PaymentGateway expressGateway;   // receives FastPaymentGateway

    public CheckoutService(PaymentGateway defaultGateway,
                           @Qualifier("fast") PaymentGateway expressGateway) {
        this.defaultGateway = defaultGateway;
        this.expressGateway = expressGateway;
    }
}
```

### @Primary vs @Qualifier

`@Primary` declares a default when multiple beans of the same type exist. It is a class-level annotation that affects all injection points of that type unless overridden by a `@Qualifier`. `@Qualifier` is more precise: it targets a specific injection point and overrides `@Primary`. The two are not mutually exclusive — a bean can be both `@Primary` and carry a `@Qualifier` value.

### Circular Dependencies

A circular dependency exists when bean A requires bean B and bean B requires bean A (directly or transitively). With constructor injection, Spring cannot resolve this at all — it throws a `BeanCurrentlyInCreationException` at startup, which is the correct and desirable behavior because a genuine circular dependency is usually a design flaw.

```
BeanCurrentlyInCreationException: Is there an unresolvable circular reference?
  A → B → A
```

With setter or field injection, Spring can resolve some circular dependencies by injecting an early reference — a partially initialized proxy — into the waiting bean. This works only for singleton-scoped beans. The mechanism is controlled by `AbstractAutowireCapableBeanFactory.allowCircularReferences`, which defaults to `true` but is set to `false` in Spring Boot 2.6+.

The correct fix for a circular dependency is a redesign: extract the shared behavior into a third bean, use an event-driven approach, or apply `@Lazy` to break the cycle at the injection point:

```java
@Service
public class A {
    private final B b;
    public A(@Lazy B b) { this.b = b; }
}
```

`@Lazy` on an injection point causes Spring to inject a proxy that resolves the actual bean only on first method call. This breaks the circular dependency during construction without changing the overall design, though it does not eliminate the underlying coupling.

### ObjectProvider for Optional and Lazy Injection

`ObjectProvider<T>` is a superinterface of `ObjectFactory<T>` that provides richer dependency resolution:

```java
@Component
public class ReportService {

    private final ObjectProvider<CacheManager> cacheManagerProvider;

    public ReportService(ObjectProvider<CacheManager> cacheManagerProvider) {
        this.cacheManagerProvider = cacheManagerProvider;
    }

    public Report generate(ReportRequest request) {
        // Resolved lazily; returns null if no CacheManager bean exists
        CacheManager cache = cacheManagerProvider.getIfAvailable();
        // ...
    }
}
```

| Method | Behavior |
|--------|----------|
| `getObject()` | Throws if unavailable (like standard injection) |
| `getIfAvailable()` | Returns null if no bean exists |
| `getIfUnique()` | Returns null if zero or more than one bean exists |
| `stream()` | Streams all matching beans |
| `orderedStream()` | Streams all matching beans respecting `@Order` |

`ObjectProvider` is particularly useful in auto-configuration classes and framework code where a dependency may or may not be provided by the application.

## Gotchas

Field injection makes unit testing painful because there is no constructor or setter to use for providing test doubles — you must resort to reflection (via frameworks like Mockito's `@InjectMocks`) or full Spring context initialization. Constructor injection allows plain `new MyService(mockRepo)` in tests.

When Spring resolves a `@Autowired` collection injection point — for example, `List<MessageConverter>` — it collects all beans implementing `MessageConverter` and injects them as a list. The order of beans in the list is undefined unless `@Order` or the `Ordered` interface is used. Assuming insertion order or alphabetical order will produce intermittent bugs.

`@Qualifier` values are strings and are not validated at compile time. A typo in a qualifier string causes a `NoSuchBeanDefinitionException` at startup, not a compile error. For large codebases, declaring custom qualifier annotations (meta-annotated with `@Qualifier`) is safer because the annotation type is checked at compile time.

The `required = false` attribute on `@Autowired` is easy to misread: it means the injection is optional, not that the bean is optional at all times. If the bean exists but is the wrong type or has an initialization error, the injection still fails. Use `ObjectProvider.getIfAvailable()` when you need truly safe optional resolution.

Setter injection for `@Configuration`-class `@Bean` methods does not go through the proxy mechanism the way constructor injection does. If bean A calls `this.b()` within a `@Configuration` class (full mode), Spring intercepts the call to return the cached singleton. But if A holds a reference obtained via setter injection, no proxy intercepts setter calls, which is correct — the reference was resolved once during wiring and is reused.
