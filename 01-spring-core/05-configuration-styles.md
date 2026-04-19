# Configuration Styles

## Overview

Spring supports three main styles for defining beans and wiring the application context: XML-based configuration, annotation-driven component scanning, and Java-based `@Configuration` classes. In modern Spring applications these styles coexist — XML is rare but still encountered in legacy codebases — and understanding each is necessary for reading configuration that predates Spring Boot as well as writing new code cleanly.

Java-based configuration with `@Configuration` and `@Bean` methods is the most explicit style. It gives full programmatic control over bean construction, allows conditional logic inline, and is type-safe. The class is itself a Spring-managed bean, and Spring applies CGLIB subclassing to it in "full mode" to intercept `@Bean` method calls and enforce singleton semantics.

Component scanning with `@ComponentScan` automates bean registration by searching the classpath for classes annotated with stereotype annotations (`@Component`, `@Service`, `@Repository`, `@Controller`, and their meta-annotated derivatives). It eliminates the boilerplate of explicit `@Bean` declarations for application-layer beans while leaving infrastructure beans (data sources, template objects, third-party integrations) in `@Configuration` classes.

The two styles compose naturally: a `@Configuration` class can declare `@Bean` methods for infrastructure and import or scan packages for application beans simultaneously. Understanding when each is appropriate — and the subtle differences in proxy behavior between them — prevents configuration bugs that are difficult to diagnose.

## Key Concepts

### @Configuration Full Mode vs Lite Mode

A class annotated with `@Configuration` is processed in "full mode": Spring applies CGLIB to create a subclass that overrides every `@Bean` method. When one `@Bean` method calls another, the CGLIB subclass intercepts the call and returns the cached singleton instead of invoking the method again.

```java
@Configuration
public class AppConfig {

    @Bean
    public DataSource dataSource() {
        return new HikariDataSource(hikariConfig());
    }

    @Bean
    public HikariConfig hikariConfig() {
        var config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost/mydb");
        return config;
    }

    @Bean
    public EntityManagerFactory entityManagerFactory() {
        // Calls hikariConfig() — CGLIB intercepts and returns the cached bean
        // Without CGLIB, a second HikariConfig instance would be created
        HikariConfig config = hikariConfig();
        // ...
    }
}
```

"Lite mode" applies when `@Bean` methods are declared in a class not annotated with `@Configuration` (e.g., `@Component`, `@Service`, or no stereotype at all). CGLIB subclassing is not applied, so inter-`@Bean` method calls produce new instances each time. Lite mode is faster to start up (no CGLIB proxy) but loses singleton guarantees for cross-`@Bean` references.

`@Configuration(proxyBeanMethods = false)` explicitly opts out of CGLIB in `@Configuration` classes. This is the mode used by most Spring Boot auto-configuration classes because:
1. Auto-configurations rarely call other `@Bean` methods within the same class.
2. It produces faster startup and smaller memory footprint at scale.
3. The class is `final`-friendly since no subclassing is needed.

```java
@Configuration(proxyBeanMethods = false)
public class LiteConfig {
    @Bean
    public Validator validator() {
        // Safe: nothing else in this class calls validator()
        return new LocalValidatorFactoryBean();
    }
}
```

### Component Scanning

```java
@Configuration
@ComponentScan(basePackages = "com.example",
               excludeFilters = @ComponentScan.Filter(
                   type = FilterType.ANNOTATION,
                   classes = Repository.class))
public class AppConfig { }
```

`@SpringBootApplication` includes `@EnableAutoConfiguration` and `@ComponentScan` for the package of the annotated class and all sub-packages. In Spring Boot applications, explicit `@ComponentScan` is rarely needed unless you need to scan packages outside the application's root package.

Stereotype annotations form a hierarchy:

```text
@Component
  ├── @Service       (marks service-layer beans; no added semantics today)
  ├── @Repository    (marks persistence-layer beans; enables exception translation)
  └── @Controller    (marks MVC controllers; enables request mapping)
        └── @RestController  (= @Controller + @ResponseBody)
```

