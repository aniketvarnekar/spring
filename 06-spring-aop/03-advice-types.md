# Advice Types

## Overview

Advice is the code that runs at a matched join point. Spring AOP provides five advice types, each corresponding to a different position relative to the intercepted method invocation: before execution, after execution (regardless of outcome), after successful return, after throwing an exception, and around the entire invocation.

`@Around` is the most powerful and flexible advice type: it controls whether the method is invoked at all, can inspect and modify arguments and return values, and can catch or rethrow exceptions. All other advice types can be implemented as `@Around`, but the more specific types (`@Before`, `@AfterReturning`, `@AfterThrowing`, `@After`) are preferred when their semantics match the requirement, because they are clearer about intent and have simpler signatures.

## Key Concepts

### @Before

Runs before the method executes. Cannot prevent execution (throw an exception to abort). Does not have access to the return value.

```java
@Aspect
@Component
public class ValidationAspect {

    @Before("execution(* com.example.service.*.*(..)) && args(id,..)")
    public void validateId(JoinPoint jp, Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                "Invalid ID in " + jp.getSignature().toShortString() + ": " + id);
        }
    }
}
```

### @AfterReturning

Runs after the method returns normally (no exception). Can access the return value via `returning`, but cannot replace it (use `@Around` for that).

```java
@Aspect
@Component
public class CacheWarmingAspect {

    // "returning" binds the method's return value to the advice parameter.
    // The parameter type (Order) acts as an additional filter: the advice only fires
    // if the return value is assignable to Order.
    @AfterReturning(
        pointcut = "execution(* com.example.repository.OrderRepository.findById(..))",
        returning = "order")
    public void warmCache(Order order) {
        if (order != null) {
            // populate a second-level cache with the loaded entity
        }
    }
}
```

### @AfterThrowing

Runs only when the method throws an exception. Can bind the exception to an advice parameter. Cannot suppress the exception — it will propagate after the advice runs. Use `@Around` to catch and suppress exceptions.

```java
@Aspect
@Component
public class ErrorNotificationAspect {

    // "throwing" binds the thrown exception. The parameter type filters by exception type.
    @AfterThrowing(
        pointcut = "within(com.example.service..*)",
        throwing = "ex")
    public void onServiceException(JoinPoint jp, Exception ex) {
        // send alert, increment error counter, etc.
        // ex will propagate to the caller after this advice returns.
    }
}
```

### @After (finally)

Runs after the method completes, whether it returned normally or threw an exception. Equivalent to a `finally` block. Use for cleanup operations that must run regardless of outcome.

```java
@Aspect
@Component
public class ResourceCleanupAspect {

    @After("execution(* com.example.service.FileProcessingService.*(..))")
    public void cleanup(JoinPoint jp) {
        // release resources, clear thread-local state, etc.
        // runs whether or not the method threw
    }
}
```

### @Around

The most powerful advice type. Wraps the entire method invocation. Must call `pjp.proceed()` to invoke the target method (or skip it by not calling `proceed()`). Must return a value compatible with the method's return type.

```java
@Aspect
@Component
public class TimingAspect {

    private static final Logger log = LoggerFactory.getLogger(TimingAspect.class);

    @Around("within(com.example.service..*)")
    public Object timeServiceMethods(ProceedingJoinPoint pjp) throws Throwable {
        String name = pjp.getSignature().toShortString();
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed(); // invoke the target method
            long elapsed = System.nanoTime() - start;
            log.debug("{} completed in {} ms", name, elapsed / 1_000_000);
            return result;
        } catch (Throwable ex) {
            long elapsed = System.nanoTime() - start;
            log.warn("{} threw {} after {} ms", name, ex.getClass().getSimpleName(), elapsed / 1_000_000);
            throw ex; // re-throw to preserve original behavior
        }
    }
}
```

**Modifying arguments with proceed(Object[]):**

```java
@Around("execution(* com.example.service.OrderService.createOrder(..))")
public Object sanitizeInput(ProceedingJoinPoint pjp) throws Throwable {
    Object[] args = pjp.getArgs();
    if (args[0] instanceof OrderRequest request) {
        // replace the argument with a sanitized version
        args[0] = request.withCustomerId(request.customerId().strip());
    }
    return pjp.proceed(args); // proceed with modified arguments
}
```

**Replacing the return value:**

```java
@Around("execution(* com.example.repository.*.findById(..))")
public Object cacheAround(ProceedingJoinPoint pjp) throws Throwable {
    String cacheKey = buildKey(pjp);
    Object cached = cache.get(cacheKey);
    if (cached != null) {
        return cached;  // return cached value without calling the method at all
    }
    Object result = pjp.proceed();
    if (result != null) {
        cache.put(cacheKey, result);
    }
    return result;
}
```

### Advice Execution Order Within an Aspect

When multiple advice types are declared in the same `@Aspect` class and match the same join point, they execute in this order:

```
@Around (before proceed)
  → @Before
    → target method
  ← @AfterReturning or @AfterThrowing
← @After
← @Around (after proceed)
```

### Accessing Annotation Attributes in Advice

```java
// Custom annotation
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    int requestsPerSecond() default 10;
}

// Advice binds the annotation instance
@Aspect
@Component
public class RateLimitAspect {

    @Around("@annotation(rateLimit)")
    public Object enforceRateLimit(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        int limit = rateLimit.requestsPerSecond();
        // check and enforce rate limit
        return pjp.proceed();
    }
}
```

## Gotchas

**`@Around` must call `pjp.proceed()` or explicitly return a value.** If `proceed()` is never called, the target method is never executed. For `void` methods, the advice must return `null`. For non-void methods, returning a non-null value substitutes the actual return value, which may surprise the caller.

**Checked exception handling in `@Around`.** The signature `throws Throwable` is required on `@Around` advice because `proceed()` declares it. Do not swallow the exception unless deliberately implementing a recovery strategy.

**`@AfterReturning` receives a shallow reference to mutable objects.** If the advice modifies a returned mutable object, the modification is visible to the caller. This is rarely desired; document it clearly or use `@Around` with a defensive copy.

**`@After` fires before `@AfterReturning` and `@AfterThrowing` in AspectJ order rules.** The execution sequence for Spring AOP is: `@Around` before-proceed → `@Before` → target → (`@AfterReturning` or `@AfterThrowing`) → `@After` → `@Around` after-proceed. Do not depend on a specific ordering between `@After` and the return/throwing advisors — use `@Around` when ordering is critical.

**Void methods and `@AfterReturning`.** The `returning` attribute on `@AfterReturning` is not required. If omitted, the advice fires for all successful returns including `void`. If specified, the parameter type must match the actual return type; mismatched types cause the advice not to fire (not an error).
