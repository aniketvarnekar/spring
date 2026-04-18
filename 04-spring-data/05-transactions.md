# Transactions

## Overview

Spring's transaction management is declarative: annotate a method with `@Transactional` and the framework wraps the call in a transaction, committing on successful return and rolling back on an unchecked exception. The implementation is AOP-based — a proxy intercepts the method call, begins a transaction via `PlatformTransactionManager`, and coordinates commit or rollback in the proxy's `after` advice.

Understanding `@Transactional` requires understanding the proxy mechanism. The proxy and the real object are separate instances. Calls from outside the bean go through the proxy and participate in transaction management. Calls from within the same bean — self-invocations — bypass the proxy and therefore bypass transaction management entirely. This is the most common source of "why isn't my transaction working?" bugs in Spring applications.

Transaction propagation determines what happens when a transactional method is called while a transaction is already active. The default propagation is `REQUIRED`, which reuses the existing transaction if one is present. `REQUIRES_NEW` suspends the current transaction and starts a new one. `NESTED` creates a savepoint within the current transaction. The other propagation behaviors (`SUPPORTS`, `NOT_SUPPORTED`, `MANDATORY`, `NEVER`) are used for specific coordination scenarios.

Read-only transactions are an optimization hint that can significantly improve performance in JPA. When `readOnly = true`, Hibernate skips dirty checking of loaded entities (because no modifications are expected), may skip the flush before query execution, and allows the underlying JDBC driver and database to apply read-only optimizations (e.g., routing to a read replica).

## Key Concepts

### @Transactional Attributes

```java
@Transactional(
    propagation = Propagation.REQUIRED,         // reuse existing or create new
    isolation = Isolation.READ_COMMITTED,       // default for most databases
    readOnly = false,                           // allow writes
    rollbackFor = {SomeCheckedException.class}, // also roll back for this exception
    noRollbackFor = {ExpectedRuntimeEx.class},  // do not roll back for this
    timeout = 30                                // seconds; -1 = no timeout
)
public void processOrder(OrderRequest request) { ... }
```

By default, Spring only rolls back for unchecked exceptions (`RuntimeException` and `Error`). Checked exceptions cause commit unless explicitly listed in `rollbackFor`. This default matches the common convention that checked exceptions are recoverable; override it when a checked exception represents a non-recoverable failure.

### Propagation Behaviors

| Propagation | Existing transaction present | No existing transaction |
|---|---|---|
| `REQUIRED` (default) | Joins existing | Creates new |
| `REQUIRES_NEW` | Suspends existing, creates new | Creates new |
| `NESTED` | Creates savepoint within existing | Creates new |
| `SUPPORTS` | Joins existing | Runs without transaction |
| `NOT_SUPPORTED` | Suspends existing, runs without | Runs without transaction |
| `MANDATORY` | Joins existing | Throws `IllegalTransactionStateException` |
| `NEVER` | Throws `IllegalTransactionStateException` | Runs without transaction |

```java
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final AuditService auditService;

    @Transactional
    public void placeOrder(OrderRequest request) {
        Order order = orderRepository.save(new Order(request));

        // REQUIRES_NEW: audit always commits independently of the order transaction.
        // If the order transaction rolls back, the audit record is still persisted.
        auditService.record("ORDER_PLACED", order.getId());
    }
}

@Service
public class AuditService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String event, Long entityId) {
        // Runs in its own separate transaction
        auditRepository.save(new AuditRecord(event, entityId));
    }
}
```

### NESTED vs REQUIRES_NEW

`NESTED` creates a savepoint in the current transaction. If the nested method rolls back, only the work since the savepoint is undone — the outer transaction can continue and commit. Rolling back the outer transaction also rolls back the nested work. This is a partial rollback pattern.

`REQUIRES_NEW` is a completely independent transaction. Its commit or rollback has no relation to the outer transaction's outcome. Use it for operations that must persist regardless of the outer transaction's fate.

