# Tricky Scenarios

Each scenario describes a real-world problem. Read the setup, predict the behavior, then read the explanation.

---

## Scenario 1: The Silent Transaction Failure

**Setup:**

```java
@Service
public class OrderService {

    @Transactional
    public void processOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.PROCESSING);
        audit(orderId);  // <-- calling own method
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void audit(Long orderId) {
        auditRepository.save(new AuditEntry(orderId, "PROCESSING"));
    }
}
```

The `processOrder` method is called from a controller. The audit entry is never saved. Why?

**Explanation:** `audit(orderId)` is called on `this`, not on the Spring proxy. The `REQUIRES_NEW` annotation on `audit` is never evaluated — no AOP interceptor is in the call path. Both operations run inside the same transaction opened by `processOrder`. If that transaction rolls back, the audit entry rolls back too, defeating the `REQUIRES_NEW` intent.

**Fix:** Extract `audit` into a separate `@Service` class and inject it into `OrderService`.

---

## Scenario 2: Circular Dependency with Constructor Injection

**Setup:**

```java
@Service
public class ServiceA {
    public ServiceA(ServiceB b) {}
}

@Service
public class ServiceB {
    public ServiceB(ServiceA a) {}
}
```

The application fails to start. Why? How would you fix it?

**Explanation:** Spring cannot create `ServiceA` because it needs `ServiceB`, and it cannot create `ServiceB` because it needs `ServiceA`. With constructor injection, there is no way to partially create either bean. Spring throws `BeanCurrentlyInCreationException`.

**Fix options:**
1. Redesign to break the cycle. This is always the correct answer — a circular dependency almost always indicates a design problem (extract a shared dependency, introduce an event or interface).
2. Inject one dependency via setter or field injection. This breaks the cycle at the cost of allowing an optional/non-final dependency.
3. Use `@Lazy` on one constructor parameter: `ServiceA(@Lazy ServiceB b)`. This injects a proxy for `ServiceB` and the real instance is created on first use.

---

## Scenario 3: @Cacheable Does Not Cache

**Setup:**

```java
@Service
public class ProductService {

    @Cacheable("products")
    public Product findById(Long id) {
        return productRepository.findById(id).orElseThrow();
    }

    public Product findAndRefresh(Long id) {
        return findById(id);  // cache miss every time
    }
}
```

Calling `findAndRefresh` always hits the database regardless of how many times it is called with the same `id`. Why?

**Explanation:** `findById` is called via `this.findById(id)`, bypassing the Spring proxy. The `@Cacheable` AOP interceptor never runs. The cache is never populated.

**Fix:** Inject `ProductService` into itself and call `self.findById(id)`, or move `findById` to a separate bean.

---

## Scenario 4: Prototype Bean in a Singleton — Always the Same Instance

**Setup:**

```java
@Component
@Scope("prototype")
public class RequestContext {
    public final String id = UUID.randomUUID().toString();
}

@Service
public class OrderProcessor {

    @Autowired
    private RequestContext context;  // injected once at startup

    public void process() {
        System.out.println(context.id);  // always the same UUID
    }
}
```

Every call to `process()` prints the same UUID. Why?

**Explanation:** `RequestContext` is prototype-scoped but `OrderProcessor` is singleton-scoped. The DI container creates `RequestContext` exactly once — when `OrderProcessor` is created — and stores the reference as a field. Subsequent calls to `process()` use that same, never-changing instance.

**Fix:** Inject `ObjectProvider<RequestContext> contextProvider` and call `contextProvider.getObject()` inside `process()`. Or use `@Lookup` method injection.

---

## Scenario 5: `@Transactional` on a `private` Method

**Setup:**

```java
@Service
public class InvoiceService {

    @Transactional
    private void saveInvoice(Invoice invoice) {
        invoiceRepository.save(invoice);
    }

    public void create(Invoice invoice) {
        saveInvoice(invoice);
    }
}
```

No exception is thrown and the code compiles, but changes are not always committed. Why?

**Explanation:** Two problems. First, `saveInvoice` is `private`. AOP proxies (both JDK and CGLIB) cannot override `private` methods. The `@Transactional` annotation is silently ignored. Second, even if the method were `public`, `create` calls `saveInvoice` on `this`, not the proxy — the self-invocation problem again.

**Fix:** Make `saveInvoice` public (or package-private), move it to a separate service, and inject the service.

---

