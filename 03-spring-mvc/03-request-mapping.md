# Request Mapping

## Overview

Request mapping is the process of matching an incoming HTTP request to a handler method. Spring MVC's `RequestMappingHandlerMapping` examines a request's URL, HTTP method, query parameters, headers, and content type, then selects the most specific matching handler. Understanding the matching algorithm and the annotations that drive it prevents ambiguous mapping errors and unexpected 404 or 405 responses.

All request mapping annotations (`@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping`) are composed annotations built on `@RequestMapping`. They are equivalent to `@RequestMapping(method = RequestMethod.GET)` etc. and carry the same attributes. The composed variants are preferred in application code for clarity; `@RequestMapping` remains useful when multiple HTTP methods should map to the same handler or when the annotation is placed at the class level.

URL patterns support exact matches, path variables (`{variable}`), wildcards (`*`, `**`), and regex constraints (`{variable:[a-z]+}`). Spring MVC also supports path patterns using Spring's `PathPatternParser` (the default since Spring 5.3), which is more efficient than `AntPathMatcher` and is used exclusively in Spring WebFlux. The parsing mode is configurable but `PathPatternParser` is the recommended choice.

The distinction between `@PathVariable`, `@RequestParam`, and `@RequestBody` is important for API design and for understanding how Spring extracts data from different parts of the request. `@PathVariable` reads from the URL path, `@RequestParam` reads from query parameters or form data, and `@RequestBody` reads from the request body via `HttpMessageConverter`.

## Key Concepts

### Mapping Annotations

```java
@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    @GetMapping                          // GET /api/articles
    public List<Article> list() { ... }

    @GetMapping("/{id}")                 // GET /api/articles/{id}
    public Article get(@PathVariable Long id) { ... }

    @PostMapping                         // POST /api/articles
    public ResponseEntity<Article> create(@RequestBody Article article) { ... }

    @PutMapping("/{id}")                 // PUT /api/articles/{id}
    public Article update(@PathVariable Long id, @RequestBody Article article) { ... }

    @PatchMapping("/{id}")               // PATCH /api/articles/{id}
    public Article patch(@PathVariable Long id, @RequestBody Map<String, Object> updates) { ... }

    @DeleteMapping("/{id}")              // DELETE /api/articles/{id}
    public ResponseEntity<Void> delete(@PathVariable Long id) { ... }

    // Multiple HTTP methods for the same handler
    @RequestMapping(value = "/{id}/status", method = {RequestMethod.GET, RequestMethod.HEAD})
    public Article status(@PathVariable Long id) { ... }
}
```

### URI Templates and @PathVariable

```java
// Simple variable
@GetMapping("/users/{userId}/orders/{orderId}")
public Order getOrder(@PathVariable Long userId, @PathVariable String orderId) { ... }

// Variable with regex constraint — only matches if the segment consists of digits
@GetMapping("/products/{id:[0-9]+}")
public Product getById(@PathVariable Long id) { ... }

// Optional path variable — requires the mapping to also match the shorter path
@GetMapping({"/reports", "/reports/{year}"})
public List<Report> reports(@PathVariable Optional<Integer> year) { ... }

// Capturing the rest of the path with **
@GetMapping("/files/**")
public Resource file(HttpServletRequest request) {
    String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
    // path = everything after /files/
    return loadFile(path);
}
```

Path variables are URL-decoded. A variable value like `%2F` (encoded slash) is decoded to `/` before matching, which may conflict with path separator semantics. Configure `UrlPathHelper.setUrlDecode(false)` if encoded slashes in path variables are required.

### @RequestParam

```java
// Required parameter — missing value causes 400 Bad Request
@GetMapping("/search")
public List<Article> search(@RequestParam String query) { ... }

// Optional parameter with default
@GetMapping("/articles")
public List<Article> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String category) { ... }

// Multi-value parameter — binds all values of ?tag=java&tag=spring
@GetMapping("/articles")
public List<Article> byTags(@RequestParam List<String> tag) { ... }

// Bind all query parameters into a map
@GetMapping("/debug")
public Map<String, String> params(@RequestParam Map<String, String> params) { ... }
```

### @RequestBody

```java
// Reads the request body via HttpMessageConverter (Jackson for JSON)
// @Valid triggers Bean Validation on the deserialized object
@PostMapping
public ResponseEntity<Article> create(@RequestBody @Valid ArticleRequest request) {
    Article created = articleService.create(request);
    return ResponseEntity.created(URI.create("/api/articles/" + created.getId()))
                         .body(created);
}

// Reading a raw string body
@PostMapping("/webhook")
public void webhook(@RequestBody String rawBody) { ... }

// Reading a byte array body
@PostMapping("/upload")
public void upload(@RequestBody byte[] data) { ... }
```

### Consumes and Produces Constraints

```java
@PostMapping(
    value = "/data",
    consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE},
    produces = MediaType.APPLICATION_JSON_VALUE
)
public DataResponse process(@RequestBody DataRequest request) { ... }
```

`consumes` restricts which requests the handler accepts based on `Content-Type`. A request with a non-matching `Content-Type` receives a `415 Unsupported Media Type` response. `produces` restricts which requests the handler will serve based on `Accept` header. A request with a non-matching `Accept` header receives a `406 Not Acceptable` response.

### Mapping Ambiguity Resolution

When multiple handlers match a request, Spring selects the most specific one using this precedence (higher in the list wins):

```
1. Exact path match  >  wildcard match
2. More specific method  >  no method constraint
3. More parameters/headers constraints  >  fewer
4. More specific media type  >  wildcard (application/json > */*)
5. Longer path  >  shorter path
```

If two handlers are equally specific, `IllegalStateException: Ambiguous handler methods` is thrown at startup.

## Gotchas

Path variables are bound by name by default, matching the variable name in the URL template to the parameter name in the method signature. With Java 25 and `-parameters` compiler flag (enabled by Spring Boot's build configuration), parameter names are preserved in bytecode and name matching works automatically. Without `-parameters`, add the variable name explicitly: `@PathVariable("id") Long id`.

`@RequestParam` without `required = false` or a `defaultValue` throws `MissingServletRequestParameterException` (400 Bad Request) when the parameter is absent. This is the correct behavior for required parameters but surprises developers who expect Spring to pass `null` for missing parameters. Use `Optional<T>` as the parameter type for a cleaner optional declaration: `@RequestParam Optional<String> category`.

Mapping `@PostMapping` and `@PutMapping` to the same path is a common design error — they are different HTTP methods and should map to different handler methods. Mapping both to the same handler using `@RequestMapping(method = {POST, PUT})` works but conflates create (POST, not idempotent) and replace (PUT, idempotent) semantics, which violates HTTP conventions.

URL-encoded form data (`application/x-www-form-urlencoded`) is read with `@RequestParam`, not `@RequestBody`. Attempting to use `@RequestBody` to read form data returns an empty object. This is a frequent mistake when building endpoints consumed by HTML forms.

The `@RequestMapping` annotation at the class level is inherited by subclasses. If a base class declares `@RequestMapping("/api")` and a subclass in a different module also extends it, both subclass's handler methods will be mapped under `/api`. This causes unexpected conflicts when multiple modules include a common controller base class.