Not all databases support savepoints (required for `NESTED`). Check your database's support before using `NESTED` propagation.

### The Self-Invocation Problem

```java
@Service
public class OrderService {

    @Transactional
    public void processOrders() {
        // This calls this.completeOrder() directly on the real object,
        // NOT through the proxy. The @Transactional(REQUIRES_NEW) on
        // completeOrder is bypassed — it runs in the SAME transaction.
        for (Order order : findPending()) {
            completeOrder(order);  // self-invocation: proxy bypassed
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeOrder(Order order) {
        // This runs in a new transaction only when called from OUTSIDE this bean.
        // When called from processOrders(), it participates in processOrders()'s transaction.
    }
}
```

Solutions to self-invocation:
1. Move `completeOrder()` to a separate bean.
2. Inject the bean into itself (via `@Lazy` or `@Autowired` in the field): `orderService.completeOrder(order)` goes through the proxy.
3. Use `AopContext.currentProxy()` to obtain the proxy reference (requires `exposeProxy = true` in `@EnableTransactionManagement`).

### Read-Only Transactions

```java
@Service
public class ReportService {

    // readOnly = true provides several optimizations in JPA:
    // 1. Hibernate skips dirty checking on loaded entities (no snapshot comparison)
    // 2. Hibernate may skip the flush before JPQL queries (since no changes are expected)
    // 3. The database or connection pool may route to a read replica
    @Transactional(readOnly = true)
    public ReportData generateReport(LocalDate from, LocalDate to) {
        List<Order> orders = orderRepository.findByCreatedAtBetween(from, to);
        return buildReport(orders);
    }
}
```

Spring Data JPA marks all `findBy*` and `findAll*` repository methods as `readOnly = true` by default in `SimpleJpaRepository`. Only `save()`, `delete()`, and `@Modifying` methods are marked as read-write.

### Transaction Isolation

| Isolation Level | Dirty Read | Non-Repeatable Read | Phantom Read |
|---|---|---|---|
| `READ_UNCOMMITTED` | Possible | Possible | Possible |
| `READ_COMMITTED` (typical default) | Prevented | Possible | Possible |
| `REPEATABLE_READ` | Prevented | Prevented | Possible |
| `SERIALIZABLE` | Prevented | Prevented | Prevented |

Specifying `Isolation.DEFAULT` (the Spring default) uses the database's own default isolation level. Most production databases default to `READ_COMMITTED`.

## Gotchas

`@Transactional` on an interface method is supported but not recommended. JDK dynamic proxies respect annotations on interface methods, but CGLIB proxies (used when the bean is not accessed via an interface) do not scan interface annotations by default. Placing `@Transactional` on the concrete class method or the `@Service` class is safer and more explicit.

`@Transactional` on `private` or `final` methods is silently ignored by Spring's proxy mechanism. The proxy cannot override private or final methods, so the transactional advice is never applied. There is no startup warning — the method runs outside any transaction without any indication that the annotation was ignored.

When `@Transactional(rollbackFor = Exception.class)` is used, ALL exceptions cause rollback, including checked exceptions that the caller might have handled and considered recoverable. This is a blunt instrument; prefer listing specific exception types or catching and re-throwing where rollback is not desired.

The `timeout` attribute terminates the transaction if it runs for longer than the specified number of seconds by marking it for rollback. A timeout does not interrupt a running JDBC query — it marks the transaction as rollback-only and throws `TransactionTimedOutException` at the next Spring transaction check point (usually at the next repository call). Long-running queries need to be interrupted at the database level via statement timeouts.

Lazy-loaded JPA associations accessed after the transaction closes throw `LazyInitializationException`. This is the "open session in view" problem — the entity's associations cannot be loaded because the `EntityManager` that owns the persistence context is closed. Solutions include: fetch associations eagerly in the query (JOIN FETCH), use DTOs or projections, or enable the `OpenEntityManagerInViewFilter` (an anti-pattern in high-throughput APIs because it holds a database connection open for the duration of the HTTP request).
