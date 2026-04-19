# Auto-Configuration

## Overview

Auto-configuration is the mechanism that allows Spring Boot to configure the application context based on what is present on the classpath and what properties are set, without requiring explicit `@Bean` declarations. When you add `spring-boot-starter-data-jpa` to a project, the `EntityManagerFactory`, `DataSource`, `TransactionManager`, and a dozen other beans are configured automatically — this is auto-configuration at work, not Spring magic.

The entry point is `@EnableAutoConfiguration`, which is included in `@SpringBootApplication`. It imports `AutoConfigurationImportSelector`, a deferred `ImportSelector` that reads the list of auto-configuration classes from `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (the replacement for `spring.factories` as of Boot 2.7/3.0). Each listed class is a `@Configuration` class, and each of its `@Bean` methods is typically guarded by one or more `@ConditionalOn*` annotations that prevent configuration from applying when it would conflict with user-provided beans or when necessary classes are absent.

The key design principle is "back off in the presence of user configuration." Every auto-configured `@Bean` method that a user might reasonably want to customize is annotated with `@ConditionalOnMissingBean`. If the application declares a bean of the same type, the auto-configured one is skipped. This is how users customize Spring Boot behavior: declare your own bean, and Boot's auto-configuration yields to it.

Auto-configuration classes have a fixed ordering relative to each other, expressed via `@AutoConfiguration(before=..., after=...)` or the older `@AutoConfigureBefore`/`@AutoConfigureAfter` annotations. This ordering is not guaranteed to match class declaration order or alphabetical order — it is derived from the explicit dependency graph expressed in these annotations. Understanding this ordering is essential when writing custom auto-configurations that should run before or after Boot's own.

## Key Concepts

### AutoConfiguration.imports

```text
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

This file (one fully qualified class name per line) is what `AutoConfigurationImportSelector` reads to discover all auto-configuration candidates. Spring Boot ships with hundreds of entries here. Adding a custom entry to this file in your own jar is how you register a custom auto-configuration:

```text
com.example.mylib.autoconfigure.MyServiceAutoConfiguration
```

Prior to Spring Boot 2.7, this was done via `META-INF/spring.factories`:
```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
  com.example.mylib.autoconfigure.MyServiceAutoConfiguration
```

Both mechanisms are supported in Boot 3.x for backward compatibility, but the `.imports` file is the current standard.

### @Conditional Hierarchy Used by Boot

| Annotation | Description |
|---|---|
| `@ConditionalOnClass` | Condition passes if all named classes are present on the classpath |
| `@ConditionalOnMissingClass` | Condition passes if the named class is absent |
| `@ConditionalOnBean` | Condition passes if a bean of the named type/name exists |
| `@ConditionalOnMissingBean` | Condition passes if no bean of the named type/name exists |
| `@ConditionalOnProperty` | Condition passes if a property equals an expected value |
| `@ConditionalOnWebApplication` | Condition passes if running as a web application |
| `@ConditionalOnNotWebApplication` | Condition passes if not running as a web application |
| `@ConditionalOnExpression` | Condition passes if a SpEL expression evaluates to true |
| `@ConditionalOnSingleCandidate` | Condition passes if exactly one bean of the type exists |
| `@ConditionalOnResource` | Condition passes if a classpath resource exists |
| `@ConditionalOnJava` | Condition passes if running on a specific Java version |

The conditions are evaluated lazily during the `BeanFactoryPostProcessor` phase. `@ConditionalOnClass` is particularly important: it prevents a `ClassNotFoundException` at wiring time when an optional dependency is absent from the classpath.

### Writing a Custom Auto-Configuration

```java
@AutoConfiguration
// Back off if the user has provided their own GreetingService
@ConditionalOnMissingBean(GreetingService.class)
// Only configure if the GreetingService class itself is on the classpath
@ConditionalOnClass(GreetingService.class)
// Allow the user to disable auto-configuration entirely
@ConditionalOnProperty(prefix = "mylib.greeting", name = "enabled",
                       havingValue = "true", matchIfMissing = true)
public class GreetingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GreetingService greetingService(GreetingProperties properties) {
        return new DefaultGreetingService(properties.getMessage());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConfigurationPropertiesBinding
    public GreetingProperties greetingProperties() {
        return new GreetingProperties();
    }
}
```

### Debugging Auto-Configuration

Run the application with `--debug` or set `logging.level.org.springframework.boot.autoconfigure=DEBUG` to activate the `ConditionEvaluationReport`. This report lists three categories:

```text
=========================
AUTO-CONFIGURATION REPORT
=========================

Positive matches (applied):
   DataSourceAutoConfiguration matched:
      - @ConditionalOnClass found required classes 'javax.sql.DataSource'
      ...

Negative matches (not applied):
   MongoAutoConfiguration:
      Did not match:
         - @ConditionalOnClass did not find required class 'com.mongodb.MongoClient'

Exclusions:
   None

Unconditional classes:
   org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
```

Exclusions can be declared in `@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)` or in `application.yaml`:

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
```

### Auto-Configuration Ordering

```java
@AutoConfiguration(after = DataSourceAutoConfiguration.class,
                   before = HibernateJpaAutoConfiguration.class)
public class MyJpaHelperAutoConfiguration { ... }
```

`@AutoConfigureOrder` sets a numeric order relative to other auto-configuration classes, analogous to `@Order` for normal beans. A lower value means higher priority (runs earlier). This is separate from `before`/`after` dependency-based ordering.

## Gotchas

`@ConditionalOnMissingBean` evaluates at the point in the `BeanFactory` post-processing phase when the auto-configuration class is processed. If a user bean is declared in a `@Configuration` class that is imported after the auto-configuration is evaluated, the condition may not see it and both beans will be registered. Auto-configuration should always run after user configuration, which the `AutoConfigurationImportSelector` deferred-loading strategy ensures — but custom `@Import` usage can circumvent this.

Writing `@ConditionalOnClass` referencing a class that is not on the classpath at compile time requires using the string form: `@ConditionalOnClass(name = "com.example.SomeClass")`. Using the class literal form causes a `NoClassDefFoundError` during annotation processing.

Auto-configuration classes must not be picked up by component scanning. If the auto-configuration jar's package is within the scan path of a consuming application, both the auto-configuration class and its beans will be registered twice — once via auto-configuration and once via scanning. Convention is to place auto-configuration classes in a package like `com.example.mylib.autoconfigure` that is not the root package of any application.

The `@AutoConfiguration` annotation (introduced in Boot 2.7) is functionally equivalent to `@Configuration(proxyBeanMethods = false)` plus `@AutoConfigureBefore`/`@AutoConfigureAfter` support. The `proxyBeanMethods = false` is intentional: auto-configuration classes rarely call other `@Bean` methods within themselves, and disabling CGLIB proxying reduces startup time at scale.

When excluding an auto-configuration class with `spring.autoconfigure.exclude`, the exclusion applies to the specific class named, not to its transitive imports. If an auto-configuration class imports another via `@Import`, the imported class is excluded only if it is also listed explicitly. This can leave partial configurations active after an exclusion.
