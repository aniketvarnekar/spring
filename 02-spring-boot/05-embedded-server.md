# Embedded Server

## Overview

Spring Boot's embedded server model bundles the web server inside the application jar rather than deploying a war file to an external container. The application owns the server, starts it, and shuts it down as part of the normal Spring lifecycle. This shifts the operational model from "manage a container that hosts applications" to "manage applications that embed a container," which is better suited to containerized and cloud-native deployments.

Spring Boot supports three embedded servlet containers — Tomcat (the default), Jetty, and Undertow — and the reactive Netty server for WebFlux applications. Switching between them requires only a dependency change: exclude the default server and add the desired one. The Spring Boot abstraction layer (`WebServerFactory`, `WebServer`) normalizes configuration differences between servers so that most customization is server-agnostic.

The embedded server starts within the Spring application context lifecycle. When `SpringApplication.run()` is called, the `ServletWebServerApplicationContext` creates a `WebServer` during the refresh phase, binds the `DispatcherServlet`, and starts the server. On context close, the server is stopped before the singleton destruction phase completes.

Customization of the embedded server is available at three levels: the `ServerProperties` binding (for common settings), `WebServerFactoryCustomizer<T>` (for programmatic adjustments), and direct factory configuration (for server-specific advanced settings). The `ServerProperties` binding is the highest-level and least fragile; the factory API provides access to server-specific options not covered by the common abstraction.

## Key Concepts

### Switching Between Servers

```xml
<!-- Exclude Tomcat from spring-boot-starter-web -->
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

<!-- Add Jetty instead -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jetty</artifactId>
</dependency>
```

| Server | Characteristics |
|---|---|
| Tomcat | Default; mature; blocking I/O; well-understood in enterprise environments |
| Jetty | Lighter weight than Tomcat; good HTTP/2 support; configurable threading model |
| Undertow | Low memory footprint; non-blocking core; used by WildFly; no JSP support |
| Netty (reactive) | For WebFlux only; fully non-blocking; not usable with Spring MVC |

### ServerProperties Configuration

```yaml
server:
  port: 8443
  address: 0.0.0.0

  # Servlet context path — prefixes all URL mappings
  servlet:
    context-path: /api

  # Connection and request limits
  tomcat:
    max-connections: 8192
    threads:
      max: 200
      min-spare: 10
    connection-timeout: 20s

  # Error page configuration
  error:
    include-message: always
    include-binding-errors: always
    include-stacktrace: on_param   # Never "always" in production
```

### WebServerFactoryCustomizer

For configuration beyond what `ServerProperties` covers, implement `WebServerFactoryCustomizer`:

```java
@Component
public class TomcatCustomizer
        implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        // Add a Tomcat connector — e.g., an HTTP connector alongside the HTTPS default
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setPort(8080);
        // Redirect HTTP to HTTPS by setting the redirect port on the HTTP connector
        connector.setRedirectPort(8443);
        factory.addAdditionalTomcatConnectors(connector);
    }
}
```

The generic `WebServerFactoryCustomizer<ConfigurableServletWebServerFactory>` works across all three servlet servers for common operations. Use a server-specific generic type parameter only when accessing server-specific APIs.

### TLS Configuration

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: changeit
    key-store-type: PKCS12
    key-alias: myapp
    # Restrict protocol versions — TLS 1.2 minimum in all modern deployments
    protocol: TLS
    enabled-protocols: TLSv1.3,TLSv1.2
    # Restrict cipher suites to approved algorithms
    ciphers: TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384
```

For mutual TLS (client certificate authentication):

```yaml
server:
  ssl:
    client-auth: need       # "want" accepts but does not require; "need" enforces
    trust-store: classpath:truststore.p12
    trust-store-password: changeit
```

### HTTP/2

```yaml
server:
  http2:
    enabled: true
```

HTTP/2 over TLS (h2) works automatically on Tomcat, Jetty, and Undertow when SSL is configured. HTTP/2 in cleartext (h2c) requires server-specific configuration. On Tomcat, h2c requires the APR connector; standard NIO does not support h2c.

### Graceful Shutdown

```yaml
server:
  shutdown: graceful    # Default is "immediate"

spring:
  lifecycle:
    # Maximum time to wait for in-flight requests to complete
    timeout-per-shutdown-phase: 30s
```

With `shutdown: graceful`, the server stops accepting new connections on SIGTERM but continues processing in-flight requests for up to `timeout-per-shutdown-phase`. After the grace period, any remaining requests are aborted.

## Gotchas

The `server.port=0` setting binds to a random available port, which is useful in tests. To discover the actual port at runtime, inject `@LocalServerPort` in `@SpringBootTest` tests, or use `ApplicationContext.getBean(WebServerApplicationContext.class).getWebServer().getPort()` in production code.

Changing `server.servlet.context-path` does not affect the actuator base path unless `management.server.base-path` is also updated (when Actuator runs on a separate port) or `management.endpoints.web.base-path` is adjusted. If the context path is `/api` and actuator remains at `/actuator`, health checks at `/actuator/health` are relative to the server root, not the context path.

`WebServerFactoryCustomizer` beans are applied after `ServerProperties` binding. A `customize()` method that sets a property already set by `ServerProperties` will override it. The ordering between multiple `WebServerFactoryCustomizer` beans is controlled by `@Order` or the `Ordered` interface.

When deploying to an external servlet container (as a war), the embedded server configuration (`ServerProperties`, `WebServerFactoryCustomizer`) is ignored because the external container controls the server. The `@SpringBootApplication` class must extend `SpringBootServletInitializer` for this deployment model, and the server-specific configuration must be moved to the container's own configuration files.

The `server.ssl.key-store: classpath:` prefix loads from the classpath, which is fine in development but means the keystore is bundled in the jar. In production, use `file:/path/to/keystore.p12` to reference a keystore managed externally from the artifact, which allows rotation without a code change or redeployment.
