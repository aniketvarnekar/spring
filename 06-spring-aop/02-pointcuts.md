# Pointcuts

## Overview

A pointcut is an expression that selects which method invocations an advice applies to. Spring AOP uses AspectJ's pointcut expression language. A pointcut can be defined inline within an advice annotation or declared separately as a `@Pointcut` method and referenced by name. Named pointcuts encourage reuse and make complex expressions composable.

The most important pointcut designators in Spring AOP are `execution`, `within`, `@annotation`, `@within`, and `bean`. Of these, `execution` is by far the most commonly used. Designators that are valid in AspectJ but not in Spring AOP (such as `call`, `initialization`, `preinitialization`, `staticinitialization`, `get`, `set`, `adviceexecution`, `withincode`, `cflow`, `cflowbelow`) will throw an `IllegalArgumentException` at context startup.

## Key Concepts

### execution

The primary pointcut designator. Selects method join points by return type, class, method name, and parameters.

```
execution(modifiers? return-type declaring-type? method-name(params) throws?)
```

Wildcards:
- `*` — matches any single type or any method name component
- `..` — in a type pattern, matches any number of package segments; in parameters, matches any number of arguments

```java
@Aspect
@Component
public class ExecutionExamples {

    // Any public method in any class
    @Pointcut("execution(public * *(..))")
    public void anyPublicMethod() {}

    // Any method in any class under com.example (any depth)
    @Pointcut("execution(* com.example..*(..))")
    public void anyMethodInPackage() {}

    // Any method in OrderService
    @Pointcut("execution(* com.example.service.OrderService.*(..))")
    public void anyOrderServiceMethod() {}

    // Only methods that return Order
    @Pointcut("execution(com.example.model.Order com.example..*(..))")
    public void methodsReturningOrder() {}

    // Method named "save" with any parameters in any class under com.example.repository
    @Pointcut("execution(* com.example.repository.*.save(..))")
    public void repositorySave() {}

    // Method with exactly one String parameter
    @Pointcut("execution(* com.example..*(String))")
    public void singleStringParam() {}
}
```

### within

Selects all join points in a given type or package. Simpler than `execution` when you want to intercept everything in a package without specifying return types or signatures.

```java
// All methods in any class in com.example.service or its sub-packages
@Pointcut("within(com.example.service..*)")
public void inServiceLayer() {}

// All methods in OrderService specifically
@Pointcut("within(com.example.service.OrderService)")
public void inOrderService() {}
```

### @annotation

Selects join points where the method itself is annotated with the specified annotation. The most common way to use custom annotations as pointcut targets.

```java
// All methods annotated with @Audited (regardless of class)
@Pointcut("@annotation(com.example.annotation.Audited)")
public void auditedMethods() {}

// Binding: retrieve the annotation instance inside the advice
@Before("@annotation(audited)")
public void beforeAudited(JoinPoint jp, Audited audited) {
    // audited is the actual annotation instance on the method
    String action = audited.action();
}
```

### @within

Selects all methods in types (classes) that carry the specified annotation. Different from `@annotation`: `@within` matches on the class-level annotation, not the method-level annotation.

```java
// All methods in classes annotated with @Service (Spring's stereotype)
@Pointcut("@within(org.springframework.stereotype.Service)")
public void inServiceBeans() {}
```

### bean

Selects join points on Spring beans whose name matches the given pattern. This is Spring AOP-specific and not part of AspectJ.

```java
// Only the bean named "orderService"
@Pointcut("bean(orderService)")
public void orderServiceBean() {}

// All beans whose name ends with "Repository"
@Pointcut("bean(*Repository)")
public void allRepositoryBeans() {}
```

### args and @args

`args` selects methods by the runtime type of their arguments. `@args` selects methods whose arguments are annotated at the class level.

```java
// Methods called with a single argument that is a Long at runtime
@Pointcut("args(Long)")
public void longArgMethod() {}

// Binding: bind the argument value into the advice parameter
@Before("execution(* com.example..*(..)) && args(id)")
public void beforeWithId(JoinPoint jp, Long id) {
    // id is bound to the first Long argument
}
```

### Combining Pointcut Expressions

Use `&&` (and), `||` (or), and `!` (not) to compose expressions.

```java
@Aspect
@Component
public class ComposedPointcuts {

    @Pointcut("within(com.example.service..*)")
    public void serviceLayer() {}

    @Pointcut("within(com.example.repository..*)")
    public void repositoryLayer() {}

    // Both service and repository layers
    @Pointcut("serviceLayer() || repositoryLayer()")
    public void dataAccessLayer() {}

    // Service layer methods that are annotated with @Transactional
    @Pointcut("serviceLayer() && @annotation(org.springframework.transaction.annotation.Transactional)")
    public void transactionalServiceMethods() {}

    // Anything in com.example but NOT in the infrastructure package
    @Pointcut("within(com.example..*) && !within(com.example.infrastructure..*)")
    public void applicationCode() {}
}
```

### Named Pointcut Reuse Across Aspects

```java
// A dedicated class for shared pointcut definitions
@Aspect   // still needs @Aspect to be recognized for named pointcuts
@Component
public class SharedPointcuts {

    @Pointcut("within(com.example.service..*)")
    public void serviceLayer() {}

    @Pointcut("within(com.example.web..*)")
    public void webLayer() {}
}

// Other aspects reference shared pointcuts by fully qualified name
@Aspect
@Component
public class MetricsAspect {

    @Around("com.example.aop.SharedPointcuts.serviceLayer()")
    public Object measureService(ProceedingJoinPoint pjp) throws Throwable {
        // ...
        return pjp.proceed();
    }
}
```

## Gotchas

**`execution` vs `within` for interfaces.** `within(com.example.OrderService)` does not match if `OrderService` is an interface and the pointcut is evaluated against the proxy. Use `execution(* com.example.OrderService.*(..))` to match calls through an interface type. `within` works reliably on concrete classes.

**Argument binding requires consistent names.** When binding arguments with `args(id)` or `@annotation(audited)`, the parameter name in the pointcut expression must exactly match the advice method parameter name. A mismatch causes `IllegalArgumentException` at startup.

**`bean()` designator only works in Spring AOP.** AspectJ does not know about Spring beans. Using `bean()` in a full AspectJ weaving context will fail.

**Package pattern precision.** `execution(* com.example.*)` matches only classes directly in `com.example`, not sub-packages. Use `execution(* com.example..*)` for all sub-packages. Forgetting the extra dot is a common mistake that silently matches nothing.

**`@within` does not inherit.** If a superclass is annotated with `@Service` but the subclass is not, `@within(@org.springframework.stereotype.Service *)` may or may not match depending on the proxy type. Prefer `execution` with explicit package patterns for reliability in inheritance scenarios.
