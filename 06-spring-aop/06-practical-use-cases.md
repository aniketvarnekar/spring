# Practical Use Cases

## Overview

Spring AOP provides the implementation substrate for most of Spring's declarative features: `@Transactional`, `@Cacheable`, `@Async`, Spring Security's `@PreAuthorize`. Beyond these built-in uses, application-level aspects are most effective for concerns that are genuinely cross-cutting — where the same behavior must apply to many methods across many classes without being expressed in each one.

The most valuable application-level AOP use cases are: structured logging with correlation context, metrics and timing, retry logic for transient failures, and custom enforcement of architectural rules via custom annotation pointcuts. Each of these would otherwise require repetitive, error-prone boilerplate in every method.

## Key Concepts

### Structured Logging Aspect

```java
/*
 * Logs method entry/exit with class name, method name, duration, and outcome.
 * Applies to all methods in any class annotated @Service, @Repository, or @RestController.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class StructuredLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(StructuredLoggingAspect.class);

    @Around("@within(org.springframework.stereotype.Service) || "
          + "@within(org.springframework.stereotype.Repository) || "
          + "@within(org.springframework.web.bind.annotation.RestController)")
    public Object logInvocation(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().toShortString();
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            log.debug("OK  {} [{} ms]", method, elapsedMs(start));
            return result;
        } catch (Throwable ex) {
            log.warn("ERR {} [{} ms] {}", method, elapsedMs(start), ex.getMessage());
            throw ex;
        }
    }

    private long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000;
    }
}
```

### Metrics Aspect with Micrometer

```java
@Aspect
@Component
public class MetricsAspect {

    private final MeterRegistry registry;

    public MetricsAspect(MeterRegistry registry) {
        this.registry = registry;
    }

    @Around("@within(org.springframework.stereotype.Service)")
    public Object recordTimer(ProceedingJoinPoint pjp) throws Throwable {
        String className  = pjp.getTarget().getClass().getSimpleName();
        String methodName = pjp.getSignature().getName();
        Timer.Sample sample = Timer.start(registry);
        try {
            Object result = pjp.proceed();
            sample.stop(Timer.builder("service.method.duration")
                    .tag("class",  className)
                    .tag("method", methodName)
                    .tag("outcome", "success")
                    .register(registry));
            return result;
        } catch (Throwable ex) {
            sample.stop(Timer.builder("service.method.duration")
                    .tag("class",  className)
                    .tag("method", methodName)
                    .tag("outcome", "error")
                    .register(registry));
            throw ex;
        }
    }
}
```

### Retry Aspect

```java
// Custom annotation marking methods that should be retried on transient failures.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Retryable {
    int maxAttempts() default 3;
    long backoffMs()  default 500;
    Class<? extends Exception>[] on() default {Exception.class};
}

@Aspect
@Component
public class RetryAspect {

    private static final Logger log = LoggerFactory.getLogger(RetryAspect.class);

    @Around("@annotation(retryable)")
    public Object retry(ProceedingJoinPoint pjp, Retryable retryable) throws Throwable {
        int maxAttempts = retryable.maxAttempts();
        long backoffMs  = retryable.backoffMs();
        Set<Class<? extends Exception>> retryOn = Set.of(retryable.on());

        Throwable lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return pjp.proceed();
            } catch (Throwable ex) {
                if (!isRetryable(ex, retryOn)) {
                    throw ex;
                }
                lastException = ex;
                if (attempt < maxAttempts) {
                    log.warn("Attempt {}/{} failed for {}: {}. Retrying in {} ms.",
                             attempt, maxAttempts, pjp.getSignature().toShortString(),
                             ex.getMessage(), backoffMs);
                    sleep(backoffMs * attempt); // exponential-like back-off
                }
            }
        }
        throw lastException;
    }

    private boolean isRetryable(Throwable ex, Set<Class<? extends Exception>> retryOn) {
        return retryOn.stream().anyMatch(type -> type.isInstance(ex));
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

// Usage:
@Service
public class ExternalApiService {

    @Retryable(maxAttempts = 3, backoffMs = 200, on = {IOException.class, HttpTimeoutException.class})
    public ApiResponse callApi(String endpoint) throws IOException {
        // ...
    }
}
```

