# Controllers

## Overview

Spring MVC controllers are the request-handling components that map HTTP requests to Java methods and return responses. `@RestController` (which combines `@Controller` and `@ResponseBody`) is the standard for REST APIs: every method's return value is serialized directly to the HTTP response body. `@Controller` without `@ResponseBody` returns a view name for server-side rendering with a template engine. The two styles can coexist in the same application.

Response body serialization is handled by `HttpMessageConverter` implementations registered with `RequestMappingHandlerAdapter`. When a handler method returns a non-void value (and the handler or class is annotated with `@ResponseBody`), the adapter iterates through the registered converters looking for one that can write the return type to the response in a format acceptable to the client's `Accept` header. Jackson's `MappingJackson2HttpMessageConverter` handles JSON and is auto-configured by Spring Boot when `jackson-databind` is on the classpath.

The controller layer should be thin. Its job is to parse the incoming request, delegate to the service layer, and translate the service result into an HTTP response. Business logic, transaction management, and cross-cutting concerns do not belong in controller methods. This separation is enforced by the fact that `@Transactional` on a controller method is an anti-pattern — controllers are not the right boundary for transactions.

`@Controller` classes are singleton-scoped by default, which means they are shared across all requests. Storing per-request state in controller fields is a threading bug. All state needed for a request must flow through method parameters or the request/session scopes.

## Key Concepts

### @RestController vs @Controller

```java
// @RestController = @Controller + @ResponseBody
// Every method's return value is serialized to the response body.
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable String id) {
        // Return value is serialized to JSON automatically.
        // Response status: 200 OK.
        return orderService.findById(id);
    }
}

// @Controller with @ResponseBody on specific methods
@Controller
public class HybridController {

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("stats", fetchStats());
        return "dashboard";  // view name — resolved by ViewResolver
    }

    @GetMapping("/api/stats")
    @ResponseBody
    public StatsDto stats() {
        return fetchStats();  // serialized to JSON
    }
}
```

### HttpMessageConverter

`HttpMessageConverter<T>` converts between HTTP request/response streams and Java objects. The `RequestMappingHandlerAdapter` selects a converter based on the handler's parameter/return type and the request's `Content-Type` / response's `Accept` headers.

```text
Default converters (registered by Spring Boot with spring-boot-starter-web):

Priority  Converter                                  Handles
--------  -----------------------------------------  ---------------------------
1         StringHttpMessageConverter                 String types, text/plain
2         ByteArrayHttpMessageConverter              byte[], application/octet-stream
3         ResourceHttpMessageConverter               Resource, application/octet-stream
4         MappingJackson2HttpMessageConverter        Object, application/json
5         MappingJackson2XmlHttpMessageConverter     Object, application/xml (if available)
```

To add or replace converters:

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Replaces ALL default converters — add back what you need
        converters.add(new MappingJackson2HttpMessageConverter(customObjectMapper()));
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Adds to the default list without replacing defaults — preferred for additions
        converters.add(0, new ProtobufHttpMessageConverter());
    }
}
```

### Jackson Integration and Customization

Spring Boot auto-configures `Jackson2ObjectMapperBuilderCustomizer` beans to customize the global `ObjectMapper`. The cleanest way to customize Jackson is to declare an `ObjectMapper` bean or a `Jackson2ObjectMapperBuilderCustomizer`:

```java
@Bean
public Jackson2ObjectMapperBuilderCustomizer jsonCustomizer() {
    return builder -> builder
        // Write dates as ISO-8601 strings, not Unix timestamps
        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        // Include only non-null properties in serialized JSON
        .serializationInclusion(JsonInclude.Include.NON_NULL)
        // Use camelCase → snake_case field name mapping
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
}
```

Per-controller or per-method Jackson customization uses `@JsonView` or a custom serializer:

```java
public class Views {
    public interface Public {}
    public interface Admin extends Public {}
}

public class UserResponse {
    @JsonView(Views.Public.class)
    private String name;

    @JsonView(Views.Admin.class)
    private String email;  // only included in Admin view
}

@GetMapping("/users/{id}")
@JsonView(Views.Public.class)
public UserResponse getUser(@PathVariable Long id) { ... }
```

### ResponseEntity

`ResponseEntity<T>` gives full control over the HTTP response: status code, headers, and body:

```java
@PostMapping
public ResponseEntity<OrderResponse> createOrder(@RequestBody @Valid OrderRequest request) {
    OrderResponse created = orderService.create(request);

    return ResponseEntity
            .created(URI.create("/api/orders/" + created.getId()))
            .header("X-Request-Id", UUID.randomUUID().toString())
            .body(created);
}

@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteOrder(@PathVariable String id) {
    orderService.delete(id);
    return ResponseEntity.noContent().build();
}
```

`ResponseEntity` is the correct way to set a non-standard response status or add response headers. `@ResponseStatus` on a method is a simpler alternative for fixed status codes but does not allow dynamic header setting.

### @RequestMapping on Class vs Method

```java
@RestController
@RequestMapping(
    path = "/api/v2/products",
    produces = MediaType.APPLICATION_JSON_VALUE
)
public class ProductController {

    // Effective URL: GET /api/v2/products
    @GetMapping
    public List<Product> list() { ... }

    // Effective URL: POST /api/v2/products
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Product> create(@RequestBody Product product) { ... }

    // Effective URL: GET /api/v2/products/{id}
    @GetMapping("/{id}")
    public Product get(@PathVariable Long id) { ... }
}
```

`produces` on the class applies to all methods unless overridden per method. `consumes` restricts which requests the endpoint accepts by `Content-Type`.

## Gotchas

A controller that stores mutable state in instance fields is thread-unsafe. Because controllers are singletons shared across all requests, instance fields are shared across all concurrent requests. Use method-local variables, `@RequestScope` beans, or `ThreadLocal` for per-request state if the controller layer genuinely needs it.

When `configureMessageConverters()` is overridden in `WebMvcConfigurer`, it replaces all default converters. A common mistake is adding one custom converter and forgetting to add back `MappingJackson2HttpMessageConverter`, causing `406 Not Acceptable` responses for all JSON requests. Use `extendMessageConverters()` to append to the defaults instead.

`@ResponseBody` serialization happens in the adapter layer, not in the controller. If the controller method throws an exception, the exception resolver intercepts before serialization. Code in `@ControllerAdvice` that produces a response body for exceptions also goes through `HttpMessageConverter` — `@ExceptionHandler` return values are serialized the same way as `@RequestMapping` return values.

The `@RestController` annotation enables `@ResponseBody` for all handler methods in the class. If one method in a `@RestController` needs to return a view name instead of a serialized body, the method must explicitly use `ModelAndView` as its return type — returning a `String` from a `@RestController` method serializes the string as a JSON string (with quotes), not as a view name.

Type-level `@RequestMapping` annotations are inherited by subclasses. If a base controller class declares `@RequestMapping("/api")` and a subclass adds `@RequestMapping("/products")`, the effective path is `/api/products`. This inheritance is intentional but can cause unexpected mapping conflicts when controller class hierarchies grow deep.
