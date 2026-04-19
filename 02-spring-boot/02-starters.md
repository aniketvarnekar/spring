# Starters

## Overview

A Spring Boot starter is a curated set of dependency descriptors that eliminates the need for manual dependency management. When you add `spring-boot-starter-web` to a project, you receive Spring MVC, Jackson, an embedded Tomcat, and all of their transitive dependencies at tested, compatible versions — without specifying a single individual version. The starter's sole job is to pull in the right jars; the actual auto-configuration lives separately.

The separation between the starter (a descriptor-only jar with no code) and the auto-configuration module (which contains `@Configuration` classes) is a deliberate design principle. It means a library author can ship auto-configuration that Spring Boot users activate by adding a single starter dependency, while the auto-configuration jar itself has no dependency on the starter — it can be used without it. The starter just assembles the transitive dependency set.

Spring Boot's own starters follow a naming convention: `spring-boot-starter-*` for official starters, and the recommendation is `*-spring-boot-starter` for third-party starters (e.g., `mybatis-spring-boot-starter`). This naming convention is purely advisory but helps users distinguish official from community starters in dependency declarations.

Understanding what a starter includes is essential for diagnosing classpath conflicts and for excluding transitive dependencies that clash with your application's requirements. The `mvn dependency:tree` command (or the equivalent in Gradle) reveals what each starter pulls in, and `spring-boot-starter-parent`'s `dependencyManagement` section sets the compatible version for each artifact.

## Key Concepts

### What a Starter Contains

A starter jar typically contains only:
- A `pom.xml` (or `build.gradle`) with curated dependencies
- Optionally, a `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` if it bundles its own auto-configuration

When a starter bundles auto-configuration directly (the simpler approach), the jar has both. When they are separated (the library pattern), you have:

```text
my-service-spring-boot-starter/
  pom.xml  (depends on my-service-spring-boot-autoconfigure + my-service-core)

my-service-spring-boot-autoconfigure/
  pom.xml  (depends on spring-boot-autoconfigure, optionally on my-service-core)
  src/main/java/com/example/myservice/autoconfigure/
    MyServiceAutoConfiguration.java
  src/main/resources/META-INF/spring/
    org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### Creating a Custom Starter

**Step 1 — Auto-configuration module:**

```java
// my-service-spring-boot-autoconfigure module
@AutoConfiguration
@ConditionalOnClass(MyServiceClient.class)
@EnableConfigurationProperties(MyServiceProperties.class)
public class MyServiceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MyServiceClient myServiceClient(MyServiceProperties props) {
        return MyServiceClient.builder()
                .baseUrl(props.getBaseUrl())
                .timeout(props.getTimeout())
                .build();
    }
}
```

```java
@ConfigurationProperties(prefix = "myservice")
public class MyServiceProperties {
    private String baseUrl = "http://localhost:8080";
    private Duration timeout = Duration.ofSeconds(5);
    // getters and setters
}
```

```text
# src/main/resources/META-INF/spring/
# org.springframework.boot.autoconfigure.AutoConfiguration.imports
com.example.myservice.autoconfigure.MyServiceAutoConfiguration
```

**Step 2 — Starter module pom.xml:**

```xml
<dependencies>
    <!-- The auto-configuration module -->
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>my-service-spring-boot-autoconfigure</artifactId>
    </dependency>
    <!-- The core library that MyServiceClient lives in -->
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>my-service-core</artifactId>
    </dependency>
    <!-- Declare spring-boot-autoconfigure as optional — consuming apps
         must have it on the classpath themselves (via spring-boot-starter) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-autoconfigure</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

### Common Official Starters

| Starter | Key Dependencies |
|---|---|
| `spring-boot-starter` | spring-core, spring-context, spring-boot, logback |
| `spring-boot-starter-web` | spring-webmvc, jackson, embedded Tomcat |
| `spring-boot-starter-webflux` | spring-webflux, reactor-netty |
| `spring-boot-starter-data-jpa` | spring-data-jpa, hibernate-core, spring-jdbc |
| `spring-boot-starter-security` | spring-security-web, spring-security-config |
| `spring-boot-starter-test` | JUnit 5, Mockito, AssertJ, spring-test |
| `spring-boot-starter-actuator` | spring-boot-actuator, micrometer-core |
| `spring-boot-starter-validation` | hibernate-validator, jakarta.validation-api |
| `spring-boot-starter-cache` | spring-context-support (cache abstraction) |
| `spring-boot-starter-aop` | spring-aop, aspectjweaver |

### Excluding Transitive Dependencies

When a starter's transitive dependencies conflict (e.g., Tomcat vs Jetty):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-tomcat</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jetty</artifactId>
</dependency>
```

### Configuration Metadata for IDE Support

Generate configuration metadata so that IDEs can auto-complete and validate `myservice.*` properties:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-configuration-processor</artifactId>
    <optional>true</optional>
</dependency>
```

The annotation processor runs at compile time and generates `META-INF/spring-configuration-metadata.json` from `@ConfigurationProperties` classes. This file is bundled in the auto-configuration jar and read by IntelliJ IDEA and VS Code to provide property key completion and documentation.

## Gotchas

The auto-configuration module should declare `spring-boot-autoconfigure` as a `compileOnly` or `optional` dependency in Maven. If it is declared as a regular `compile` dependency, every consumer of your library transitively depends on the entire auto-configuration jar, even if they do not use Spring Boot. Optional dependencies do not appear in transitive dependency resolution.

`@ConditionalOnClass` in an auto-configuration class must reference classes from a dependency that is marked `optional` in the auto-configuration module's pom. If the class is on the compile classpath unconditionally, `@ConditionalOnClass` will always be true, and the auto-configuration will apply even when the consumer does not have the class at runtime — producing a `ClassNotFoundException`.

Starter ordering matters for conflict resolution. When two starters both pull in a library at different versions, Maven's nearest-definition rule (not Spring's) resolves the version. Declaring the desired version explicitly in the project's own `dependencyManagement` block overrides any transitive declaration, which is the reliable way to pin a version.

The `spring-boot-starter-parent` BOM manages versions only for the artifacts it knows about. Third-party libraries not in the BOM must have their versions declared explicitly. Forgetting this produces an error at build time (`'version' is missing for dependency`) if the library is not in any imported BOM.

Bundling auto-configuration directly in a starter (rather than a separate autoconfigure module) is fine for simple cases but makes it harder for consumers to use the auto-configuration without the full starter dependency set. If your library has complex or optional runtime dependencies, separating the modules gives consumers more flexibility.
