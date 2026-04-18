# AOP Concepts

## Overview

Aspect-Oriented Programming (AOP) separates cross-cutting concerns — logic that applies to many classes but does not belong to their core responsibility — from the business code that should not have to know about them. Logging, metrics, transaction management, security enforcement, and retry logic are the most common cross-cutting concerns in Spring applications. Without AOP, these are scattered through every class in repetitive boilerplate.

Spring AOP implements the AOP Alliance interfaces and uses runtime weaving via proxies. This approach is simpler than compile-time or load-time weaving but has one important constraint: it only intercepts method calls that go through the Spring-managed proxy. Calls from one method to another method on the same class bypass the proxy entirely.

AspectJ is the full AOP framework that Spring integrates with for pointcut expression syntax. Spring uses AspectJ's `@Aspect` annotation and its pointcut expression language, but it does not perform AspectJ weaving by default. When Spring AOP's proxy limitation is too restrictive, full AspectJ weaving can be enabled with `@EnableLoadTimeWeaving` or compile-time weaving via the AspectJ Maven plugin.

## Key Concepts

### Core Vocabulary

| Term | Definition |
|---|---|
| Aspect | A class that encapsulates cross-cutting logic. Annotated with `@Aspect`. |
| Join point | A specific point in program execution where an aspect can be applied. In Spring AOP, always a method invocation. |
| Pointcut | An expression that selects which join points an advice applies to. Uses AspectJ pointcut expression syntax. |
| Advice | The action taken at a join point. The "what" of the aspect. |
| Weaving | Linking aspects with the main application code. Spring AOP weaves at runtime via proxies. |
| Target | The object being proxied. The actual bean whose methods are intercepted. |
| Proxy | The AOP-enhanced object that wraps the target and intercepts method calls. |
| Introduction | Adding new methods or fields to existing types. Implemented with `@DeclareParents`. |

### @Aspect and @EnableAspectJAutoProxy

```java
// Enable Spring's AspectJ auto-proxy support.
// With Spring Boot, this is activated automatically when spring-boot-starter-aop is on the classpath.
@Configuration
@EnableAspectJAutoProxy
public class AopConfig {
}

// An aspect is a Spring bean annotated with @Aspect.
// @Component registers it in the application context; @Aspect marks it for AOP processing.
@Aspect
@Component
public class LoggingAspect {
    // advice methods go here
}
```

### Spring AOP vs AspectJ

| Feature | Spring AOP | Full AspectJ |
|---|---|---|
| Weaving time | Runtime (proxy) | Compile-time or load-time |
| Join point types | Method execution only | Method, constructor, field access, static initializer |
| Self-invocation | Intercepted: no | Intercepted: yes |
| Performance | Proxy overhead per call | Bytecode-level; near-zero overhead |
| Configuration | Spring beans and annotations | AspectJ compiler or Java agent |
| Typical use | Application-level cross-cutting | Framework-level or extreme performance requirements |

Spring AOP is sufficient for the vast majority of application-layer cross-cutting concerns. Reach for full AspectJ only when you need to intercept calls that Spring proxies cannot — such as constructor execution, field access, or `static` method calls.

### Enabling AOP in Spring Boot

```xml
<!-- pom.xml — brings in spring-aop and AspectJ runtime -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

Spring Boot auto-configuration activates `@EnableAspectJAutoProxy` when `spring-boot-starter-aop` is present and there is at least one `@Aspect` bean in the context. No explicit `@EnableAspectJAutoProxy` annotation is required.

### JoinPoint and ProceedingJoinPoint

Both provide metadata about the intercepted method invocation:

```java
@Aspect
@Component
public class InspectionAspect {

    @Before("execution(* com.example..*(..))")
    public void inspect(JoinPoint joinPoint) {
        String className  = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args     = joinPoint.getArgs();
        // JoinPoint is read-only; it does not allow proceeding or replacing the result.
    }

    @Around("execution(* com.example..*(..))")
    public Object aroundInspect(ProceedingJoinPoint pjp) throws Throwable {
        // ProceedingJoinPoint extends JoinPoint and adds proceed() to continue execution.
        Object result = pjp.proceed();        // invoke the actual method
        // result may be replaced with a different return value
        return result;
    }
}
```

## Gotchas

**Self-invocation bypasses the proxy.** If a `@Transactional` or `@Cacheable` method calls another annotated method in the same class, the second call goes directly to the target object without passing through the proxy. The annotation is silently ignored. Solutions: move the called method to a different bean, inject `self` via `@Autowired ApplicationContext` and call through it, or switch to full AspectJ load-time weaving.

**@Aspect beans must be Spring-managed.** `@Aspect` alone does not register the class as a bean. Add `@Component` (or `@Bean` in a `@Configuration` class) to make it visible to the auto-proxy infrastructure.

**Abstract classes are not proxied by default.** CGLIB can subclass abstract classes, but only if they have a no-argument constructor. JDK dynamic proxies require an interface.

**AspectJ expression errors fail at startup.** An invalid pointcut expression (e.g., a typo in a package name) causes `IllegalArgumentException` during context refresh, not silently at runtime. This is a feature: fail fast.

**Aspect applies to all instances.** A `@Singleton` aspect instance is shared across all proxied bean invocations. Stateful aspects must synchronize their internal state or use `AspectJExpressionPointcut` with `this()` binding.