### Audit Logging with Custom Annotation

```java
// Annotation captures the action name and whether arguments should be logged.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String action();
    boolean logArgs() default false;
}

@Aspect
@Component
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;

    public AuditAspect(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint pjp, Audited audited) throws Throwable {
        String principal = resolveCurrentUser();
        String args = audited.logArgs() ? Arrays.toString(pjp.getArgs()) : "[redacted]";

        try {
            Object result = pjp.proceed();
            auditLogRepository.save(new AuditEntry(principal, audited.action(), args, "SUCCESS"));
            return result;
        } catch (Throwable ex) {
            auditLogRepository.save(new AuditEntry(principal, audited.action(), args, "FAILURE: " + ex.getMessage()));
            throw ex;
        }
    }

    private String resolveCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonymous";
    }
}

// Usage:
@Service
public class OrderService {

    @Audited(action = "CREATE_ORDER", logArgs = true)
    @Transactional
    public Order createOrder(OrderRequest request) { ... }

    @Audited(action = "CANCEL_ORDER")
    @Transactional
    public void cancelOrder(Long orderId) { ... }
}
```

### Enforcing Architectural Layer Rules

```java
// Prevent repository methods from being called directly from controllers
// (they should always go through a service).
@Aspect
@Component
public class ArchitectureEnforcementAspect {

    @Before("within(com.example.web..*) && "
          + "execution(* com.example.repository..*(..))")
    public void preventDirectRepositoryAccess(JoinPoint jp) {
        throw new IllegalStateException(
            "Architectural violation: controller called repository directly. "
            + "Offending call: " + jp.getSignature());
    }
}
```

### Caching Aspect Pattern

```java
// Simple map-based caching to illustrate the pattern.
// In production, use Spring's @Cacheable with a proper CacheManager.
@Aspect
@Component
public class SimpleCacheAspect {

    private final Map<String, Object> cache = new ConcurrentHashMap<>();

    @Around("@annotation(com.example.annotation.Cached)")
    public Object cached(ProceedingJoinPoint pjp) throws Throwable {
        String key = pjp.getSignature().toShortString()
                + ":" + Arrays.toString(pjp.getArgs());
        return cache.computeIfAbsent(key, k -> {
            try {
                return pjp.proceed();
            } catch (Throwable ex) {
                throw new RuntimeException(ex);
            }
        });
    }
}
```

## Gotchas

**AOP is not a substitute for unit testing.** Cross-cutting concerns implemented in aspects must be tested by verifying their effect on the intercepted method — an aspect that silently fails (e.g., due to a pointcut expression that matches nothing) is undetectable without integration tests that exercise both the permitted and the denied paths.

**Avoid business logic in aspects.** An aspect should implement a pure cross-cutting concern. If an aspect needs to know about domain rules (e.g., "only audit order creation, not reads"), that logic belongs in the service or in annotation attributes, not hardcoded in the aspect.

**Aspect over-reach.** A pointcut like `execution(* com.example..*(..))` matches every method in the application, including getters, `equals`, `hashCode`, `toString`, and initialization methods. This can be expensive and produce unexpected behavior. Scope pointcuts carefully using `within`, package patterns, or custom annotations.

**Retry and transactions.** A retry aspect that wraps a `@Transactional` method must run outside the transaction (lower order number = higher precedence = outer wrapper). If the retry aspect is inside the transaction, each retry attempt runs in the same doomed transaction and the retry is futile. Set the retry aspect's `@Order` lower than `@EnableTransactionManagement`'s order.

**Thread-local state in aspects.** Aspects that store state in instance variables are shared across all concurrent invocations (the aspect is a singleton). Use `ThreadLocal` for invocation-scoped state, or accept `JoinPoint` parameters instead.
