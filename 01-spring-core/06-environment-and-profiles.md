# Environment and Profiles

## Overview

The `Environment` abstraction in Spring provides a unified API for accessing externalized configuration and for expressing conditions under which beans are activated. It integrates two concepts: properties (key-value configuration from any number of sources) and profiles (named sets of bean definitions that are included or excluded at runtime). Both concepts are surfaced through the `Environment` interface, which is available as a first-class bean in any `ApplicationContext`.

The property resolution system is built on `PropertySource` objects, each representing one source of key-value pairs. Sources are arranged in a priority-ordered list, and the first source that contains a given key wins. Spring Boot extends this with a rich, documented `PropertySource` hierarchy that covers environment variables, system properties, `application.yaml`/`application.properties` files, profile-specific files, command-line arguments, and more. Knowing this hierarchy is essential for predicting which value takes effect when the same key appears in multiple places.

`@Value` and `@ConfigurationProperties` are the two primary mechanisms for injecting property values into beans. `@Value` is simpler and suitable for one-off property access; `@ConfigurationProperties` binds a whole namespace of related properties to a typed object with validation support. For anything beyond two or three properties, `@ConfigurationProperties` is almost always the better choice.

Profiles extend the property system into the bean definition space. A `@Profile` annotation on a `@Bean` method or `@Component` class means the bean is registered only when the named profile is active. This enables environment-specific wiring — different `DataSource` beans for development and production, different service implementations for mock and live environments — without scattering `if` statements through configuration code.

## Key Concepts

### PropertySource Hierarchy (Spring Boot)

From highest priority to lowest (higher overrides lower):

```text
1.  Command-line arguments (--server.port=9090)
2.  SPRING_APPLICATION_JSON (env var or system property)
3.  OS environment variables
4.  Java system properties (-Dserver.port=9090)
5.  JNDI attributes
6.  ServletContext / ServletConfig init parameters
7.  @TestPropertySource (in tests)
8.  Profile-specific files: application-{profile}.yaml
9.  Application files: application.yaml / application.properties
10. @PropertySource annotations on @Configuration classes
11. Default properties (SpringApplication.setDefaultProperties)
```

Understanding this order is critical for debugging configuration issues. A value set in `application.yaml` will always be overridden by an OS environment variable with the same key (after relaxed binding normalization).

### @Value

```java
@Component
public class EmailService {

    // Simple property injection
    @Value("${email.from-address}")
    private String fromAddress;

    // With default value if property is absent
    @Value("${email.batch-size:50}")
    private int batchSize;

    // SpEL expression
    @Value("#{systemProperties['user.timezone']}")
    private String timezone;
}
```

`@Value` expressions are resolved at injection time, not lazily. If a property is missing and has no default, a `BeanCreationException` is thrown at startup. The `${...}` syntax resolves from `PropertySources`; the `#{...}` syntax is SpEL and has access to beans, system properties, and environment.

### @ConfigurationProperties

```java
@ConfigurationProperties(prefix = "mail")
@Validated
public class MailProperties {

    @NotBlank
    private String host;

    private int port = 25;

    @Valid
    private Smtp smtp = new Smtp();

    // getters and setters required for binding

    public static class Smtp {
        private boolean auth;
        private boolean starttlsEnable;
        // getters and setters
    }
}
```

```yaml
mail:
  host: smtp.example.com
  port: 587
  smtp:
    auth: true
    starttls-enable: true   # relaxed binding: camelCase, kebab-case, and UPPER_SNAKE all work
```

Register the class as a bean with `@EnableConfigurationProperties(MailProperties.class)` in a `@Configuration` class, or annotate the class itself with `@Configuration` (though the latter was deprecated in Spring Boot 2.4 in favor of the former or `@ConfigurationPropertiesScan`).

`@NestedConfigurationProperty` annotates a field whose type is itself a complex properties object defined in an external class (i.e., not a static inner class). Without it, the nested structure is not picked up by the configuration metadata processor for IDE auto-completion.

