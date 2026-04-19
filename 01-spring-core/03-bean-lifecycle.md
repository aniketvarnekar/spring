# Bean Lifecycle

## Overview

Every Spring-managed bean passes through a deterministic sequence of lifecycle phases from the moment the container decides to create it to the moment the container shuts down. Understanding this sequence is essential for placing initialization and cleanup logic at the correct point, for diagnosing ordering issues between beans, and for correctly implementing framework extension points like `BeanPostProcessor` and `BeanFactoryPostProcessor`.

The lifecycle can be divided into three broad stages: instantiation and dependency injection, initialization callbacks, and destruction callbacks. The initialization stage itself is layered: Spring-provided `BeanPostProcessor` extensions (which enable `@Autowired`, `@Value`, and similar annotations) run before and after the bean's own initialization callbacks, so the bean sees a fully wired state by the time `@PostConstruct` fires.

`BeanPostProcessor` and `BeanFactoryPostProcessor` are two distinct extension points that are often confused. `BeanFactoryPostProcessor` operates on `BeanDefinition` objects before any regular beans are instantiated — it can modify metadata like property values and scope. `BeanPostProcessor` operates on bean instances after each bean is created and injected but before it is placed in the singleton cache — it can wrap, replace, or validate the instance.

The destruction phase runs only when the `ApplicationContext` is explicitly closed (via `ConfigurableApplicationContext.close()` or a shutdown hook). In a Spring Boot application, `SpringApplication` registers a JVM shutdown hook automatically, so `@PreDestroy` and `DisposableBean.destroy()` fire on normal JVM exit. Beans that are prototype-scoped are never destroyed by the container — their destruction is the caller's responsibility.

## Key Concepts

### Full Lifecycle Sequence

```text
1.  BeanDefinition registered (by BeanFactoryPostProcessors if needed)
2.  Instantiation (constructor called)
3.  Property population (@Autowired fields/setters injected)
4.  BeanNameAware.setBeanName()
5.  BeanClassLoaderAware.setBeanClassLoader()
6.  BeanFactoryAware.setBeanFactory()
7.  ApplicationContextAware.setApplicationContext()
    (also: EnvironmentAware, ResourceLoaderAware, etc.)
8.  BeanPostProcessor.postProcessBeforeInitialization()  ← @PostConstruct processed here
9.  InitializingBean.afterPropertiesSet()
10. init-method (declared via @Bean(initMethod=...) or XML)
11. BeanPostProcessor.postProcessAfterInitialization()  ← AOP proxies created here
12. [Bean is ready for use — placed in singleton cache]
    ...
13. @PreDestroy                                          ← processed by BPP
14. DisposableBean.destroy()
15. destroy-method (declared via @Bean(destroyMethod=...) or XML)
```

`@PostConstruct` is not handled by Spring itself but by `CommonAnnotationBeanPostProcessor`, which is a `BeanPostProcessor` registered in any `AnnotationConfigApplicationContext`. It processes the annotation during `postProcessBeforeInitialization`. This is why `@PostConstruct` runs before `InitializingBean.afterPropertiesSet()`.

### Aware Interfaces

Aware interfaces let beans access container infrastructure without depending on it through injection, which would create circular dependencies in framework internals. Common aware interfaces:

```java
@Component
public class AuditService implements ApplicationContextAware, BeanNameAware {

    private ApplicationContext applicationContext;
    private String beanName;

    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        // Called after all Aware callbacks, before BeanPostProcessors
        this.applicationContext = ctx;
    }

    @Override
    public void setBeanName(String name) {
        this.beanName = name;
    }
}
```

Using `ApplicationContextAware` to look up beans is the service-locator anti-pattern. Reserve aware interfaces for framework-level code — for example, a custom `BeanPostProcessor` that needs the `BeanFactory`.

### Initialization Callbacks

Spring supports three initialization mechanisms, executed in the order shown:

```java
@Component
public class ConnectionPool implements InitializingBean {

    @PostConstruct
    public void validateConfig() {
        // Runs first (via CommonAnnotationBeanPostProcessor)
        // All @Autowired dependencies are available here
    }

    @Override
    public void afterPropertiesSet() {
        // Runs second (via InitializingBean contract)
        // Couples the class to Spring API
    }

    // Declared as @Bean(initMethod = "open") or in XML
    public void open() {
        // Runs third — no Spring coupling
        // Preferred for application classes; reserve the others for framework code
    }
}
```

