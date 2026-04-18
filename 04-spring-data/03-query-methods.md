# Query Methods

## Overview

Spring Data derives JPQL queries from repository method names by parsing them according to a well-defined grammar. The method name is split into a subject (what to select) and a predicate (the WHERE clause), and the result is mapped to the declared return type. This approach eliminates boilerplate JPQL for common queries while keeping the query visible in the method signature.

The parsing engine processes method names left to right. It first identifies the subject keyword (`find`, `get`, `read`, `count`, `exists`, `delete`, `remove`), then the optional limiting expression (`First`, `Top`, `Distinct`), then the entity name (optional, used to verify the type), then the property traversal that forms the WHERE clause. Property traversal follows the entity's field names, with relationship navigation separated by `And` and `Or` conjunctions.

The return type of a query method signals both the cardinality and the type of the result. Returning `Optional<T>` for a single-result query causes an exception if more than one result is found; returning `T` does the same but additionally throws if no result is found. Returning `List<T>` returns an empty list for no results. Returning `Page<T>` or `Slice<T>` with a `Pageable` parameter enables pagination. Returning `Stream<T>` enables lazy result streaming within a transaction.

The derived query approach has limits. Complex queries involving aggregate functions, subselects, `HAVING` clauses, or non-trivial join conditions are better expressed with `@Query`. Method-name-derived queries become unreadable when the predicate involves more than three or four conditions. Use derivation for simple, common queries and `@Query` for complex ones.

## Key Concepts

### Subject Keywords

| Subject keyword | Behavior |
|---|---|
| `findBy`, `getBy`, `readBy`, `queryBy` | SELECT — returns entities |
| `countBy` | SELECT COUNT(*) — returns `long` |
| `existsBy` | SELECT COUNT(*) > 0 — returns `boolean` |
| `deleteBy`, `removeBy` | DELETE — returns `void` or `long` (count) |
| `findFirst{N}By`, `findTop{N}By` | SELECT with `LIMIT N` |
| `findDistinctBy` | SELECT DISTINCT |

### Predicate Keywords

```java
public interface OrderRepository extends JpaRepository<Order, Long> {

    // Equal (default when no keyword follows property name)
    List<Order> findByStatus(String status);

    // Comparison operators
    List<Order> findByAmountGreaterThan(BigDecimal amount);
    List<Order> findByAmountBetween(BigDecimal min, BigDecimal max);
    List<Order> findByCreatedAtAfter(LocalDateTime since);

    // Null checks
    List<Order> findByShippedAtIsNull();
    List<Order> findByShippedAtIsNotNull();

    // String operations
    List<Order> findByCustomerNameContaining(String fragment);        // LIKE %fragment%
    List<Order> findByCustomerNameStartingWith(String prefix);        // LIKE prefix%
    List<Order> findByCustomerNameIgnoreCase(String name);            // LOWER comparison

    // Collection membership
    List<Order> findByStatusIn(Collection<String> statuses);          // IN (...)
    List<Order> findByStatusNotIn(Collection<String> statuses);

    // Boolean
    List<Order> findByActiveTrue();
    List<Order> findByActiveFalse();

    // Compound predicates
    List<Order> findByStatusAndCustomerId(String status, Long customerId);
    List<Order> findByStatusOrPriority(String status, int priority);

    // Negation
    List<Order> findByStatusNot(String status);

    // Relationship traversal (Order has a Customer with a country field)
    List<Order> findByCustomer_Country(String country);
}
```

### Limiting Results

```java
// First/Top without a number: equivalent to Top1
Optional<Order> findFirstByStatusOrderByCreatedAtDesc(String status);

// Top N: the most recent 5 pending orders
List<Order> findTop5ByStatusOrderByCreatedAtDesc(String status);

// Pageable combines limiting and sorting — works with any query method
Page<Order> findByStatus(String status, Pageable pageable);
```

### Return Types

| Return type | Behavior when zero results | Behavior when multiple results |
|---|---|---|
| `T` | `EmptyResultDataAccessException` | `IncorrectResultSizeDataAccessException` |
| `Optional<T>` | `Optional.empty()` | `IncorrectResultSizeDataAccessException` |
| `List<T>` | empty list | all results |
| `Page<T>` | empty page | paginated results |
| `Slice<T>` | empty slice | slice (knows if more pages exist) |
| `Stream<T>` | empty stream | lazy stream |

`Page<T>` executes two queries: the content query and a `COUNT` query to compute total elements. `Slice<T>` executes one query with `LIMIT + 1` to determine whether another page exists, without a `COUNT`. Use `Slice` when the total count is not needed, to avoid the expensive `COUNT` query.

```java
// Pageable usage
Page<Order> page = orderRepository.findByStatus(
        "PENDING",
        PageRequest.of(0, 20, Sort.by("createdAt").descending()));

page.getContent();       // List<Order> for this page
page.getTotalElements(); // total count (from COUNT query)
page.getTotalPages();    // total pages
page.hasNext();          // is there a next page?
```

### Sorting

```java
// Static sort in method name
List<Order> findByStatusOrderByCreatedAtDescAmountAsc(String status);

// Dynamic sort via Sort parameter
List<Order> findByStatus(String status, Sort sort);

// Usage:
Sort sort = Sort.by(
    Sort.Order.desc("createdAt"),
    Sort.Order.asc("id")
);
List<Order> orders = orderRepository.findByStatus("PENDING", sort);
```

### Stream Return Type

```java
// Stream must be consumed within the originating transaction
@Transactional(readOnly = true)
public void processAllOrders() {
    try (Stream<Order> stream = orderRepository.findAllByStatus("PENDING")) {
        stream.forEach(order -> processOrder(order));
    }
    // Stream is closed by the try-with-resources — the underlying cursor is closed
}
```

`Stream<T>` is backed by a scrollable cursor, which means the entire result set is not loaded into memory at once. It must be closed to release the cursor. Running a stream query outside a transaction causes the cursor to be closed prematurely when Hibernate's connection handling returns the connection to the pool.

## Gotchas

Property path parsing in derived query methods uses the entity's field names, not database column names. If an entity has `private String customerId` (mapped to `customer_id` column), the method must use `findByCustomerId`, not `findByCustomer_id`. Underscores in method names are used as explicit path separators for relationship traversal: `findByCustomer_Id` traverses the `customer` relationship and then accesses the `id` field.

When a method name is ambiguous — for example, `findByCustomerIdAndStatus` where the entity has both a `customerId` field and a `customer.id` relationship — Spring Data applies a greedy matching algorithm. It tries to match the longest property prefix first. Use underscore separators to disambiguate: `findByCustomer_IdAndStatus`.

`Page<T>` queries issue a `COUNT` query whose semantics match the content query but without `ORDER BY`. If the content query uses a complex join or subselect, the generated `COUNT` query may be inaccurate or inefficient. In such cases, provide a custom `countQuery` in a `@Query` annotation alongside the main query.

`existsBy` methods translate to `SELECT COUNT(*) > 0`, which is often less efficient than `SELECT 1 ... LIMIT 1`. Hibernate does not optimize `COUNT` for existence checks. For high-frequency existence checks, consider a `@Query` with `SELECT CASE WHEN COUNT(e.id) > 0 THEN true ELSE false END`.

Derived `deleteBy` methods are not always a single `DELETE` statement. Spring Data issues a `findBy` query first to load the matching entities, then calls `delete()` on each one individually (to trigger JPA lifecycle callbacks). This is N+1 deletes. Use `@Modifying @Query("DELETE FROM Order o WHERE ...")` for a single bulk delete when lifecycle callbacks are not needed.
