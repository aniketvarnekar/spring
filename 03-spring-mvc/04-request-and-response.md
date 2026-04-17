# Request and Response

## Overview

Beyond URL path and request body, HTTP requests carry headers, cookies, and session data. Spring MVC provides dedicated annotations for extracting each of these: `@RequestHeader` for headers, `@CookieValue` for cookies, and `HttpSession` for session attributes. On the response side, `ResponseEntity` provides full control over status codes and headers, `@ResponseStatus` sets a fixed status code declaratively, and `StreamingResponseBody` enables streaming large responses without buffering.

`ResponseEntity<T>` is the most flexible response type. It wraps the body, the status code, and any response headers in a single return value. The type parameter constrains what the body serializer produces, giving Jackson or other converters a concrete type to serialize. `ResponseEntity<Void>` is correct for responses that intentionally have no body (204 No Content, 304 Not Modified).

The distinction between `HttpEntity` and `ResponseEntity` is that `HttpEntity` does not carry a status code — it is used for both request and response access in certain scenarios. `ResponseEntity` extends `HttpEntity` with a status code.

Streaming responses are important for large payloads — file downloads, generated reports, server-sent events — where buffering the entire response in memory would be prohibitive. `StreamingResponseBody` defers writing to the response output stream to a separate thread, freeing the MVC thread. Spring MVC handles the thread coordination.

## Key Concepts

### @RequestHeader

```java
@GetMapping("/messages")
public List<Message> getMessages(
        @RequestHeader("Authorization") String authHeader,
        @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
        @RequestHeader(value = "Accept-Language", defaultValue = "en") String language) {
    // Headers are case-insensitive in HTTP; Spring handles normalization.
    return messageService.findMessages(language);
}

// Bind all request headers into a map
@GetMapping("/echo")
public Map<String, String> echoHeaders(@RequestHeader Map<String, String> headers) {
    return headers;
}

// Bind into MultiValueMap to access headers with multiple values
@GetMapping("/multi")
public void multiHeaders(@RequestHeader MultiValueMap<String, String> headers) { ... }
```

### @CookieValue

```java
@GetMapping("/profile")
public UserProfile getProfile(
        @CookieValue(name = "session_id", required = false) String sessionId,
        @CookieValue(name = "JSESSIONID", defaultValue = "") String jsessionId) {
    return profileService.findBySession(sessionId);
}
```

For full cookie access (name, value, domain, path, max-age), inject `HttpServletRequest` and call `request.getCookies()`. `@CookieValue` extracts only the value.

### ResponseEntity

```java
// 201 Created with Location header and body
@PostMapping("/users")
public ResponseEntity<UserResponse> createUser(@RequestBody @Valid UserRequest request) {
    UserResponse user = userService.create(request);
    URI location = URI.create("/api/users/" + user.getId());
    return ResponseEntity.created(location).body(user);
}

// 204 No Content
@DeleteMapping("/users/{id}")
public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
    userService.delete(id);
    return ResponseEntity.noContent().build();
}

// 304 Not Modified with conditional caching
@GetMapping("/resources/{id}")
public ResponseEntity<Resource> getResource(
        @PathVariable Long id,
        @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {

    Resource resource = resourceService.find(id);
    String etag = resource.getEtag();

    if (etag.equals(ifNoneMatch)) {
        return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
    }

    return ResponseEntity.ok()
            .eTag(etag)
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)))
            .body(resource);
}

// Dynamic status code
@GetMapping("/orders/{id}/status")
public ResponseEntity<OrderStatus> getStatus(@PathVariable String id) {
    try {
        return ResponseEntity.ok(orderService.getStatus(id));
    } catch (OrderNotFoundException ex) {
        return ResponseEntity.notFound().build();
    }
}
```

### @ResponseStatus

`@ResponseStatus` on a method or exception class sets a fixed HTTP status code:

```java
// Sets the response status to 201 Created without ResponseEntity
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
public OrderResponse create(@RequestBody @Valid OrderRequest request) {
    return orderService.create(request);
}

// On a custom exception class — any method that throws this receives the status
@ResponseStatus(HttpStatus.NOT_FOUND)
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String id) {
        super("Order not found: " + id);
    }
}
```

`@ResponseStatus` on exception classes is processed by `ResponseStatusExceptionResolver`. The exception message is included in the response body only if `server.error.include-message=always` is set.

### StreamingResponseBody

```java
@GetMapping("/reports/{id}/download")
public ResponseEntity<StreamingResponseBody> downloadReport(@PathVariable Long id) {
    Report report = reportService.find(id);

    StreamingResponseBody body = outputStream -> {
        // This lambda runs in a separate thread managed by Spring MVC.
        // It writes directly to the HTTP response output stream.
        // No need to buffer the entire report in memory.
        reportService.writeTo(report, outputStream);
        outputStream.flush();
    };

    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"report-" + id + ".csv\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(body);
}
```

Spring MVC dispatches the `StreamingResponseBody` lambda to the `TaskExecutor` bean named `applicationTaskExecutor` (or `mvcTaskExecutor` if configured). The response is committed once `writeWith()` is invoked, so no further modifications to status or headers are possible at that point.

### HttpEntity Access

```java
// Access request headers and body together without individual annotations
@PostMapping("/passthrough")
public ResponseEntity<String> passthrough(HttpEntity<String> requestEntity) {
    HttpHeaders headers = requestEntity.getHeaders();
    String body = requestEntity.getBody();
    // Re-use request headers in the response
    return ResponseEntity.ok().headers(headers).body("Echoed: " + body);
}
```

## Gotchas

`ResponseEntity` headers are added to the response before the body is written. After `ResponseEntity` is returned from a handler method and `HttpMessageConverter` begins writing the body, the response is committed — subsequent modifications to response headers (from `postHandle` or `afterCompletion`) will have no effect because the HTTP response headers section is already sent to the client.

`@ResponseStatus(HttpStatus.CREATED)` on a `@ResponseBody` method sets the status code but does not add a `Location` header. The `Location` header is a convention for `201 Created` responses and must be added manually via `ResponseEntity` or `HttpServletResponse.setHeader()`. Without `Location`, clients cannot follow the created resource URL without additional knowledge.

`@CookieValue` reads cookies from the `Cookie` request header. Response cookies (Set-Cookie) must be added via `HttpServletResponse.addCookie()` or by returning a `ResponseEntity` with a `Set-Cookie` header. There is no symmetrical `@SetCookieValue` annotation.

When using `StreamingResponseBody`, the HTTP response status and headers must be set before writing begins. Once `OutputStream.write()` is called, the response is committed and the servlet container may have already sent headers to the client. Set all headers in the `ResponseEntity` builder before returning the `StreamingResponseBody`.

`@ResponseStatus` on an `@ExceptionHandler` method in a `@ControllerAdvice` does not apply the status code — the method must explicitly return a `ResponseEntity` with the desired status or the framework uses the status from the `@ResponseStatus` annotation on the exception class. The interaction between `@ResponseStatus` on the handler and `@ResponseStatus` on the exception class can be confusing; prefer `ResponseEntity` for explicit status in `@ControllerAdvice` methods.
