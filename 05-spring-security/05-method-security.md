# Method Security

## Overview

Method security extends Spring Security's authorization model from HTTP request matching to individual method invocations. Rather than only controlling access at the URL level, method security allows annotations directly on service methods, repository methods, or any Spring-managed bean method. This enables fine-grained access control that would be impractical to express as URL patterns — for example, "a user can access their own account data but not other users'."

Method security is enabled by `@EnableMethodSecurity` on a `@Configuration` class. This annotation replaces the older `@EnableGlobalMethodSecurity` and activates all four annotation types by default: `@PreAuthorize`, `@PostAuthorize`, `@PreFilter`, and `@PostFilter`. The implementation is AOP-based: Spring wraps the annotated bean in a proxy that evaluates the SpEL expression before or after the method invocation and throws `AccessDeniedException` if access is denied.

`@PreAuthorize` is the most commonly used annotation. It evaluates a SpEL expression before the method runs, preventing execution if access is denied. `@PostAuthorize` evaluates after the method returns and can access the return value via the `returnObject` SpEL variable — useful for ownership checks that depend on the returned entity. `@PreFilter` and `@PostFilter` filter collection parameters or return values rather than allowing or denying the entire invocation.

Custom SpEL security expressions allow encapsulation of complex, reusable authorization logic. A custom expression is implemented as a Spring bean with methods that take the `Authentication` and relevant parameters, and the bean is referenced in SpEL via its bean name with the `@` prefix.

## Key Concepts

### @EnableMethodSecurity

```java
@Configuration
@EnableMethodSecurity  // activates @PreAuthorize, @PostAuthorize, @PreFilter, @PostFilter
public class SecurityConfig {
    // By default, @Secured and @RolesAllowed are not enabled.
    // Enable them with: @EnableMethodSecurity(securedEnabled = true, jsr250Enabled = true)
}
```

### @PreAuthorize

```java
@Service
public class OrderService {

    // Simple role check
    @PreAuthorize("hasRole('ADMIN')")
    public List<Order> findAll() { ... }

    // Access method parameter via SpEL — #customerId binds to the parameter named customerId
    @PreAuthorize("hasRole('ADMIN') or #customerId == authentication.name")
    public List<Order> findByCustomer(String customerId) { ... }

    // Combined conditions
    @PreAuthorize("isAuthenticated() and hasAuthority('ORDER_WRITE')")
    public Order create(OrderRequest request) { ... }

    // Delegate to a custom security bean registered in the Spring context
    @PreAuthorize("@orderSecurity.canAccess(authentication, #orderId)")
    public Order findById(String orderId) { ... }
}
```

### @PostAuthorize

```java
@Service
public class DocumentService {

    // returnObject is the method's return value — evaluated AFTER the method runs.
    // The method executes regardless; if the expression is false, AccessDeniedException is thrown.
    // Use this when ownership can only be determined from the returned entity.
    @PostAuthorize("returnObject.ownerId == authentication.name or hasRole('ADMIN')")
    public Document findById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + id));
    }
}
```

`@PostAuthorize` is appropriate when the ownership check requires data from the returned object (e.g., `ownerId`), which is not available before the method runs. The method always executes, so use it only when a database read for a non-owner is acceptable.

### @PreFilter and @PostFilter

```java
@Service
public class FileService {

    // @PreFilter evaluates an expression for each element of the collection parameter.
    // Elements for which the expression is false are removed before the method runs.
    // The parameter must be a Collection (not an array).
    @PreFilter("filterObject.ownerId == authentication.name")
    public void processFiles(List<File> files) {
        // files contains only the caller's own files
    }

    // @PostFilter evaluates for each element of the returned collection.
    // Elements for which the expression is false are removed from the returned list.
    @PostFilter("filterObject.ownerId == authentication.name or hasRole('ADMIN')")
    public List<File> listFiles() {
        return fileRepository.findAll();  // may return all files; filter trims the result
    }
}
```

`@PostFilter` on a method that returns a large collection is inefficient: the database loads all rows and then the security layer discards most of them. For large datasets, prefer a `@PreAuthorize`-guarded service that fetches only the caller's data at the query level.

### Custom Security Expressions

```java
// Custom security bean — accessed in SpEL via @beanName.method(args)
@Component("orderSecurity")
public class OrderSecurityService {

    private final OrderRepository orderRepository;

    public OrderSecurityService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public boolean canAccess(Authentication authentication, String orderId) {
        if (authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return true;
        }
        return orderRepository.existsByIdAndCustomerId(orderId, authentication.getName());
    }
}

// Usage in annotations:
@PreAuthorize("@orderSecurity.canAccess(authentication, #orderId)")
public Order getOrder(String orderId) { ... }
```

### Meta-Annotations for Reusable Security Policies

```java
// Define a reusable security annotation to avoid repeating the same SpEL expression
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ADMIN')")
public @interface AdminOnly {}

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("isAuthenticated()")
public @interface LoginRequired {}

// Clean application code
@Service
public class UserAdminService {

    @AdminOnly
    public void deleteUser(Long userId) { ... }

    @LoginRequired
    public UserProfile getProfile() { ... }
}
```

## Gotchas

Method security is implemented via AOP proxies, so the self-invocation problem applies. A method within the same class calling another `@PreAuthorize`-annotated method in the same class bypasses the proxy and the security check. Move the called method to a different bean if the annotation must be enforced.

`@PostAuthorize` runs the method body before evaluating the security expression. If the method has side effects (writes to the database, sends a notification), those side effects occur even if `@PostAuthorize` subsequently denies access and throws `AccessDeniedException`. Use `@PreAuthorize` whenever possible; reserve `@PostAuthorize` for purely read-based ownership verification.

`@PreFilter` removes elements from the input collection silently — the caller does not receive any indication that some elements were filtered out. This can cause confusion when the caller expects all supplied elements to be processed. Document the filtering behavior explicitly in the method contract.

When `@EnableMethodSecurity` is used without any parameters, it creates a `MethodSecurityInterceptor` bean that runs for all `@PreAuthorize` and related annotations. If a second `@EnableMethodSecurity` annotation is present in a child context (e.g., in a `@WebMvcTest` slice), two `MethodSecurityInterceptor` instances may be created and annotations may be evaluated twice. Use a single `@EnableMethodSecurity` in the production configuration.

SpEL expressions in `@PreAuthorize` are not validated at compile time. A typo in a method name or a wrong variable reference silently fails at runtime with `EvaluationException`. Test security expressions with integration tests that exercise both the permitted and denied paths.
