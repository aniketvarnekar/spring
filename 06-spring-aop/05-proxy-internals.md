# Proxy Internals

## Overview

Spring AOP wraps target beans in proxies at bean creation time. When other beans call methods on the proxy, the proxy intercepts the call, runs applicable advice, and delegates to the real target object. The choice of proxy implementation — JDK dynamic proxy vs CGLIB — depends on whether the target class implements at least one interface.

Understanding the proxy mechanism is essential for diagnosing the self-invocation problem: a method within the same class calling another method on the same class does not go through the proxy, so AOP advice on the called method is never triggered. This is the single most common AOP failure mode in practice.

## Key Concepts

### JDK Dynamic Proxy

Used when the target bean implements at least one interface and `proxyTargetClass` is false (the default). The proxy implements the same interface(s) as the target and is backed by `java.lang.reflect.Proxy`. The proxy class is generated at runtime.

```text
Caller → Proxy (implements OrderService) → Target (OrderServiceImpl)
```

Constraints:
- The caller must hold a reference typed to the interface, not the concrete class.
- Methods not declared in any interface cannot be proxied.
- Casting the proxy to the concrete implementation class throws `ClassCastException`.

### CGLIB Proxy

Used when the target bean does not implement any interface, or when `proxyTargetClass = true` is configured. CGLIB generates a subclass of the target class at runtime and overrides its methods to insert the advice.

```text
Caller → CGLIBProxy extends OrderServiceImpl → OrderServiceImpl (target)
```

Constraints:
- The target class must not be `final` (cannot be subclassed).
- Intercepted methods must not be `final` (cannot be overridden).
- CGLIB requires a no-argument constructor (or Spring's objenesis support, which bypasses it in most cases).

### Choosing the Proxy Type

```java
// Force CGLIB for all beans in a configuration, even those that implement interfaces.
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AopConfig {}
```

With Spring Boot, `spring.aop.proxy-target-class=true` (the Spring Boot default since 2.x) forces CGLIB globally. This eliminates the need to always write code against interfaces and avoids `ClassCastException` surprises when code casts to the concrete class.

### The Self-Invocation Problem

```java
@Service
public class OrderService {

    @Transactional
    public void placeOrder(OrderRequest request) {
        validate(request);
        // calls save() directly on the same instance — bypasses the proxy
        save(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(OrderRequest request) {
        // REQUIRES_NEW is silently ignored when called from placeOrder()
        // because placeOrder() calls this.save(), not proxy.save()
    }
}
```

**Solutions:**

Option 1: Move `save()` to a separate bean.

```java
@Service
public class OrderPersistenceService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(OrderRequest request) { ... }
}

@Service
public class OrderService {
    private final OrderPersistenceService persistence;

    @Transactional
    public void placeOrder(OrderRequest request) {
        persistence.save(request);  // goes through the proxy
    }
}
```

Option 2: Self-inject via `ApplicationContext` (a workaround; option 1 is preferable).

```java
@Service
public class OrderService implements ApplicationContextAware {

    private OrderService self;

    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        this.self = ctx.getBean(OrderService.class);  // the proxy
    }

    @Transactional
    public void placeOrder(OrderRequest request) {
        self.save(request);  // goes through the proxy
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(OrderRequest request) { ... }
}
```

Option 3: Enable full AspectJ load-time weaving. No proxy is involved; weaving happens at the bytecode level and self-invocation is intercepted.

### Inspecting the Proxy Type at Runtime

```java
@Component
public class ProxyInspector {

    private final OrderService orderService;

    public ProxyInspector(OrderService orderService) {
        this.orderService = orderService;
    }

    public void inspect() {
        System.out.println(orderService.getClass().getName());
        // JDK proxy:  com.sun.proxy.$Proxy42
        // CGLIB:      com.example.service.OrderService$$SpringCGLIB$$0

        System.out.println(AopUtils.isJdkDynamicProxy(orderService));  // true or false
        System.out.println(AopUtils.isCglibProxy(orderService));        // true or false
        System.out.println(AopUtils.getTargetClass(orderService));      // com.example.service.OrderService
    }
}
```

### AopContext.currentProxy()

In rare cases where refactoring to a separate bean is not possible, `AopContext.currentProxy()` returns the proxy for the current thread's target object. This requires `exposeProxy = true` on `@EnableAspectJAutoProxy`.

```java
@Configuration
@EnableAspectJAutoProxy(exposeProxy = true)
public class AopConfig {}

@Service
public class OrderService {

    @Transactional
    public void placeOrder(OrderRequest request) {
        // Cast to the interface or class to call through the proxy
        ((OrderService) AopContext.currentProxy()).save(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(OrderRequest request) { ... }
}
```

This couples the service to the AOP infrastructure and is considered a code smell. Use it only when a proper refactoring is not feasible.

## Gotchas

**`final` methods are invisible to CGLIB.** A `final` method on the target class cannot be overridden by the CGLIB subclass and thus cannot be advised. Spring does not warn about this; the advice simply does not apply. This is a silent failure.

**`@Scope("prototype")` beans and proxies.** Prototype-scoped beans are not wrapped in persistent proxies — a new instance is created per `getBean()` call. Scoped proxies (`@Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)`) are different from AOP proxies; they are structural proxies used for scope bridging, not for AOP advice.

**Third-party library classes.** Spring AOP can only proxy Spring-managed beans. Objects instantiated with `new`, objects returned by external libraries, or objects obtained outside the ApplicationContext are not proxied and advice does not apply.

**Proxy equality.** The proxy and the target object are not `==` equal. If code stores a reference to a bean and later looks up the same bean from the context expecting reference equality, the comparison fails when a proxy is involved.

**Interface casting with CGLIB.** When `proxyTargetClass = true`, the CGLIB proxy is a subclass of the target class and also implements any interfaces the target implements. Casting to the concrete class or any interface both work. With JDK proxies, casting to the concrete class fails.
