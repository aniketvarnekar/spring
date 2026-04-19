# Aspect Ordering

## Overview

When multiple aspects apply to the same join point, Spring AOP must determine the order in which their advice executes. The order matters because aspects form a nested wrapper structure around the target method: the outermost aspect's `@Around` advice runs first before `proceed()`, and last after `proceed()` returns. Getting the ordering wrong can cause subtle bugs — for example, running a caching aspect outside a transaction aspect means the cache is populated before the transaction commits.

Spring AOP uses the `org.springframework.core.Ordered` interface and the `@Order` annotation to control aspect ordering. Lower order values mean higher precedence (the aspect wraps on the outside). Aspects without an explicit order are treated as having the lowest precedence (`Integer.MAX_VALUE`).

## Key Concepts

### @Order on an Aspect

```java
// Order 1: outermost wrapper — runs first before proceed(), last after proceed().
@Aspect
@Component
@Order(1)
public class SecurityAspect {

    @Around("within(com.example.service..*)")
    public Object checkSecurity(ProceedingJoinPoint pjp) throws Throwable {
        // security check runs before transaction and caching
        return pjp.proceed();
    }
}

// Order 2: next layer inward
@Aspect
@Component
@Order(2)
public class TransactionAspect {
    // In practice, use @Transactional; this illustrates ordering.
    @Around("within(com.example.service..*)")
    public Object manageTransaction(ProceedingJoinPoint pjp) throws Throwable {
        return pjp.proceed();
    }
}

// Order 3: innermost wrapper — closest to the target method
@Aspect
@Component
@Order(3)
public class CachingAspect {
    @Around("within(com.example.service..*)")
    public Object cacheResult(ProceedingJoinPoint pjp) throws Throwable {
        return pjp.proceed();
    }
}
```

Call stack for a method that is covered by all three:

```text
SecurityAspect.around (before proceed)
  TransactionAspect.around (before proceed)
    CachingAspect.around (before proceed)
      target method
    CachingAspect.around (after proceed)
  TransactionAspect.around (after proceed)
SecurityAspect.around (after proceed)
```

### Implementing Ordered Interface

Alternative to `@Order`; useful when the order is computed rather than a compile-time constant.

```java
@Aspect
@Component
public class DynamicOrderAspect implements Ordered {

    @Override
    public int getOrder() {
        return 5; // could be read from configuration
    }

    @Around("within(com.example..*)")
    public Object advice(ProceedingJoinPoint pjp) throws Throwable {
        return pjp.proceed();
    }
}
```

### Advice Ordering Within a Single Aspect

When the same `@Aspect` class has multiple advice methods that match the same join point, the execution order between them is undefined in Spring AOP (AspectJ defines it, but Spring AOP does not guarantee it). If you need a specific order for advice methods that target the same join point, split them into separate aspects and apply `@Order`.

```java
// Problematic: order between these two @Before methods in the same aspect is undefined
@Aspect
@Component
public class DoubleBeforeAspect {

    @Before("within(com.example.service..*)")
    public void first() {}  // may run before or after second()

    @Before("within(com.example.service..*)")
    public void second() {} // order not guaranteed
}

// Better: use a single @Before that calls helpers in explicit order
@Aspect
@Component
public class OrderedBeforeAspect {

    @Before("within(com.example.service..*)")
    public void ordered(JoinPoint jp) {
        first(jp);
        second(jp);
    }
}
```

### Ordering with Spring's Built-in Aspects

Spring's built-in aspects (transaction management, caching, async, security) have defined order values. Key values:

| Aspect | Default order |
|---|---|
| `@EnableTransactionManagement` | `Integer.MAX_VALUE` (lowest precedence) unless `order` attribute is set |
| `@EnableCaching` | `Integer.MAX_VALUE` unless `order` attribute is set |
| `@EnableAsync` | `Integer.MAX_VALUE` unless `order` attribute is set |
| `@EnableMethodSecurity` | `100` by default |

To ensure a custom aspect runs inside (after) Spring's transaction aspect:

```java
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 1)  // just inside the transaction boundary
public class InsideTransactionAspect {
    // This aspect's @Around after-proceed block runs before the transaction commits.
}
```

To ensure it runs outside (before) the transaction:

```java
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)  // outside the transaction boundary
public class OutsideTransactionAspect {
    // This aspect commits as a whole within a wrapping transaction context.
}
```

### Advice Ordering Across Aspects for @Before and @After

For `@Before` advice: the aspect with the lowest order number (highest precedence) runs first.
For `@After` advice: the aspect with the lowest order number (highest precedence) runs last (it wraps the outermost layer, so its after-advice is the last to execute as the stack unwinds).

This is consistent with the wrapping model: order 1 is the outermost wrapper.

## Gotchas

**Default unordered aspects are unpredictable.** Two aspects without `@Order` run in an unspecified, non-deterministic order (JVM-dependent). Always apply `@Order` when more than one aspect targets the same join point and ordering is semantically significant.

**`@Order` on method-level advice is not supported.** `@Order` applies only to the aspect class, not to individual advice methods within an aspect. Advice method ordering within an aspect is undefined when multiple advice match the same join point.

**Transaction and caching order.** The default order for `@EnableTransactionManagement` is `Integer.MAX_VALUE`, making the transaction aspect the innermost wrapper by default. If a caching aspect also defaults to `Integer.MAX_VALUE`, the relative order between them is undefined. Explicitly set the order on `@EnableTransactionManagement(order = 200)` or `@EnableCaching(order = 100)` to fix the relationship.

**`@Order` does not affect which advice is chosen; only when it runs.** All matching advice from all aspects runs. `@Order` controls sequence, not eligibility. To skip an aspect conditionally, use a pointcut condition or check a flag inside the advice.
