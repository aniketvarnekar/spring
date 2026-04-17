# Actuator

## Overview

Spring Boot Actuator exposes operational endpoints over HTTP (and JMX) that provide visibility into a running application's health, configuration, metrics, thread state, bean definitions, and more. It is the primary mechanism for integrating a Spring Boot application with infrastructure-level health checks, monitoring systems like Prometheus, and ops dashboards. Adding the `spring-boot-starter-actuator` dependency activates Actuator; exposing individual endpoints is controlled by configuration.

The endpoint model distinguishes between two properties: enabled (whether the endpoint bean exists at all) and exposed (whether it is accessible over HTTP or JMX). By default, all built-in endpoints are enabled but only `/health` and `/info` are exposed over HTTP. Exposing all endpoints is a security risk in production and should be done only after configuring appropriate authentication.

Actuator integrates tightly with Micrometer, a vendor-neutral metrics facade that ships with Spring Boot. Micrometer provides dimensional metrics (counters, gauges, timers, distribution summaries) and ships backends (registries) for Prometheus, Datadog, Graphite, InfluxDB, and many others. Adding a registry dependency (e.g., `micrometer-registry-prometheus`) causes metrics to be automatically exported in the registry's format via the `/actuator/metrics` or `/actuator/prometheus` endpoints.

Custom extension of Actuator is straightforward: implement `HealthIndicator` to add domain-specific health checks, and annotate a class with `@Endpoint` to create entirely new operational endpoints with typed read and write operations. Both mechanisms participate in the standard Actuator security and exposure configuration.

## Key Concepts

### Built-in Endpoints

| Endpoint | Path | Description |
|---|---|---|
| `health` | `/actuator/health` | Aggregated health status from all HealthIndicators |
| `info` | `/actuator/info` | Arbitrary application info from `InfoContributor` beans |
| `metrics` | `/actuator/metrics/{name}` | Micrometer metric values |
| `env` | `/actuator/env` | PropertySources and their values (sensitive!) |
| `beans` | `/actuator/beans` | All Spring beans in the context |
| `conditions` | `/actuator/conditions` | Auto-configuration condition evaluation report |
| `configprops` | `/actuator/configprops` | Bound `@ConfigurationProperties` values |
| `mappings` | `/actuator/mappings` | All `@RequestMapping` handlers |
| `loggers` | `/actuator/loggers/{name}` | Read and change log levels at runtime |
| `threaddump` | `/actuator/threaddump` | Current thread dump |
| `heapdump` | `/actuator/heapdump` | Heap dump download |
| `prometheus` | `/actuator/prometheus` | Metrics in Prometheus format (requires registry) |

### Exposure Configuration

```yaml
management:
  endpoints:
    web:
      exposure:
        # Expose all endpoints — restrict access with Spring Security instead
        include: "*"
        # Or list specific endpoints:
        # include: health,info,metrics,prometheus
        exclude: heapdump,threaddump

  endpoint:
    health:
      # Show full health detail — restrict to authenticated users in production
      show-details: always
      # Show component names in the health response
      show-components: always

  # Change the actuator base path (default: /actuator)
  web:
    base-path: /management
```

### Security Considerations

Actuator endpoints that expose internal state (`env`, `beans`, `configprops`, `conditions`, `heapdump`) must never be publicly accessible. The recommended approach with Spring Security:

```java
@Bean
public SecurityFilterChain actuatorSecurity(HttpSecurity http) throws Exception {
    return http
        .securityMatcher(EndpointRequest.toAnyEndpoint())
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class))
                .permitAll()
            .anyRequest()
                .hasRole("ACTUATOR"))
        .httpBasic(Customizer.withDefaults())
        .build();
}
```

### Custom HealthIndicator

```java
@Component
public class ExternalServiceHealthIndicator implements HealthIndicator {

    private final ExternalServiceClient client;

    public ExternalServiceHealthIndicator(ExternalServiceClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            boolean reachable = client.ping();
            if (reachable) {
                return Health.up()
                        .withDetail("service", "external-payments")
                        .withDetail("latency", client.lastLatencyMs() + "ms")
                        .build();
            }
            return Health.down()
                    .withDetail("service", "external-payments")
                    .withDetail("reason", "ping returned false")
                    .build();
        } catch (Exception ex) {
            return Health.down(ex).build();
        }
    }
}
```

