# Auditing

## Overview

Spring Data JPA's auditing support automatically populates `@CreatedDate`, `@LastModifiedDate`, `@CreatedBy`, and `@LastModifiedBy` fields on entities when they are persisted or updated. This eliminates the boilerplate of manually setting timestamps and audit user references in service methods, and ensures that the audit information is captured consistently regardless of which code path creates or modifies an entity.

The auditing infrastructure is activated by `@EnableJpaAuditing` on a `@Configuration` class. Once enabled, Spring Data registers a `AuditingEntityListener` (declared via `@EntityListeners`) on entities that use the auditing annotations. The listener is a JPA `EntityListener` — a callback class that Spring Data integrates with the JPA lifecycle. On `@PrePersist` and `@PreUpdate` events, the listener sets the annotated fields.

Date/time fields are populated from the framework's clock, which defaults to `Clock.systemUTC()`. The clock can be replaced with a custom bean for testing — providing a fixed clock makes time-dependent tests deterministic without mocking the entire auditing infrastructure.

The `@CreatedBy` and `@LastModifiedBy` fields require an `AuditorAware<T>` bean that returns the current user. The type parameter `T` is the type of the principal identifier stored — typically `String` for a username or `Long` for a user ID. In a Spring Security application, `AuditorAware` reads from `SecurityContextHolder`. The implementation must handle the case where no user is authenticated (e.g., during application startup or background jobs), returning `Optional.empty()` to suppress auditing for those events.

## Key Concepts

### Enabling Auditing

```java
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        // Reads the current user from Spring Security's authentication context.
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getName);
    }
}
```

`auditorAwareRef` links the annotation to the `AuditorAware` bean by name.

### Auditing Annotations on Entities

```java
@Entity
@EntityListeners(AuditingEntityListener.class)  // registers the JPA callback
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private BigDecimal price;

    // Set on first persist, never updated
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Updated on every save
    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Username of the user who created the entity
    @CreatedBy
    @Column(updatable = false)
    private String createdBy;

    // Username of the user who last modified the entity
    @LastModifiedBy
    private String lastModifiedBy;
}
```

### @MappedSuperclass for Shared Auditing Fields

Rather than repeating auditing annotations on every entity, extract them into a mapped superclass:

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @CreatedBy
    @Column(updatable = false, length = 100)
    private String createdBy;

    @LastModifiedBy
    @Column(length = 100)
    private String lastModifiedBy;

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public String getLastModifiedBy() { return lastModifiedBy; }
}

// All entities extending this class get auditing for free
@Entity
public class Order extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // ...
}
```

### Custom @EntityListeners

For logic beyond what the standard auditing annotations provide — for example, writing audit records to a separate `audit_log` table rather than fields on the entity — implement a custom `EntityListener`:

```java
public class AuditLogEntityListener {

    // @PostPersist, @PostUpdate, @PostRemove receive the entity after the operation completes.
    // The EntityManager context may be closed — avoid lazy-loading in these callbacks.
    @PostPersist
    public void onPersist(Object entity) {
        // In a real implementation, publish an event or write to an audit table.
        // Do not inject beans directly into an EntityListener — use CDI or SpringBeanAutowiringSupport.
        AuditEventPublisher.publish("CREATED", entity);
    }

    @PostUpdate
    public void onUpdate(Object entity) {
        AuditEventPublisher.publish("UPDATED", entity);
    }

    @PostRemove
    public void onRemove(Object entity) {
        AuditEventPublisher.publish("DELETED", entity);
    }
}

@Entity
@EntityListeners({AuditingEntityListener.class, AuditLogEntityListener.class})
public class SensitiveResource extends AuditableEntity { ... }
```

### Testing with a Fixed Clock

```java
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "fixedDateTimeProvider")
public class TestJpaConfig {

    private static final LocalDateTime FIXED_NOW =
            LocalDateTime.of(2025, 1, 15, 12, 0, 0);

    @Bean
    public DateTimeProvider fixedDateTimeProvider() {
        // Returns a fixed time — makes audited timestamps deterministic in tests
        return () -> Optional.of(FIXED_NOW);
    }
}
```

## Gotchas

`@EnableJpaAuditing` must be on a `@Configuration` class that is loaded before any JPA entities are used. In Spring Boot, placing it on the main application class or a dedicated JPA configuration class ensures it is loaded in the correct phase. Placing it in a `@SpringBootTest` test configuration can interfere with the production auditing configuration.

`@EntityListeners(AuditingEntityListener.class)` must be present on every entity (or on a `@MappedSuperclass`) that uses auditing annotations. The annotations alone (`@CreatedDate`, etc.) have no effect without the listener. This is a common source of confusion when auditing appears to be configured correctly but fields remain null.

`@CreatedBy` and `@LastModifiedBy` fields are only populated if `AuditorAware.getCurrentAuditor()` returns a non-empty `Optional`. If the method returns `Optional.empty()` (e.g., when called during application startup or from a background job without an authentication context), the fields are left null. This can cause `NOT NULL` constraint violations if the column is marked `nullable = false`. Make the column nullable or ensure all write paths have an authentication context.

The auditing listener populates fields using reflection. Fields marked `final` cannot be populated — the listener silently skips them. Do not declare auditing fields as `final`.

When using `@Modifying @Query` bulk updates, JPA lifecycle callbacks (and therefore entity listeners) are NOT triggered. The `@LastModifiedDate` and `@LastModifiedBy` fields of modified entities will not be updated by the auditing infrastructure for bulk updates. This requires either manual timestamp updates in the `@Query` itself or post-update compensation logic.
