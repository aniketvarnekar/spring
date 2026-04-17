# Testing

## Overview

Spring Boot's testing support builds on Spring Framework's `spring-test` module and adds application-level conveniences: a fast way to start the full application context, a set of "slice" test annotations that load only the relevant slice of the application (web layer, persistence layer, etc.), and `@MockBean` for replacing beans with Mockito mocks within the Spring context. Understanding the performance and correctness trade-offs between these approaches is necessary for writing a test suite that is both reliable and fast.

`@SpringBootTest` loads a complete `ApplicationContext` — every auto-configuration, every component-scanned bean, and every `@Configuration` class. It is the highest-fidelity test but also the slowest to start. Slice tests (`@WebMvcTest`, `@DataJpaTest`, etc.) load a minimal context restricted to the layer under test, with everything else excluded or mocked. The slice approach is typically the right default for unit-style integration tests of individual layers.

Spring caches `ApplicationContext` instances across tests within a JVM run. Two test classes that produce the same context configuration (same component set, same `@MockBean` declarations, same active profiles) share the same context — no duplicate initialization cost. Context caching is the primary mechanism that keeps a large test suite's overall runtime manageable. `@MockBean` and `@SpyBean` both break context caching because they produce a context with a different configuration, so overuse of `@MockBean` is a common cause of slow test suites.

`ApplicationContextRunner` is a test utility for testing auto-configurations and conditional logic in complete isolation, without starting a Spring Boot application at all. It is the correct tool for testing that a custom auto-configuration backs off in the presence of a user-declared bean, or that it activates only when a specific class is on the classpath.

## Key Concepts

### @SpringBootTest Modes

```java
// Default — MOCK mode: loads full ApplicationContext, MockMvc is used for web requests
@SpringBootTest
class FullContextTest { ... }

// RANDOM_PORT — starts a real embedded server on a random port
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealServerTest {
    @LocalServerPort
    int port;  // injected with the actual random port
}

// DEFINED_PORT — starts a real embedded server on server.port (default 8080)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
class DefinedPortTest { ... }

// NONE — no web environment at all; loads ApplicationContext without a web layer
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ServiceLayerTest { ... }
```

### @WebMvcTest

Tests only the Spring MVC layer: controllers, `ControllerAdvice`, `Filter`, `WebMvcConfigurer`, and `HandlerMethodArgumentResolver`. The service and repository layers are not loaded.

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // @MockBean replaces the service with a Mockito mock in the context
    @MockBean
    private OrderService orderService;

    @Test
    void placeOrder_returnsCreated() throws Exception {
        given(orderService.placeOrder(any())).willReturn(new OrderResponse("ORD-001"));

        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\": \"CUST-1\", \"amount\": 99.99}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderId").value("ORD-001"));
    }
}
```

### @DataJpaTest

Tests only the JPA layer: entities, repositories, and the underlying `DataSource` and `EntityManagerFactory`. A real (usually H2) database is used by default, and each test method runs in a transaction that is rolled back.

```java
@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByCustomerId_returnsMatchingOrders() {
        entityManager.persist(new Order("ORD-1", "CUST-42", BigDecimal.TEN));
        entityManager.flush();

        List<Order> orders = orderRepository.findByCustomerId("CUST-42");

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getId()).isEqualTo("ORD-1");
    }
}
```

To use the real database instead of H2:
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryRealDbTest { ... }
```

### @MockBean vs @SpyBean

`@MockBean` replaces a bean in the `ApplicationContext` with a Mockito mock. The mock has no real behavior unless explicitly configured with `given(...)` or `when(...)`.

`@SpyBean` replaces a bean with a Mockito spy — the real implementation is used by default, but individual methods can be stubbed. Use `@SpyBean` when you want to verify interactions with a real implementation.

```java
@SpringBootTest
class AuditTest {

    @SpyBean  // real AuditService, but we can verify method calls
    private AuditService auditService;

    @Test
    void placeOrder_auditsTheAction() {
        orderService.placeOrder(new OrderRequest("CUST-1", BigDecimal.TEN));

        verify(auditService).record(eq("ORDER_PLACED"), any());
    }
}
```

### MockMvc vs TestRestTemplate

| | MockMvc | TestRestTemplate |
|---|---|---|
| Server started | No (mock dispatcher) | Yes (real embedded server) |
| Works with | `@WebMvcTest`, `@SpringBootTest(MOCK)` | `@SpringBootTest(RANDOM_PORT)` |
| Filters | Executed | Executed |
| Full HTTP | No | Yes |
| Suitable for | Controller and filter logic | End-to-end HTTP behavior, HTTP semantics |

```java
// TestRestTemplate usage with RANDOM_PORT
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderApiTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthCheck() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

### @TestPropertySource

Override properties for a specific test or test class:

```java
@SpringBootTest
@TestPropertySource(properties = {
    "payment.gateway-url=http://localhost:9999",
    "payment.retry-count=1"
})
class PaymentServiceTest { ... }
```

For properties that come from a separate file:
```java
@TestPropertySource(locations = "classpath:test-payment.properties")
```

### ApplicationContextRunner

For testing auto-configuration logic in isolation:

```java
class MyAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(MyServiceAutoConfiguration.class));

    @Test
    void autoConfigures_whenClassPresent() {
        contextRunner.run(ctx -> {
            assertThat(ctx).hasSingleBean(MyServiceClient.class);
        });
    }

    @Test
    void backsOff_whenUserProvidesBean() {
        contextRunner
            .withUserConfiguration(UserMyServiceConfig.class)
            .run(ctx -> {
                assertThat(ctx).hasSingleBean(MyServiceClient.class);
                // Ensure the user's bean won — not the auto-configured one
                assertThat(ctx.getBean(MyServiceClient.class))
                        .isInstanceOf(UserMyServiceConfig.CustomClient.class);
            });
    }
}
```

## Gotchas

Every unique `@MockBean` or `@SpyBean` declaration in a test class causes a new `ApplicationContext` to be created for that class, bypassing the context cache. If twenty test classes each mock a different subset of the same beans, twenty separate context loads occur. The solution is to centralize mock configuration in a shared base test class or `@TestConfiguration` class.

`@SpringBootTest` without any class parameter searches upward from the test class package to find the `@SpringBootApplication` class. If the test is in a sub-package that contains no `@SpringBootApplication`, it may find one unexpectedly in a parent package, or fail to find one at all. Always verify that `@SpringBootTest` loads the intended context.

`@DataJpaTest` enables transaction rollback by default, which means `@Transactional` wraps each test method and rolls back at the end. If the service under test creates a new transaction (`REQUIRES_NEW` propagation), that inner transaction commits independently of the test transaction, leaving data in the database across tests. Prefer `@Sql` annotations for explicit data setup and teardown in this case.

`TestRestTemplate` does not throw exceptions on 4xx and 5xx responses — it returns the `ResponseEntity` with the error status code. This is intentional behavior, designed for testing error responses. If you expect a 404 and `assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)`, the assertion works correctly. Using vanilla `RestTemplate` in tests will throw `HttpClientErrorException` on error status codes, which is often not what you want.

In-memory databases used by `@DataJpaTest` may have different behavior from the production database, particularly around DDL (constraint checking, GENERATED columns), case sensitivity, and SQL dialect. Schema validation failures that would catch production bugs may pass silently on H2. Annotate critical persistence tests with `@AutoConfigureTestDatabase(replace = NONE)` to run against the real database and rely on Testcontainers or a CI database for setup.