For application code, `@PostConstruct` is the cleanest option because it decouples the class from the Spring API while remaining declarative. `init-method` is useful for wiring third-party classes that you cannot annotate. `InitializingBean` should be reserved for framework and library code within the Spring ecosystem.

### Destruction Callbacks

Destruction mirrors initialization:

```java
@Component
public class ConnectionPool implements DisposableBean {

    @PreDestroy
    public void releaseSessions() {
        // Runs first (via CommonAnnotationBeanPostProcessor)
    }

    @Override
    public void destroy() {
        // Runs second
    }

    public void close() {
        // Declared as @Bean(destroyMethod = "close")
        // Runs third
    }
}
```

By default, `@Bean` methods infer a destroy method automatically: if the bean type declares a public no-arg `close()` or `shutdown()` method, Spring calls it. To opt out of this inference, set `destroyMethod = ""`.

### BeanFactoryPostProcessor

`BeanFactoryPostProcessor` modifies `BeanDefinition` metadata before any beans are instantiated. The most commonly encountered implementation is `PropertySourcesPlaceholderConfigurer`, which resolves `${...}` placeholders in `BeanDefinition` property values:

```java
@Component
public class LoggingBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) {
        // Safe: operates on definitions, not instances
        for (String name : factory.getBeanDefinitionNames()) {
            BeanDefinition def = factory.getBeanDefinition(name);
            // Inspect or modify def here
        }
    }
}
```

`BeanFactoryPostProcessor` beans are instantiated and invoked before all other beans. They must be declared at the top-level context, not inside a `@Configuration` class that participates in circular dependencies.

### BeanPostProcessor

`BeanPostProcessor` operates on instances. Each instance passes through all registered `BeanPostProcessor` implementations in order (controlled by `Ordered` or `@Order`). The two callback methods bracket the bean's own initialization callbacks:

```java
@Component
public class TimingBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        // Called after injection, before @PostConstruct / afterPropertiesSet / init-method
        // Return the bean (or a replacement) — must not return null for a singleton
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // Called after all initialization callbacks
        // This is where AOP proxy creation happens — returning a proxy here replaces
        // the original instance in the singleton cache
        return bean;
    }
}
```

Because AOP proxies are created in `postProcessAfterInitialization`, any code that runs in earlier phases (`@PostConstruct`, `afterPropertiesSet`) runs on the raw, un-proxied object. This is relevant for `@Transactional`: a method called from `@PostConstruct` does not run in a transaction because the proxy does not yet exist.

## Gotchas

Calling `applicationContext.getBean()` from within a `BeanFactoryPostProcessor` is dangerous. It triggers premature instantiation of the requested bean before `BeanPostProcessor` implementations have been registered, meaning the bean will not be processed by `@Autowired` handlers, AOP weavers, or other post-processors. The container may issue a warning, and the resulting bean may be partially or incorrectly configured.

Prototype-scoped beans do not receive destruction callbacks. Spring instantiates a new prototype on every `getBean()` call and immediately relinquishes management of that instance. `@PreDestroy` and `DisposableBean.destroy()` are silently ignored for prototypes. If a prototype bean holds resources (connections, file handles), the caller is responsible for releasing them.

The order of `BeanPostProcessor` execution matters for AOP. If a custom `BeanPostProcessor` tries to autowire a bean that will eventually be wrapped in an AOP proxy, it may receive the raw, un-proxied instance if the AOP post-processor runs after it. The `@Order` value of custom `BeanPostProcessor` implementations must be considered relative to `AnnotationAwareAspectJAutoProxyCreator`, which creates the AOP proxies.

`@PostConstruct` does not propagate exceptions differently from normal method calls. If the annotated method throws a checked exception that is wrapped in an `UndeclaredThrowableException`, the container aborts the bean initialization and fails the entire startup. Design initialization methods to be idempotent where possible and to throw unchecked exceptions on fatal conditions.

When a `@Configuration` class itself implements `BeanFactoryPostProcessor` or `BeanPostProcessor`, it is instantiated very early — before other beans and before `@PropertySource` values are fully resolved. This means `@Value` injection into a class that is also a `BeanFactoryPostProcessor` may not receive placeholder-resolved values. The solution is to declare the `BFPP` as a static `@Bean` method inside a `@Configuration` class, or as a separate class.
