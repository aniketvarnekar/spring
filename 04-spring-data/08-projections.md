# Projections

## Overview

A projection is a subset of an entity's data returned by a query. Instead of loading the full entity with all its columns and associations, a projection returns only the fields the caller needs. This reduces the amount of data transferred from the database, the amount of memory used in the JPA persistence context, and the risk of accidentally triggering lazy-loading chains.

Spring Data JPA supports three kinds of projections: interface-based projections (where the result is a proxy implementing a declared interface), class-based (DTO) projections (where the result is an instance of a specific class), and dynamic projections (where the caller determines at invocation time which projection type to use). Each has different trade-offs between flexibility, type safety, and what kinds of derived data they support.

Interface-based projections are the most concise. The framework generates a proxy at runtime that intercepts getter calls and returns the corresponding query column value. The interface can declare methods whose names map to entity field names, and it can also declare methods annotated with `@Value(SpEL)` to compute derived values from multiple fields. However, interface projections cannot be used as constructor-expression targets in JPQL.

Class-based DTO projections use a constructor expression in JPQL or a constructor with matching parameter types for derived queries. They are plain Java objects (records or classes) — no proxy is involved. They are more type-safe than interface projections because the mapping is through a constructor, and they avoid the proxy overhead. The trade-off is that they cannot use SpEL for derived fields without additional code.

## Key Concepts

### Interface-Based Projections

```java
// Declare only the fields needed by the caller
public interface OrderSummary {
    String getExternalRef();
    String getStatus();
    BigDecimal getAmount();

    // SpEL expression — computed from two fields without a separate query
    @Value("#{target.status + ' - ' + target.externalRef}")
    String getDisplayLabel();

    // Nested projection — projects a related entity without loading the full entity
    CustomerInfo getCustomer();

    interface CustomerInfo {
        String getName();
        String getEmail();
    }
}

// Repository method returns the projection type
public interface OrderRepository extends JpaRepository<Order, Long> {

    // Spring Data generates: SELECT o.external_ref, o.status, o.amount, c.name, c.email
    // FROM orders o JOIN customers c ON o.customer_id = c.id WHERE o.status = ?
    List<OrderSummary> findByStatus(String status);

    // Single result
    Optional<OrderSummary> findSummaryById(Long id);
}
```

### Class-Based (DTO) Projections

```java
// Record DTO — immutable, no boilerplate
public record OrderDto(String externalRef, String status, BigDecimal amount) {}

// Requires a JPQL constructor expression matching the record's canonical constructor
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT new com.example.dto.OrderDto(o.externalRef, o.status, o.amount) " +
           "FROM Order o WHERE o.status = :status")
    List<OrderDto> findDtosByStatus(@Param("status") String status);
}
```

For derived query methods (without `@Query`), class-based projections work if the class has a single constructor and the field types and names match:

```java
// Works when OrderDto has a constructor that matches (String, String, BigDecimal)
// and field names match the entity fields (Spring Data uses constructor parameter names)
List<OrderDto> findByCustomerId(Long customerId);
```

### Dynamic Projections

Dynamic projections allow the caller to specify the projection type at the call site, driven by a `Class<T>` parameter:

```java
public interface OrderRepository extends JpaRepository<Order, Long> {

    // The <T> type parameter and Class<T> allow the caller to specify the projection
    <T> List<T> findByStatus(String status, Class<T> type);
}

// Caller decides the projection at invocation time
List<OrderSummary> summaries = orderRepository.findByStatus("PENDING", OrderSummary.class);
List<OrderDto> dtos = orderRepository.findByStatus("PENDING", OrderDto.class);
List<Order> entities = orderRepository.findByStatus("PENDING", Order.class);
```

### Projection Comparison

| Characteristic | Interface projection | Class (DTO) projection | Entity |
|---|---|---|---|
| Overhead | Proxy per row | Plain object | Managed entity |
| SQL columns fetched | Only projected fields | Only constructor params | All mapped columns |
| SpEL derived fields | Yes (@Value) | No | N/A |
| Dirty checking | No | No | Yes |
| Nested projections | Yes | Via constructor | Via FETCH JOIN |
| Usable in JPQL `new` | No | Yes | Yes |
| Type safety | Interface contract | Constructor match | Full entity |

### @Value SpEL in Interface Projections

```java
public interface ProductSummary {
    String getName();
    BigDecimal getPrice();

    // target refers to the backing entity (or map) being proxied
    @Value("#{target.price * (1 - target.discountRate)}")
    BigDecimal getEffectivePrice();

    // Can invoke Spring beans registered in the context
    @Value("#{@currencyFormatter.format(target.price)}")
    String getFormattedPrice();
}
```

`@Value` expressions in projections receive `target` as the entity instance, which means they trigger loading the entity even when the other interface methods only need specific columns. If the entity is loaded anyway, there is no advantage over just loading the entity and computing the derived value in the calling code.

## Gotchas

Interface-based projections with `@Value(SpEL)` expressions cause the full entity to be loaded because the SpEL expression evaluates against the entity instance. If the goal is to reduce the data transferred from the database, `@Value`-annotated methods in interface projections defeat that purpose. Use class-based DTO projections with explicit JPQL constructor expressions for strict column reduction.

When an interface projection method references a relationship field (e.g., `getCustomer().getName()`), Spring Data JPA must join the related table to populate the projection. If the query does not include a JOIN, a secondary query is issued per projected row — the N+1 problem in a projection context. Inspect the generated SQL when using nested projections.

Dynamic projections require that all projection types used with the same repository method produce compatible SQL. If the interface projection fetches columns A, B, C and the DTO projection fetches A, B only, Spring Data may reuse the same query for both — which means the DTO receives values for B when it only needs A. Always test with actual SQL logging to verify the generated queries match expectations.

Class-based DTO projections in derived query methods (without `@Query`) rely on matching the constructor parameter names to the entity field names. Without the `-parameters` compiler flag (enabled by Spring Boot's Maven plugin), Kotlin-style parameter name matching is not available, and the order of constructor parameters is used instead. This can cause incorrect binding when parameter names and field names diverge.

A projection interface that extends another interface inherits all the methods of the parent. This enables reuse — a `DetailedOrderSummary` can extend `OrderSummary` and add methods. However, the SQL generated for the detailed interface may differ from the base interface, causing confusion when the same repository method is called with different projection types via dynamic projections.