### Relaxed Binding

Spring Boot's binding engine normalizes property keys, so all of the following resolve to the same property:

```text
mail.smtp.starttls-enable    (kebab-case — preferred in files)
mail.smtp.starttlsEnable     (camelCase)
mail.smtp.starttls_enable    (underscore)
MAIL_SMTP_STARTTLSENABLE     (upper snake — preferred for environment variables)
```

`@Value` does not apply relaxed binding. It resolves the exact string you provide. Prefer `@ConfigurationProperties` when consuming configuration from environment variables where exact casing may vary.

### Profiles

```java
@Configuration
public class DataSourceConfig {

    @Bean
    @Profile("dev")
    public DataSource h2DataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
    }

    @Bean
    @Profile("prod")
    public DataSource postgresDataSource() {
        // constructed from @ConfigurationProperties-bound values
        return DataSourceBuilder.create().build();
    }
}
```

Activate profiles via:
- `spring.profiles.active=prod` in `application.yaml`
- `SPRING_PROFILES_ACTIVE=prod` environment variable
- `-Dspring.profiles.active=prod` JVM system property
- `SpringApplication.setAdditionalProfiles(...)` programmatically
- `@ActiveProfiles` in tests

Profile expressions extend the syntax beyond a single profile name:

```java
@Profile("prod & !eu")        // active when prod is active AND eu is not
@Profile("dev | test")        // active when either dev or test is active
@Profile("!prod")             // active when prod is not active
```

Profile-specific application files follow the naming convention `application-{profile}.yaml`. They are loaded in addition to `application.yaml` and override properties from the base file.

### @Conditional

`@Conditional` is the underlying mechanism for profile-based and all other conditional bean registration. Implement `Condition` to express arbitrary logic:

```java
public class OnDockerCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // Check for Docker environment indicator
        return Files.exists(Path.of("/.dockerenv"));
    }
}

@Bean
@Conditional(OnDockerCondition.class)
public MetricsExporter dockerMetricsExporter() { ... }
```

`ConditionContext` provides access to the `BeanDefinitionRegistry`, `ConfigurableListableBeanFactory`, `Environment`, `ResourceLoader`, and `ClassLoader` — giving a condition full visibility into the partially-constructed container state.

## Gotchas

`@PropertySource` on a `@Configuration` class is processed later than `application.yaml` and has lower priority in the Spring Boot hierarchy. A value defined in a file loaded via `@PropertySource` will be overridden by `application.yaml`. If the intent is to provide defaults that can be overridden, use `SpringApplication.setDefaultProperties()` instead.

`@Value` injection does not work in `BeanFactoryPostProcessor` implementations or in classes that are instantiated very early in the lifecycle (see the bean lifecycle section). The `PropertySourcesPlaceholderConfigurer` that resolves `${...}` is itself a `BeanFactoryPostProcessor` and runs before other beans, but it cannot inject into its own class or into other `BeanFactoryPostProcessor` beans.

Profile-specific `application-{profile}.yaml` files do not replace the base `application.yaml` — they merge with it, with the profile-specific file taking higher precedence. Properties absent from the profile file retain their value from the base file. Misunderstanding this causes confusion when a property that should be environment-specific is left in the base file and not overridden.

`@ConfigurationProperties` uses JavaBean conventions and requires public setters (or constructor binding, enabled via `@ConstructorBinding` or as the default in Spring Boot 3.x when using records). A missing setter causes the property to silently not be bound, with the field retaining its default value. Enable `@Validated` and add `@NotNull` to critical properties to catch silent binding failures at startup.

Profiles are additive — all active profiles apply simultaneously. If `spring.profiles.active=dev,security`, beans from both the `dev` profile and the `security` profile are registered. This means that profile-specific beans can conflict if two active profiles each define a bean of the same type without `@Primary` or `@Qualifier`. Design profiles to compose cleanly or use `@Profile("dev & security")` to define beans that are active only in their intersection.
