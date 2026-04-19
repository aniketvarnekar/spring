# IoC Container

## Overview

The Inversion of Control container is the heart of the Spring Framework. Rather than objects constructing or locating their own dependencies, the container takes responsibility for creating objects, wiring them together, configuring them, and managing their lifecycle. This inversion is what makes Spring applications testable and loosely coupled at scale.

The container's primary client-facing interface is `ApplicationContext`, which extends `BeanFactory` and adds enterprise-specific functionality: event publication, internationalization support, environment abstraction, and eager initialization of singleton beans. `BeanFactory` is a lazy, lower-level interface that initializes beans only on demand. In practice, `BeanFactory` is almost never used directly in application code; it remains relevant mainly for framework internals and resource-constrained environments.

Every bean the container manages is described by a `BeanDefinition`. This is the internal metadata object that records a bean's class, scope, constructor arguments, property values, initialization and destruction method names, and dependency relationships. When you annotate a class with `@Component` or declare a `@Bean` method, the container ultimately converts that declaration into a `BeanDefinition` at startup, before any beans are instantiated. Understanding `BeanDefinition` is essential for grasping how `BeanFactoryPostProcessor` operates — it works on definitions, not instances.

The container bootstrap process is a two-phase pipeline. In the first phase, all `BeanDefinition` objects are loaded, registered, and then post-processed by any `BeanFactoryPostProcessor` implementations. In the second phase, non-lazy singleton beans are instantiated in dependency order, passed through the `BeanPostProcessor` chain, and placed in the singleton cache. Only after both phases complete does the `ApplicationContext` publish a `ContextRefreshedEvent` and become ready for use.

## Key Concepts

### ApplicationContext Implementations

```text
BeanFactory
  └── ApplicationContext
        ├── ConfigurableApplicationContext
        │     ├── AbstractApplicationContext
        │     │     ├── GenericApplicationContext
        │     │     │     └── AnnotationConfigApplicationContext  (standalone apps)
        │     │     ├── ClassPathXmlApplicationContext            (XML config)
        │     │     └── FileSystemXmlApplicationContext           (XML from filesystem)
        │     └── WebApplicationContext
        │           └── AnnotationConfigWebApplicationContext     (traditional Spring MVC)
        └── (SpringApplication creates a context appropriate for the environment)
```

`AnnotationConfigApplicationContext` is the standard choice for annotation-driven standalone applications. `SpringApplication` — the entry point for Spring Boot — selects an appropriate `ApplicationContext` implementation based on what is on the classpath: `AnnotationConfigServletWebServerApplicationContext` for servlet-based web applications, `AnnotationConfigReactiveWebServerApplicationContext` for reactive stacks, and plain `AnnotationConfigApplicationContext` for non-web applications.

```java
// Standalone, annotation-driven bootstrap (no Spring Boot)
var ctx = new AnnotationConfigApplicationContext(AppConfig.class);
MyService svc = ctx.getBean(MyService.class);
ctx.close();
```

### BeanFactory vs ApplicationContext

| Capability | BeanFactory | ApplicationContext |
|---|---|---|
| Bean instantiation and wiring | Yes | Yes |
| Singleton cache | Yes | Yes |
| BeanPostProcessor auto-detection | No (manual) | Yes (automatic) |
| BeanFactoryPostProcessor auto-detection | No (manual) | Yes (automatic) |
| Internationalization (MessageSource) | No | Yes |
| Event publication | No | Yes |
| Environment / PropertySource | No | Yes |
| Eager singleton initialization | No | Yes |

The absence of automatic `BeanPostProcessor` detection in a raw `BeanFactory` is particularly significant: without it, `@Autowired`, `@Value`, and `@PostConstruct` do not work unless the corresponding processors are registered manually.

### BeanDefinition Internals

`BeanDefinition` is the canonical description of a managed bean. Its key attributes are:

