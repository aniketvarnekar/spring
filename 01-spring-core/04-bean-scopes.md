# Bean Scopes

## Overview

A bean's scope determines how many instances the container creates and how long each instance lives. Spring defines six built-in scopes: singleton, prototype, request, session, application, and websocket. The first two are available in any `ApplicationContext`; the last four require a web-aware context. Custom scopes can also be registered with the container through the `Scope` SPI.

The singleton scope is the default and the most misunderstood. A singleton bean means one instance per `ApplicationContext` instance, not one instance per JVM or classloader. Two separate `ApplicationContext` instances can each hold their own copy of a singleton-scoped bean. This distinction matters when parent-child context hierarchies are in use.

The prototype scope is the opposite extreme: the container creates a new instance on every `getBean()` call and every injection point resolution. The container does not cache prototypes and does not track them after handing them over. This makes prototype beans unsuitable for resources that need lifecycle management, since `@PreDestroy` is never called on them.

Web scopes (request, session, application) bind bean instances to the lifecycle of an HTTP artifact. They require the `RequestContextListener` or `RequestContextFilter` to be registered so that Spring can bind and unbind the current request/session to the thread. Spring Boot registers these automatically when a web application context is active.

## Key Concepts

### Singleton Scope

```java
@Component
// @Scope("singleton") is the default — no annotation needed
public class UserService {
    // One instance per ApplicationContext
    // Shared across all callers — must be thread-safe
}
```

Singleton beans must be thread-safe when they hold mutable state. Instance variables in a singleton bean that are modified by multiple threads without synchronization are a common source of race conditions in Spring applications.

### Prototype Scope

```java
@Component
@Scope("prototype")
public class ReportBuilder {
    // New instance on every injection or getBean() call
    // Holds per-report state — not appropriate as a singleton
    private final List<Section> sections = new ArrayList<>();

    public void addSection(Section section) { sections.add(section); }
    public Report build() { return new Report(sections); }
}
```

When a prototype-scoped bean is declared as a dependency of another bean via `@Autowired`, the prototype is created once and stored as a field — the same instance is reused for the lifetime of the enclosing singleton, defeating the purpose of the prototype scope.

### The Scoped-Proxy Problem

The central challenge with shorter-lived scopes in a longer-lived bean is that injection happens once at container startup. If a singleton holds a reference to a request-scoped bean, that reference becomes stale or invalid once the request ends.

```
Singleton (lives for application lifetime)
  └── @Autowired RequestScopedBean (created once at startup — WRONG)
                                    ↑
                     Should be a new instance per request
```

The solution is a scoped proxy. The container injects a proxy into the singleton, and the proxy delegates to the actual scoped instance on every method call:

```java
@Component
@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestContext {
    private String traceId;
    // getters/setters
}

@Service
public class AuditService {
    // Receives a CGLIB proxy, not a real RequestContext
    // The proxy looks up the real instance from the request scope on each call
    private final RequestContext requestContext;

    public AuditService(RequestContext requestContext) {
        this.requestContext = requestContext;
    }
}
```

`ScopedProxyMode.TARGET_CLASS` uses CGLIB to subclass the bean. `ScopedProxyMode.INTERFACES` uses a JDK dynamic proxy and requires the bean to implement at least one interface.

### Prototype Injected into Singleton

For a prototype bean to behave correctly when consumed by a singleton, there are three options:

**Option 1 — Method injection (`@Lookup`):** The container overrides the annotated method at runtime to return a new prototype instance on every call.

```java
@Component
public abstract class SingletonService {

    // The container overrides this method — do not call super or implement it
    @Lookup
    protected abstract PrototypeWorker createWorker();

    public void process(Task task) {
        // A fresh PrototypeWorker on every invocation
        PrototypeWorker worker = createWorker();
        worker.execute(task);
    }
}
```

**Option 2 — `ObjectProvider<T>`:** Inject `ObjectProvider<PrototypeWorker>` and call `.getObject()` when a new instance is needed. Clean and non-abstract.

**Option 3 — `ApplicationContext.getBean()`:** Works but is the service-locator anti-pattern; use `ObjectProvider` instead.

### Web Scopes

| Scope | Lifecycle | Shared with |
|-------|-----------|-------------|
| `request` | Single HTTP request | No one |
| `session` | HTTP session | Same user across requests |
| `application` | `ServletContext` lifetime | All users, all sessions |
| `websocket` | WebSocket session | Messages in same WebSocket session |

```java
@Component
@Scope(value = WebApplicationContext.SCOPE_SESSION,
       proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ShoppingCart {
    private final List<Item> items = new ArrayList<>();
    // One per user session, accessed via scoped proxy from singleton services
}
```

### Custom Scope

Implement the `Scope` interface and register it:

```java
public class ThreadScope implements Scope {
    private final ThreadLocal<Map<String, Object>> store =
            ThreadLocal.withInitial(HashMap::new);

    @Override
    public Object get(String name, ObjectFactory<?> objectFactory) {
        return store.get().computeIfAbsent(name, k -> objectFactory.getObject());
    }

    @Override
    public Object remove(String name) {
        return store.get().remove(name);
    }

    @Override
    public void registerDestructionCallback(String name, Runnable callback) {
        // Call callback when the thread's scope is invalidated
    }

    @Override
    public String getConversationId() {
        return String.valueOf(Thread.currentThread().getId());
    }
}

// Registration
@Configuration
public class ScopeConfig {
    @Bean
    public static CustomScopeConfigurer customScopeConfigurer() {
        var configurer = new CustomScopeConfigurer();
        configurer.addScope("thread", new ThreadScope());
        return configurer;
    }
}
```

## Gotchas

A prototype bean injected via `@Autowired` into a singleton receives exactly one instance at startup. The singleton holds that single prototype instance for its entire lifetime, turning the prototype into an effective singleton. If per-call fresh instances are required, use `@Lookup`, `ObjectProvider`, or `ApplicationContext.getBean()`.

Scoped proxies add a small indirection cost on every method call, because the proxy must look up the real bean from the scope. This is negligible for request and session beans but worth measuring if a high-call-rate method is involved. More significantly, proxied beans cannot be compared by reference — `proxy == realBean` is always false.

Web-scoped beans cannot be used outside of a web request. If a background thread, a `@Scheduled` method, or an integration test attempts to call a method on a request-scoped bean, the scoped proxy throws an `IllegalStateException` because no request is bound to the current thread. Tests that use request-scoped beans must wrap execution in a `MockHttpServletRequest` via `RequestContextHolder`.

The `application` scope is essentially a singleton scoped to the `ServletContext` lifetime, which in a Spring Boot application is the same as the `ApplicationContext` lifetime. Prefer a plain singleton unless you specifically need `ServletContext` binding.

Stateful singleton beans are a pervasive source of threading bugs. Spring does not add any synchronization to singleton beans. A bean that accumulates per-request state in an instance field under load will exhibit data corruption. Verify that every singleton field is either immutable, a thread-safe type, or protected by explicit synchronization.
