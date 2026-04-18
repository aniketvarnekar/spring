# Repository Abstraction

## Overview

Spring Data's repository abstraction eliminates boilerplate persistence code by generating implementations at runtime from interface declarations. The programmer declares what queries are needed in the form of a typed interface extending one of Spring Data's marker interfaces, and the framework generates a proxy-based implementation that translates method names, `@Query` annotations, and parameter types into the appropriate store-specific operations.

The repository hierarchy forms a capability ladder. `Repository<T, ID>` is the root marker interface with no methods — it exists solely to mark an interface for Spring Data processing. `CrudRepository<T, ID>` adds save, find, and delete operations. `PagingAndSortingRepository<T, ID>` adds pagination and sorting. `JpaRepository<T, ID>` adds JPA-specific operations including batch deletes, flush control, and entity manager access. Most applications extend `JpaRepository` for the full feature set.

The implementation behind a JPA repository is `SimpleJpaRepository`, which implements all repository interfaces and delegates to an `EntityManager`. The `RepositoryFactoryBean` is the bridge between Spring's bean factory and the repository infrastructure: it creates a proxy for each repository interface, discovers the appropriate `RepositoryFactory` (in this case `JpaRepositoryFactory`), and registers the resulting implementation as a Spring bean.

Custom repository behavior — logic that cannot be expressed through method name derivation or `@Query` annotations — is added through repository fragments: additional interfaces with their own implementations that are merged into the final repository proxy. This fragment pattern keeps the interface clean while allowing arbitrary imperative logic.

## Key Concepts

### Repository Hierarchy

```
Repository<T, ID>                         — marker interface
  └── CrudRepository<T, ID>               — save, findById, findAll, count, delete, exists
        └── PagingAndSortingRepository    — findAll(Sort), findAll(Pageable)
              └── JpaRepository<T, ID>    — flush, saveAndFlush, deleteAllInBatch,
                                            getById (reference without loading)
```

| Interface | Use When |
|---|---|
| `CrudRepository` | You need only basic CRUD; the persistence store is not JPA |
| `PagingAndSortingRepository` | You need paginated or sorted queries over CRUD |
| `JpaRepository` | You use JPA and want the full feature set (flush control, batch ops) |

For reactive stores, use `ReactiveCrudRepository`. For MongoDB, use `MongoRepository`. The pattern is the same across Spring Data modules; only the leaf interface and the underlying store differ.

### How RepositoryFactoryBean Works

```
ApplicationContext bootstrap
  │
  ├── @EnableJpaRepositories scans for interfaces extending Repository<T, ID>
  │
  └── For each interface:
        JpaRepositoryFactoryBean.afterPropertiesSet()
          ├── Creates JpaRepositoryFactory
          ├── Determines the entity class and ID type from the generic parameters
          ├── Instantiates SimpleJpaRepository as the base implementation
          ├── Discovers custom repository fragments (interfaces with matching Impl classes)
          ├── Discovers @Query methods and generates query executors
          ├── Creates a JDK dynamic proxy implementing the repository interface
          └── Registers the proxy as a singleton bean
```

The proxy dispatches each method call to the appropriate executor: the base `SimpleJpaRepository` for standard CRUD, a fragment implementation for custom methods, or a generated query executor for derived queries and `@Query` methods.

### Custom Repository Fragments

```java
// Define the custom capability as an interface
public interface OrderRepositoryCustom {
    List<Order> findOverdueOrders(Duration olderThan);
}

// Provide the implementation — class name must be the interface name + "Impl"
// OR configure repositoryImplementationPostfix in @EnableJpaRepositories
@Repository
public class OrderRepositoryCustomImpl implements OrderRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<Order> findOverdueOrders(Duration olderThan) {
        LocalDateTime cutoff = LocalDateTime.now().minus(olderThan);
        return em.createQuery(
                "SELECT o FROM Order o WHERE o.status = 'PENDING' AND o.createdAt < :cutoff",
                Order.class)
                .setParameter("cutoff", cutoff)
                .getResultList();
    }
}

// Extend both JpaRepository and the custom interface
public interface OrderRepository
        extends JpaRepository<Order, Long>, OrderRepositoryCustom {
    // standard + custom methods combined in one interface
}
```

### @EnableJpaRepositories

```java
@Configuration
@EnableJpaRepositories(
    basePackages = "com.example.order.repository",
    // Custom suffix for Impl classes — default is "Impl"
    repositoryImplementationPostfix = "Impl",
    // Use a specific EntityManagerFactory when multiple are present
    entityManagerFactoryRef = "orderEntityManagerFactory",
    transactionManagerRef = "orderTransactionManager"
)
public class JpaConfig { }
```

Spring Boot auto-configures `@EnableJpaRepositories` for the package of the `@SpringBootApplication` class when `spring-boot-starter-data-jpa` is on the classpath.

## Gotchas

The `getById(ID)` method on `JpaRepository` returns an entity proxy that is loaded lazily. If the entity does not exist, no exception is thrown until the proxy's properties are accessed, at which point `EntityNotFoundException` is thrown. Use `findById(ID)` (returns `Optional<T>`) for existence-checked fetching.

The `save(entity)` method on `SimpleJpaRepository` checks whether the entity is new (via `isNew()`) and calls either `persist` or `merge`. If the entity uses manually assigned IDs (rather than generated ones), `isNew()` returns `false` when the ID is set, causing an unnecessary `merge` even for new entities. Implement `Persistable<ID>` on the entity to provide a custom `isNew()` implementation.

Extending `JpaRepository` exposes the `deleteAll()` method, which deletes entities one by one (N DELETE statements). `deleteAllInBatch()` issues a single `DELETE FROM table` statement. The naming is confusing; always prefer `deleteAllInBatch()` when deleting all rows in a table.

`@EnableJpaRepositories` without a `basePackages` specification defaults to the package of the annotated class. If the configuration class is in a different package than the repository interfaces, the repositories are not discovered. Be explicit with `basePackages` in non-trivial project structures.

When multiple Spring Data modules are on the classpath (JPA + MongoDB, for example), Spring Data uses strict repository detection: a repository is assigned to the module whose store it is based on the entity type annotation (`@Entity` → JPA, `@Document` → MongoDB). Ensure entity types are annotated correctly to avoid a repository being assigned to the wrong store.
