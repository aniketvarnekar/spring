# JPA Repositories

## Overview

`JpaRepository` is the Spring Data JPA root interface for repositories backed by the Java Persistence API. Its default implementation is `SimpleJpaRepository`, which wraps a JPA `EntityManager` and implements all standard CRUD and query operations. Understanding `SimpleJpaRepository` behavior is what explains the sometimes surprising semantics of `save()`, `delete()`, and flush timing.

The `EntityManager` is the JPA counterpart to Spring's `ApplicationContext` — it manages the lifecycle of entity instances within a persistence context. The persistence context is a first-level cache: entities loaded within a context are tracked, and modifications to those entities are automatically detected and persisted on flush. `SimpleJpaRepository` holds an injected `EntityManager` that is typically proxied: each transaction gets its own underlying `EntityManager` instance, but the proxy routes calls to the correct one.

Flush mode determines when the persistence context synchronizes its state with the database. The default flush mode in JPA is `AUTO`, which flushes before query execution (to ensure that queries see the current in-memory state) and at transaction commit. `COMMIT` mode only flushes at commit, which can improve performance but may cause queries to return stale data within the same transaction. Spring Data JPA repositories use `AUTO` by default.

The distinction between `save()` and `saveAndFlush()` is critical for test code and for code that needs to observe database-assigned values (generated IDs, default column values, database-side triggers) immediately. `save()` persists the entity in the persistence context but does not guarantee an immediate SQL statement. `saveAndFlush()` calls `save()` and then flushes, ensuring the SQL is sent to the database within the current transaction.

## Key Concepts

### SimpleJpaRepository.save()

```java
// From SimpleJpaRepository source (simplified):
@Transactional
public <S extends T> S save(S entity) {
    if (entityInformation.isNew(entity)) {
        em.persist(entity);   // INSERT — entity has no ID or isNew() returns true
        return entity;
    } else {
        return em.merge(entity);  // UPDATE — entity has an ID
    }
}
```

`isNew()` checks `@Id` field nullity for non-primitive IDs, or delegates to `Persistable.isNew()` if the entity implements that interface. The persist/merge distinction matters for detached entities: calling `save()` on a detached entity with an existing ID triggers `merge`, which copies the detached entity's state onto a managed instance. The returned instance from `merge` is the managed one — the original detached argument is not managed after the call.

### EntityManager Operations

| Operation | Description |
|---|---|
| `persist(entity)` | Makes a transient entity managed; schedules INSERT |
| `merge(entity)` | Copies detached state onto a managed entity; returns managed instance |
| `remove(entity)` | Schedules DELETE for a managed entity |
| `find(Class, id)` | Loads entity by primary key; returns null if not found |
| `getReference(Class, id)` | Returns a proxy without loading the entity; throws if accessed and missing |
| `flush()` | Synchronizes persistence context to database |
| `refresh(entity)` | Reloads entity state from the database, discarding in-memory changes |
| `detach(entity)` | Removes entity from persistence context; stops tracking changes |
| `clear()` | Detaches all entities from the persistence context |

### save() vs saveAndFlush()

```java
@Service
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public void processOrder(OrderRequest request) {
        Order order = new Order(request);
        Order saved = orderRepository.save(order);

        // At this point, saved.getId() may be null for database-generated IDs
        // if the persistence context has not yet flushed.
        // The INSERT has been queued but not necessarily sent to the database.

        Order savedAndFlushed = orderRepository.saveAndFlush(order);
        // The INSERT has now been sent. saved.getId() is populated.
        // Use this when downstream logic in the same transaction needs the generated ID.
    }
}
```

### Flush Modes

```java
// Force a query to see the current transaction's uncommitted changes
// (only relevant in COMMIT flush mode or when auto-flush is disabled)
@Query("SELECT o FROM Order o WHERE o.status = 'PENDING'")
@QueryHints(@QueryHint(name = org.hibernate.annotations.QueryHints.FLUSH_MODE,
                       value = "AUTO"))
List<Order> findPendingOrders();
```

The default `FlushModeType.AUTO` flushes before any JPQL/HQL query that might be affected by the current pending changes. This avoids stale reads within a transaction at the cost of potentially more frequent flushes. Setting `FlushModeType.COMMIT` at the `EntityManager` level reduces flush frequency but requires care to avoid stale query results.

### Batch Operations

```java
// Avoid N+1 deletes — issues one DELETE FROM orders WHERE id IN (...)
orderRepository.deleteAllByIdInBatch(List.of(1L, 2L, 3L));

// Deletes all — issues DELETE FROM orders (no WHERE clause)
orderRepository.deleteAllInBatch();

// Saves entities and immediately flushes in batches (uses JDBC batching if configured)
orderRepository.saveAllAndFlush(entities);
```

For JDBC-level batching (grouping multiple INSERT/UPDATE statements into one network round trip), configure Hibernate:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50
        order_inserts: true   # reorder inserts to maximize batch grouping
        order_updates: true
```

### Existence Checks

```java
// Returns true/false without loading the entity
boolean exists = orderRepository.existsById(42L);

// Loads and counts — less efficient than existsById for boolean checks
long count = orderRepository.count();

// Conditional existence from derived query
boolean hasActive = orderRepository.existsByCustomerIdAndStatus("CUST-1", "ACTIVE");
```

## Gotchas

`delete(entity)` requires the entity to be managed (in the current persistence context). If you pass a detached entity, `SimpleJpaRepository` calls `em.merge()` first, then `em.remove()` — this loads the entity from the database even if you just want to delete it by ID. Use `deleteById(id)` to avoid the unnecessary merge.

`findAll()` without any specification or pageable returns all rows in the table. On a large table this loads all entities into memory, causing an `OutOfMemoryError`. Always use `findAll(Pageable)` or a `Specification` for queries on tables that grow without bound.

The `@Transactional` annotation on `SimpleJpaRepository` methods uses the default `REQUIRED` propagation, which means they participate in an existing transaction if one is active. A call to `save()` from within a `@Transactional` service method shares the transaction. A call from a non-transactional context causes `save()` to create its own transaction that commits immediately. The behavior difference can cause surprising results when checking return values or expecting flushed state.

`saveAll(Iterable<S>)` calls `save()` in a loop — it is not a true batch insert. For true bulk inserts, use a `@Modifying` `@Query` with native SQL, or configure Hibernate's JDBC batching and use `saveAllAndFlush()`. Without JDBC batching configuration, even 100 calls to `save()` in a loop issue 100 separate INSERT statements.

When an entity is updated after being fetched within the same transaction, Hibernate's dirty checking detects the change and issues an UPDATE automatically on flush — there is no need to call `save()` on a managed entity. Calling `save()` on a managed entity is a no-op (it calls `merge`, which copies the entity's state onto itself). This behavior surprises developers who expect every persistence action to require an explicit `save()` call.