The aggregate health status (`UP`, `DOWN`, `OUT_OF_SERVICE`, `UNKNOWN`) is computed from the status of all registered `HealthIndicator` beans. The default aggregation strategy uses severity ordering: `DOWN` takes precedence over `OUT_OF_SERVICE`, which takes precedence over `UNKNOWN`, which takes precedence over `UP`.

### Custom @Endpoint

```java
@Component
@Endpoint(id = "featureflags")
public class FeatureFlagsEndpoint {

    private final FeatureFlagService flagService;

    public FeatureFlagsEndpoint(FeatureFlagService flagService) {
        this.flagService = flagService;
    }

    @ReadOperation
    public Map<String, Boolean> flags() {
        // HTTP GET /actuator/featureflags
        return flagService.getAllFlags();
    }

    @ReadOperation
    public Boolean flag(@Selector String name) {
        // HTTP GET /actuator/featureflags/{name}
        return flagService.getFlag(name);
    }

    @WriteOperation
    public void setFlag(@Selector String name, boolean enabled) {
        // HTTP POST /actuator/featureflags/{name} with body {"enabled": true}
        flagService.setFlag(name, enabled);
    }
}
```

`@Endpoint` works for both HTTP and JMX. Use `@WebEndpoint` for HTTP-only endpoints and `@JmxEndpoint` for JMX-only. `@ReadOperation` maps to HTTP GET, `@WriteOperation` to HTTP POST, and `@DeleteOperation` to HTTP DELETE.

### Micrometer Metrics

```java
@Service
public class OrderService {

    private final Counter orderCounter;
    private final Timer orderTimer;

    public OrderService(MeterRegistry registry) {
        // Tags enable filtering and grouping in dashboards
        this.orderCounter = Counter.builder("orders.placed")
                .description("Total orders placed")
                .tag("region", "us-east")
                .register(registry);

        this.orderTimer = Timer.builder("orders.processing.time")
                .description("Time to process an order")
                .register(registry);
    }

    public Order placeOrder(OrderRequest request) {
        return orderTimer.record(() -> {
            Order order = process(request);
            orderCounter.increment();
            return order;
        });
    }
}
```

## Gotchas

Exposing `management.endpoints.web.exposure.include=*` in a production environment without Spring Security is a critical security vulnerability. The `env` endpoint exposes environment variables and property sources — including passwords, API keys, and secrets — to anyone who can reach the actuator port. Always secure or restrict the management port.

The `health` endpoint's aggregate `DOWN` status returns HTTP 503 by default, not 200. Load balancers and container orchestrators that interpret a 503 as a failure will immediately route traffic away from or restart the instance. This is the intended behavior for the `/health` endpoint but can cause instability if a non-critical health indicator (like a cache or a secondary service) flips to `DOWN`. Mark non-critical indicators with status `UNKNOWN` or `OUT_OF_SERVICE`, or exclude them from the aggregation with `management.health.<indicator-id>.enabled=false`.

Custom `@Endpoint` operations with `@Selector` parameters accept the selector value as a path segment. If the selector value contains characters that are significant to the URL (slashes, question marks), path handling may not behave as expected. Spring encodes path segments but the exact behavior depends on whether the MVC or WebFlux stack is in use.

The `info` endpoint is empty by default. To populate it, add `info.*` properties or implement `InfoContributor` beans. In a CI/CD pipeline, injecting `info.app.version` and `info.build.time` from the build tool is a common practice — it makes the running artifact version visible without logging into the host.

Micrometer's `@Timed` annotation on Spring MVC controllers and `@RestController` methods requires `TimedAspect` to be registered as a bean. Without it, `@Timed` annotations are silently ignored. Spring Boot does not register `TimedAspect` automatically; declare it as a `@Bean` in a configuration class.