`@Repository` is the one stereotype with real added behavior: `PersistenceExceptionTranslationPostProcessor` detects it and wraps JPA/JDBC exceptions in Spring's `DataAccessException` hierarchy.

### @Import and @ImportResource

`@Import` pulls in one or more `@Configuration` classes, `ImportSelector` implementations, or `ImportBeanDefinitionRegistrar` implementations:

```java
@Configuration
@Import({SecurityConfig.class, CacheConfig.class})
public class RootConfig { }
```

`ImportSelector` allows conditional imports based on runtime information — this is the mechanism underlying `@EnableAutoConfiguration`:

```java
public class ConditionalImportSelector implements ImportSelector {
    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        // Return class names to import based on metadata
        return new String[]{"com.example.ConditionalBean"};
    }
}
```

`@ImportResource` bridges XML configuration into a Java config class:

```java
@Configuration
@ImportResource("classpath:legacy-beans.xml")
public class AppConfig { }
```

### @DependsOn and Ordering

By default, Spring determines instantiation order from the dependency graph. `@DependsOn` adds explicit ordering constraints for beans that have an implicit dependency not detectable by type (for example, a bean that registers a JDBC driver as a side effect of its constructor):

```java
@Bean
@DependsOn("driverRegistrar")
public DataSource dataSource() { ... }
```

`SmartInitializingSingleton` is a callback interface invoked after all singletons have been initialized. It is useful for validation logic that requires all beans to be wired but must run before the container is fully ready for use.

### Conditional Beans

`@Conditional` is the raw mechanism for conditionally including a `BeanDefinition`:

```java
@Bean
@Conditional(OnLinuxCondition.class)
public PosixFileWatcher fileWatcher() { ... }
```

Spring Boot's `@ConditionalOn*` annotations are meta-annotated with `@Conditional` and cover the most common cases:

| Annotation | Condition |
|---|---|
| `@ConditionalOnClass` | A class is present on the classpath |
| `@ConditionalOnMissingClass` | A class is absent from the classpath |
| `@ConditionalOnBean` | A bean of a given type/name exists |
| `@ConditionalOnMissingBean` | No bean of a given type/name exists |
| `@ConditionalOnProperty` | A property has a specific value |
| `@ConditionalOnWebApplication` | Running as a web application |
| `@ConditionalOnExpression` | A SpEL expression evaluates to true |

## Gotchas

When `proxyBeanMethods = false` is set on a `@Configuration` class, calling one `@Bean` method from another produces a new instance, not the container singleton. If that cross-call exists and was relying on CGLIB interception for shared state, the result is multiple instances where one was expected. The solution is to inject the shared bean as a parameter to the `@Bean` method instead.

Component scanning without explicit `basePackages` starts from the package of the annotated class. Placing a `@SpringBootApplication` (and therefore `@ComponentScan`) in a non-root package — for example, `com.example.app.main` — will miss beans declared in sibling or parent packages like `com.example.app.service`. Always place the application class in the root package of the module.

`@Import` on a class is processed at configuration class parse time, before most `BeanFactoryPostProcessor` implementations run. An `ImportSelector` can access annotation metadata but cannot reliably call `applicationContext.getBean()` or depend on environment properties that are set by a later `BeanFactoryPostProcessor`.

Declaring the same bean name twice (via two `@Bean` methods with the same name, or a `@Bean` and a `@Component` with the same name) results in silent override by default in standalone Spring. In Spring Boot 2.6+, `spring.main.allow-bean-definition-overriding` defaults to `false`, turning the override into a startup failure. This is the safer default, but it catches previously silent bugs when upgrading.

A `@Configuration` class itself is a singleton bean managed by the container. Its CGLIB subclass is what appears in the singleton cache, not the original class. Code that reflects on the bean's class will see the CGLIB-generated subclass name, not the original class name. Use `AopProxyUtils.ultimateTargetClass()` or check `ClassUtils.getUserClass()` to obtain the original class for reflection purposes.
