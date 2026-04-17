# Exception Handling

## Overview

Spring MVC's exception handling system translates exceptions thrown by handler methods into appropriate HTTP responses. Without any configuration, uncaught exceptions produce either a container-default error page or a JSON error response from Spring Boot's `BasicErrorController`. The production-quality alternative is a `@ControllerAdvice` class that catches specific exception types and returns structured, standards-compliant error responses.

`@ExceptionHandler` declares a method that handles a specific exception type. When placed in a `@Controller` class, it handles exceptions from that controller only. When placed in a `@ControllerAdvice` class, it becomes a global handler for all controllers. `@RestControllerAdvice` combines `@ControllerAdvice` and `@ResponseBody`, making it the standard choice for REST APIs where exception handlers return JSON.

Spring 6 introduced first-class support for RFC 7807 (Problem Details for HTTP APIs) via the `ProblemDetail` class. `ProblemDetail` models the standard `type`, `title`, `status`, `detail`, and `instance` fields, and Jackson serializes it to a standard JSON format that API clients can parse generically. `ResponseEntityExceptionHandler` now produces `ProblemDetail` for all its internally handled exceptions when `spring.mvc.problemdetails.enabled=true`.

The `HandlerExceptionResolver` chain is the underlying mechanism. `ExceptionHandlerExceptionResolver` processes `@ExceptionHandler` methods. `ResponseStatusExceptionResolver` processes `@ResponseStatus` on exception classes. `DefaultHandlerExceptionResolver` handles standard Spring MVC exceptions like `HttpMessageNotReadableException` and `MethodArgumentNotValidException`. These resolvers are tried in order; the first one that handles the exception wins.

## Key Concepts

### @ControllerAdvice and @ExceptionHandler

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // Handle application-specific domain exception
    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleOrderNotFound(OrderNotFoundException ex,
                                              HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage());
        detail.setTitle("Order Not Found");
        detail.setInstance(URI.create(request.getRequestURI()));
        // Add custom extension properties (RFC 7807 allows additional fields)
        detail.setProperty("orderId", ex.getOrderId());
        return detail;
    }

    // Catch all unhandled runtime exceptions — prevents leaking stack traces
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        // Log the exception with full detail for ops
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        // Return minimal detail to the client — never expose internal details
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
    }
}
```

`@ControllerAdvice` is a class-level annotation. It optionally restricts which controllers it advises via `basePackages`, `basePackageClasses`, or `assignableTypes` attributes.

### ResponseEntityExceptionHandler

`ResponseEntityExceptionHandler` is a convenient base class that provides `@ExceptionHandler` methods for all standard Spring MVC exceptions:

| Exception | Default Status |
|---|---|
| `MethodArgumentNotValidException` | 400 Bad Request |
| `HttpMessageNotReadableException` | 400 Bad Request |
| `MissingServletRequestParameterException` | 400 Bad Request |
| `ConstraintViolationException` | 400 Bad Request |
| `MethodNotAllowedException` | 405 Method Not Allowed |
| `HttpMediaTypeNotSupportedException` | 415 Unsupported Media Type |
| `HttpMediaTypeNotAcceptableException` | 406 Not Acceptable |
| `NoHandlerFoundException` | 404 Not Found |

Override individual methods to customize the response:

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Validation failed");
        detail.setTitle("Invalid Request");

        // Collect all field errors into the problem detail extension
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage()));
        detail.setProperty("fieldErrors", fieldErrors);

        return ResponseEntity.badRequest().body(detail);
    }
}
```

### ProblemDetail (RFC 7807)

Enable RFC 7807 responses globally in `application.yaml`:

```yaml
spring:
  mvc:
    problemdetails:
      enabled: true
```

With this setting, `ResponseEntityExceptionHandler` produces `ProblemDetail` for all its handled exceptions automatically. The response `Content-Type` is `application/problem+json`.

```json
{
  "type": "https://example.com/errors/order-not-found",
  "title": "Order Not Found",
  "status": 404,
  "detail": "No order with ID ORD-999 exists",
  "instance": "/api/orders/ORD-999",
  "orderId": "ORD-999"
}
```

### Custom Exception Hierarchy

```java
// Base exception with error code and client-safe message
public abstract class ApiException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus status;

    protected ApiException(String errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() { return errorCode; }
    public HttpStatus getStatus() { return status; }
}

public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String resourceType, Object id) {
        super("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND,
              resourceType + " not found with id: " + id);
    }
}

public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super("CONFLICT", HttpStatus.CONFLICT, message);
    }
}

// Single handler covers the entire hierarchy
@ExceptionHandler(ApiException.class)
public ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
    ProblemDetail detail = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
    detail.setProperty("errorCode", ex.getErrorCode());
    return ResponseEntity.status(ex.getStatus()).body(detail);
}
```

### HandlerExceptionResolver Chain

```
ExceptionHandlerExceptionResolver     — @ExceptionHandler methods (highest priority)
  ↓ (if unhandled)
ResponseStatusExceptionResolver       — @ResponseStatus on exception classes
  ↓ (if unhandled)
DefaultHandlerExceptionResolver       — standard Spring MVC exceptions
  ↓ (if unhandled)
Exception propagates to servlet container → BasicErrorController (Spring Boot)
```

To override the priority or add a custom resolver, implement `HandlerExceptionResolver` and register it as a bean with `@Order`.

## Gotchas

`@ExceptionHandler` in a `@ControllerAdvice` catches exceptions thrown from handler methods and from handler interceptors' `preHandle` methods. It does not catch exceptions thrown during filter execution (before the `DispatcherServlet`), which are handled by the servlet container or a separate filter.

When multiple `@ExceptionHandler` methods could match an exception (e.g., one handles `RuntimeException` and another handles `OrderNotFoundException`, which extends `RuntimeException`), Spring selects the most specific match — the one for `OrderNotFoundException`. If two handlers are equally specific for the same exception type, an `IllegalStateException` is thrown at startup.

`ResponseEntityExceptionHandler.handleExceptionInternal()` is the bottleneck through which all responses flow. If you override it, all standard exception responses are affected. Override individual `handle*` methods for targeted customization; override `handleExceptionInternal` only for global response post-processing.

`@ControllerAdvice` without `@ResponseBody` (i.e., plain `@ControllerAdvice` rather than `@RestControllerAdvice`) requires the exception handler method to return a `ModelAndView` or annotate itself with `@ResponseBody`. In a REST API, always use `@RestControllerAdvice` to avoid accidentally returning a view name.

Spring Boot's `BasicErrorController` is the fallback that handles requests forwarded to `/error`. If an exception escapes all `HandlerExceptionResolver` implementations, the container catches it and forwards to `/error`, where `BasicErrorController` produces the response. Customizing `BasicErrorController` is possible but unnecessary if `@ControllerAdvice` handles all expected exceptions.