## Scenario 6: Different `@DataJpaTest` and Production Behavior

**Setup:** A test uses `@DataJpaTest` and passes. The application in production throws a constraint violation that does not appear in tests.

**Explanation:** `@DataJpaTest` by default replaces the configured data source with an in-memory H2 database. DDL is generated from the entity model, so the schema always matches the entities. In production, the schema is managed separately (Flyway, Liquibase, or hand-written SQL). A column added to the entity but missing from the migration script causes the violation. Additionally, H2 does not enforce all constraints that PostgreSQL/MySQL enforce (e.g., deferrable constraints, partial indexes, certain character encoding rules).

**Fix:** Configure `@DataJpaTest` to use the real database type for contract-critical tests. Set `@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)` and provide a Testcontainers data source via `@DynamicPropertySource`.

---

## Scenario 7: Spring Security `@PreAuthorize` Not Applied

**Setup:**

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    // ...no @EnableMethodSecurity
}

@Service
public class AdminService {
    @PreAuthorize("hasRole('ADMIN')")
    public void sensitiveOperation() { ... }
}
```

`sensitiveOperation()` is callable by any authenticated user. Why?

**Explanation:** `@EnableMethodSecurity` (or the older `@EnableGlobalMethodSecurity`) is required to activate the AOP interceptors that evaluate `@PreAuthorize` expressions. Without it, the annotation is present in bytecode but never processed. Method calls reach the target without any security check.

**Fix:** Add `@EnableMethodSecurity` to the security configuration class.

---

## Scenario 8: `@EventListener` and Transaction Timing

**Setup:**

```java
@Service
public class OrderService {

    @Autowired
    private ApplicationEventPublisher publisher;

    @Transactional
    public void placeOrder(Order order) {
        orderRepository.save(order);
        publisher.publishEvent(new OrderPlacedEvent(order));
    }
}

@Component
public class EmailListener {

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        emailService.sendConfirmation(event.order());  // reads order from DB — not found
    }
}
```

The email listener runs but the order is not found in the database when it tries to read it. Why?

**Explanation:** `@EventListener` fires synchronously within the same thread, while `placeOrder` is still inside its open transaction. The `save(order)` is in the persistence context but not yet committed to the database. The email listener's read (in a different transaction or in no transaction) cannot see the uncommitted row.

**Fix:** Replace `@EventListener` with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`. The listener then fires after the transaction commits, guaranteeing the data is visible to all transactions.

---

## Scenario 9: `@Async` Loses the SecurityContext

**Setup:**

```java
@Service
public class ReportService {

    @Async
    public void generateReport() {
        // SecurityContextHolder.getContext().getAuthentication() == null
        String user = SecurityContextHolder.getContext().getAuthentication().getName();
        // NullPointerException
    }
}
```

An async method throws `NullPointerException` when accessing `SecurityContextHolder`. Why?

**Explanation:** `SecurityContextHolder` uses `MODE_THREADLOCAL` by default. When `@Async` executes the method in a different thread from a `TaskExecutor`, the new thread starts with an empty `SecurityContext`. The principal is lost.

**Fix options:**
1. Set `SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL)` — propagates the context to child threads, but this does not work if the `TaskExecutor` recycles threads from a pool.
2. Wrap the executor with `DelegatingSecurityContextAsyncTaskExecutor` in the `@EnableAsync` configuration.
3. Use `SecurityContextHolder.getContext()` at the call site and pass the authentication as a parameter to the async method.

---

## Scenario 10: Multiple `@EnableMethodSecurity` Annotations Cause Double Evaluation

**Setup:**

A `@SpringBootTest` slice test includes its own `@EnableMethodSecurity` annotation on a test configuration class. The `@PreAuthorize` expression is evaluated twice. The second evaluation sometimes succeeds and sometimes fails depending on context state.

**Explanation:** `@EnableMethodSecurity` registers a `MethodSecurityInterceptor` bean. If the annotation appears in both the production configuration and a test configuration that is loaded into the same context, two interceptors are registered. Each intercepts the method call independently. Double evaluation can cause unexpected behavior (e.g., a rate limiter in the security expression runs twice, or the authentication context changes between evaluations).

**Fix:** Declare `@EnableMethodSecurity` in exactly one `@Configuration` class in the production code. Do not repeat it in test configurations. Use `@Import(SecurityConfig.class)` in the test slice to reuse the production security configuration.