- **beanClassName** — the fully qualified class name or null for factory-method beans
- **scope** — `singleton`, `prototype`, or a custom scope name
- **lazyInit** — whether to defer instantiation until first use
- **constructorArgumentValues** — indexed or named constructor arguments
- **propertyValues** — setter-injected properties
- **initMethodName** / **destroyMethodName** — lifecycle callbacks
- **dependsOn** — explicit ordering constraints (`@DependsOn`)
- **primary** — whether this definition is the primary candidate for autowiring
- **factoryBeanName** / **factoryMethodName** — for `@Bean` methods on `@Configuration` classes

`BeanDefinitionRegistry` is the interface through which definitions are added and queried. `DefaultListableBeanFactory` implements both `BeanDefinitionRegistry` and `BeanFactory`, and it is the implementation that backs every `ApplicationContext`.

### Container Bootstrap Phases

```text
1. Load configuration
   │  (XML parsing, component scanning, @Configuration processing)
   │
   ▼
2. Register BeanDefinitions in BeanDefinitionRegistry
   │
   ▼
3. Invoke BeanFactoryPostProcessors
   │  (ConfigurationClassPostProcessor expands @Configuration classes)
   │  (PropertySourcesPlaceholderConfigurer resolves ${...} in BeanDefinitions)
   │
   ▼
4. Register BeanPostProcessors (as beans themselves)
   │
   ▼
5. Instantiate and initialize non-lazy singletons
   │  (dependency order determined by BeanDefinition graph)
   │  (each bean passes through BeanPostProcessor chain)
   │
   ▼
6. Publish ContextRefreshedEvent — container is ready
```

`ConfigurationClassPostProcessor` is the most important `BeanFactoryPostProcessor` in a typical application. It processes `@Configuration`, `@ComponentScan`, `@Import`, `@Bean`, and `@PropertySource` annotations, converting them all into `BeanDefinition` entries before any beans are instantiated.

### Accessing the Container

Beans should not look up other beans from the container directly — that is the service-locator anti-pattern. When a bean genuinely needs dynamic lookup (optional dependencies, prototype retrieval, etc.), prefer `ObjectProvider<T>`:

```java
@Component
public class ReportGenerator {
    private final ObjectProvider<PdfRenderer> rendererProvider;

    public ReportGenerator(ObjectProvider<PdfRenderer> rendererProvider) {
        this.rendererProvider = rendererProvider;
    }

    public void generate() {
        // getIfAvailable returns null if no bean of this type is registered
        PdfRenderer renderer = rendererProvider.getIfAvailable();
        if (renderer != null) {
            renderer.render();
        }
    }
}
```

`ObjectProvider` is resolved lazily and does not cause a `NoSuchBeanDefinitionException` for missing dependencies if you call `getIfAvailable()` or `getIfUnique()`.

## Gotchas

Calling `getBean()` directly from within a bean to obtain another bean defeats the purpose of dependency injection and creates a hidden coupling between the bean and the container. `ObjectProvider` is the safe alternative when truly dynamic lookup is necessary.

The two-phase bootstrap means that `BeanFactoryPostProcessor` implementations must not trigger premature instantiation of other beans by calling `getBean()` inside `postProcessBeanFactory`. Doing so causes beans to be initialized before the full post-processing pipeline has run, which can result in missing `@Autowired` values or incorrect scope behavior.

`ClassPathXmlApplicationContext` and `AnnotationConfigApplicationContext` are both standalone containers — they do not integrate with a web server. If you use either in a Spring Boot application alongside `SpringApplication`, you end up with two separate container hierarchies, neither of which manages the other's beans. Always let `SpringApplication` create and own the `ApplicationContext`.

Bean names matter more than they appear to. When two `@Bean` methods in different `@Configuration` classes declare the same method name, the second definition silently overrides the first by default. Spring Boot 2.1+ sets `spring.main.allow-bean-definition-overriding=false` by default, which turns this silent override into a startup exception — the safer behavior.

The `ApplicationContext` hierarchy (parent-child contexts) is still used in classic Spring MVC applications, where a root context holds services and a child `DispatcherServlet` context holds controllers. In Spring Boot, the hierarchy collapses to a single context in most configurations. Misunderstanding the hierarchy causes `@Autowired` resolution failures, because a child context can see parent beans but a parent context cannot see child beans.
