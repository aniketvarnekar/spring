# Configuration Properties

## Overview

`@ConfigurationProperties` is the recommended mechanism for binding a namespace of related configuration keys to a typed Java object. Unlike `@Value`, which injects individual properties into individual fields, `@ConfigurationProperties` binds an entire subtree of the property hierarchy to a class's fields, supports nested structures, validates the bound values with Bean Validation, and integrates with IDE tooling via generated metadata. It is the right choice for any configuration that has more than two or three related keys.

The binding process converts property keys to Java field names using relaxed binding rules, which normalize kebab-case, camelCase, underscore-separated, and UPPER_SNAKE_CASE representations to the same canonical form. This makes YAML files (where `my-property` is idiomatic) and environment variables (where `MY_PROPERTY` is idiomatic) both bind correctly to the same Java field named `myProperty`. `@Value` does not apply relaxed binding — it requires the exact key as written.

Spring Boot 3.x defaults to constructor binding for `@ConfigurationProperties` classes. A class with a single all-args constructor (or one annotated `@ConstructorBinding`) is bound via constructor rather than setters, enabling immutable properties objects. Records are the natural fit for this pattern.

Configuration metadata generation via the annotation processor enables first-class IDE support: key completion, type checking, and Javadoc-derived documentation tooltips in `application.yaml` and `application.properties`. Including the processor as an optional compile-time dependency is a low-effort improvement for any library that ships a `@ConfigurationProperties` class.

## Key Concepts

### Basic Binding

```yaml
# application.yaml
payment:
  gateway-url: https://api.payments.example.com
  api-key: secret-key-123
  timeout: 10s
  retry-count: 3
  supported-currencies:
    - USD
    - EUR
    - GBP
```

```java
@ConfigurationProperties(prefix = "payment")
@Validated
public class PaymentProperties {

    @NotBlank
    private String gatewayUrl;

    @NotBlank
    private String apiKey;

    @DurationUnit(ChronoUnit.SECONDS)
    private Duration timeout = Duration.ofSeconds(5);

    @Min(0) @Max(10)
    private int retryCount = 0;

    private List<String> supportedCurrencies = List.of("USD");

    // getters and setters (or use a record / @ConstructorBinding)
}
```

Register the class for binding in a `@Configuration` class:

```java
@Configuration
@EnableConfigurationProperties(PaymentProperties.class)
public class PaymentConfig { }
```

Alternatively, annotate the class itself with `@Component` or use `@ConfigurationPropertiesScan` on the application class to auto-discover all `@ConfigurationProperties` classes in the package tree.

### Nested Properties

```java
@ConfigurationProperties(prefix = "mail")
public class MailProperties {

    private String host;
    private int port = 25;
    private Smtp smtp = new Smtp();

    // Standard getters and setters

    public static class Smtp {
        private boolean auth;
        // "starttls-enable" in YAML binds to "starttlsEnable" here
        private boolean starttlsEnable;
        // getters and setters
    }
}
```

For nested types defined outside the enclosing class (e.g., from a library), annotate the field with `@NestedConfigurationProperty` to tell the metadata processor to recurse into that type:

```java
public class MailProperties {
    @NestedConfigurationProperty
    private SslBundle ssl;
}
```

### Constructor Binding (Records)

```java
@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(
        @NotBlank String gatewayUrl,
        @NotBlank String apiKey,
        Duration timeout,
        @Min(0) int retryCount) {

    // No setters needed — bound via constructor
    // Default values are not expressible in record constructor parameters
    // directly; use a companion @Bean to supply defaults when needed
}
```

Constructor binding requires that exactly one constructor is present (or one is annotated with `@ConstructorBinding`). The container calls the constructor once during binding.

### Relaxed Binding Rules

All of the following property keys bind to the field `myProperty`:

```
myservice.my-property      (kebab-case — preferred for .yaml files)
myservice.myProperty       (camelCase)
myservice.my_property      (underscore)
MYSERVICE_MYPROPERTY       (UPPER_SNAKE_CASE — preferred for env vars)
```

Only `@ConfigurationProperties` applies relaxed binding. `@Value("${myservice.my-property}")` requires the exact key string as it appears in the property source.

### Validation

Apply `@Validated` to the `@ConfigurationProperties` class and any JSR-380 constraint annotation to its fields. Validation runs at application startup, before the application is ready to serve requests:

```java
@ConfigurationProperties(prefix = "server.config")
@Validated
public class ServerConfig {

    @NotNull
    @Pattern(regexp = "https?://.*")
    private String baseUrl;

    @Min(1024) @Max(65535)
    private int port;

    @Valid  // triggers validation of nested object's constraints
    private DatabaseConfig database = new DatabaseConfig();
}
```

If validation fails, the application refuses to start and prints a descriptive error message listing every violated constraint. This is far preferable to discovering a misconfiguration at runtime.

### IDE Metadata Generation

```xml
<!-- pom.xml — optional dependency; not included at runtime -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
    <optional>true</optional>
</dependency>
```

After a build, `target/classes/META-INF/spring-configuration-metadata.json` is generated. Check this file into source control for IDEs that read it. The generated metadata documents the prefix, all keys, their types, default values (inferred from field initializers), and any Javadoc present on the class.

## Gotchas

A missing setter on a mutable `@ConfigurationProperties` class causes the corresponding property to be silently ignored during binding. The field retains its default value without any warning. Adding `@Validated` with `@NotNull` on critical fields turns silent ignoring into a startup failure.

Properties bound with `@ConfigurationProperties` are not available during `BeanFactoryPostProcessor` initialization. If a `BeanFactoryPostProcessor` attempts to inject or read a `@ConfigurationProperties` bean, it may receive an un-bound, default-valued instance because property binding has not yet run. Use `Environment.getProperty()` directly in `BeanFactoryPostProcessor` implementations.

`Duration` fields are bound from strings like `10s`, `5m`, `2h` using `@DurationUnit` to set the default unit. Without `@DurationUnit`, the raw `ISO-8601` duration format (`PT10S`) is expected. Mixing the two formats in a team causes confusion; pick one convention and document it.

The `@ConfigurationProperties` prefix must use kebab-case. Using camelCase or underscore in the prefix itself (e.g., `prefix = "myService"` instead of `prefix = "my-service"`) is technically accepted by the binder but generates a warning in recent Boot versions and does not match the conventions that relaxed binding normalization assumes.

When using constructor binding with records, Spring cannot set default values for missing properties — the record constructor is called with `null` for missing string fields or `0` for missing numeric fields, and the constraints are checked after construction. If some fields are truly optional with defaults, use a standard class with setters and field initializers rather than a record.
