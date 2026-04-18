# Custom Queries

## Overview

When a query cannot be expressed through method name derivation, `@Query` allows the developer to write the query directly using JPQL (Java Persistence Query Language), HQL (Hibernate Query Language), or native SQL. `@Query` is placed on a repository method and the framework routes calls to that method through the specified query rather than deriving one from the name.

JPQL operates on entity objects and their mapped relationships, not on database tables and columns. It is portable across JPA providers and is the default for `@Query`. Native SQL operates directly on tables and columns, enabling database-specific features (window functions, JSON operators, `FOR UPDATE SKIP LOCKED`, etc.) at the cost of portability. Mixing JPQL and native SQL in the same repository is common for applications that need standard queries plus a few database-specific operations.

`@Modifying` is required for `@Query` methods that modify data (INSERT, UPDATE, DELETE). Without it, the framework assumes the query is read-only and wraps it in a transaction that does not permit data modification. `@Modifying` also controls whether the persistence context is cleared after the query — important for maintaining consistency when the query modifies entities that may already be cached in the context.

Parameters in `@Query` expressions are bound either positionally (`?1`, `?2`) or by name (`:paramName`). Named parameters are strongly preferred because they are resilient to parameter order changes. `@Param("paramName")` binds a method parameter to a named query parameter by name; without it, Spring Data uses the method parameter name if `-parameters` is enabled at the compiler level.

## Key Concepts

### JPQL Queries

```java
public interface OrderRepository extends JpaRepository<Order, Long> {

    // JPQL operates on entities and their mapped fields, not database tables
    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.amount > :minAmount")
    List<Order> findByStatusAndMinAmount(@Param("status") String status,
                                         @Param("minAmount") BigDecimal minAmount);

    // JOIN FETCH to avoid N+1: loads orders and their items in one query
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.customer.id = :customerId")
    List<Order> findWithItemsByCustomerId(@Param("customerId") Long customerId);

    // Aggregate query returning a scalar value
    @Query("SELECT SUM(o.amount) FROM Order o WHERE o.status = 'COMPLETED'")
    BigDecimal totalCompletedAmount();

    // Constructor expression — creates a DTO without loading full entities
    @Query("SELECT new com.example.dto.OrderSummary(o.id, o.status, o.amount) " +
           "FROM Order o WHERE o.customer.id = :customerId")
    List<OrderSummary> findSummariesByCustomerId(@Param("customerId") Long customerId);

    // Pagination with @Query — Spring Data appends ORDER BY and LIMIT/OFFSET
    @Query(value = "SELECT o FROM Order o WHERE o.status = :status",
           countQuery = "SELECT COUNT(o) FROM Order o WHERE o.status = :status")
    Page<Order> findByStatusPaged(@Param("status") String status, Pageable pageable);
}
```

### Native SQL Queries

```java
// nativeQuery = true makes Spring Data pass the query directly to the database
@Query(value = "SELECT * FROM orders WHERE status = :status AND created_at > NOW() - INTERVAL '7 days'",
       nativeQuery = true)
List<Order> findRecentByStatus(@Param("status") String status);

// Native query with pagination — countQuery is required for Page return type
@Query(value = "SELECT o.* FROM orders o WHERE o.customer_id = :customerId ORDER BY o.created_at DESC",
       countQuery = "SELECT COUNT(*) FROM orders WHERE customer_id = :customerId",
       nativeQuery = true)
Page<Order> findByCustomerIdNative(@Param("customerId") Long customerId, Pageable pageable);

// Native query returning a projection interface — column names must match interface methods
@Query(value = "SELECT id, status, amount FROM orders WHERE status = :status",
       nativeQuery = true)
List<OrderProjection> findProjectionsByStatus(@Param("status") String status);
```

### @Modifying

```java
// UPDATE — @Modifying required; @Transactional ensures this runs within a transaction
@Modifying
@Transactional
@Query("UPDATE Order o SET o.status = :newStatus WHERE o.status = :oldStatus")
int updateStatusBulk(@Param("oldStatus") String oldStatus,
                     @Param("newStatus") String newStatus);

// DELETE — single SQL statement, no lifecycle callbacks fired
@Modifying
@Transactional
@Query("DELETE FROM Order o WHERE o.status = 'CANCELLED' AND o.createdAt < :before")
int deleteCancelledBefore(@Param("before") LocalDateTime before);

// clearAutomatically flushes and clears the persistence context after the query,
// preventing stale cached entity state from being returned by subsequent finds.
// Default is false — enable when the query modifies entities that may be in the context.
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("UPDATE Order o SET o.priority = 1 WHERE o.customerId = :customerId")
void resetPriorityForCustomer(@Param("customerId") Long customerId);
```

### SpEL Expressions in Queries

Spring Data supports SpEL expressions in `@Query` via `#{...}` syntax:

```java
// #{#entityName} resolves to the entity name of the repository's domain type
// Useful in a generic base repository to avoid hardcoding the entity name
@Query("SELECT e FROM #{#entityName} e WHERE e.active = true")
List<T> findAllActive();

// Bind a property of a parameter object using SpEL
@Query("SELECT o FROM Order o WHERE o.customer.id = :#{#customer.id}")
List<Order> findByCustomerObject(@Param("customer") Customer customer);
```

### Specification API (Criteria Queries)

For fully dynamic WHERE clauses (e.g., optional filters based on which fields the user provides), use `JpaSpecificationExecutor`:

```java
public interface OrderRepository extends JpaRepository<Order, Long>,
                                          JpaSpecificationExecutor<Order> { }

// Build specifications compositely
public class OrderSpecifications {

    public static Specification<Order> hasStatus(String status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Order> amountGreaterThan(BigDecimal min) {
        return (root, query, cb) ->
                min == null ? null : cb.greaterThan(root.get("amount"), min);
    }
}

// Usage
Specification<Order> spec = where(hasStatus("PENDING"))
        .and(amountGreaterThan(new BigDecimal("100")));
List<Order> orders = orderRepository.findAll(spec);
```

## Gotchas

A `@Query` JPQL query that JOINs a collection association without `FETCH` causes N+1 queries — the associated collection is not loaded with the main query and is fetched separately for each returned entity when accessed. Always use `JOIN FETCH` or a DTO projection for queries that need associated data.

`@Modifying` without `clearAutomatically = true` leaves the persistence context in a stale state if the bulk update modifies entities that are already loaded in that context. Subsequent `findById()` calls will return the pre-update state from the first-level cache. Set `clearAutomatically = true` or call `EntityManager.clear()` after bulk operations that affect entities likely to be in the current context.

Named parameters (`:paramName`) require either the `-parameters` compiler flag or explicit `@Param` annotations. Spring Boot enables `-parameters` via its Maven plugin configuration, so `@Param` is technically optional in Spring Boot projects. When the repository is used in a module that does not use Spring Boot's compiler configuration, the missing `@Param` annotations cause `QueryCreationException` at startup.

A native `@Query` with `nativeQuery = true` is not validated at startup — syntax errors surface only at runtime when the query is executed. JPQL queries on the other hand are validated at application startup by the JPA provider. Use integration tests that exercise native queries to catch SQL syntax errors early.

`countQuery` in `@Query` for `Page<T>` return types must return a single scalar value (the count). A count query that uses a `DISTINCT` or `GROUP BY` may produce multiple rows, causing `IncorrectResultSizeDataAccessException`. Write the count query explicitly when pagination is needed for queries with aggregation.
