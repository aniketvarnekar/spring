# Spring MVC

This section covers Spring's web framework from the DispatcherServlet request pipeline through controllers, request mapping, response handling, exception management, filters and interceptors, content negotiation, and validation. The focus is on how HTTP requests flow through the framework and where each extension point intervenes.

## Notes

| File | Description |
|------|-------------|
| [01-dispatcherservlet.md](./01-dispatcherservlet.md) | Request processing pipeline, HandlerMapping, HandlerAdapter, ViewResolver, full request lifecycle |
| [02-controllers.md](./02-controllers.md) | @RestController vs @Controller, response serialization, HttpMessageConverter, Jackson integration |
| [03-request-mapping.md](./03-request-mapping.md) | Mapping annotations, URI templates, @PathVariable, @RequestParam, @RequestBody, consumes/produces |
| [04-request-and-response.md](./04-request-and-response.md) | @RequestHeader, @CookieValue, ResponseEntity, @ResponseStatus, StreamingResponseBody |
| [05-exception-handling.md](./05-exception-handling.md) | @ExceptionHandler, @ControllerAdvice, ResponseEntityExceptionHandler, ProblemDetail, resolver chain |
| [06-filters-and-interceptors.md](./06-filters-and-interceptors.md) | Filter vs HandlerInterceptor capabilities, OncePerRequestFilter, interceptor lifecycle, ordering |
| [07-content-negotiation.md](./07-content-negotiation.md) | Accept header strategy, HttpMessageConverter configuration, custom converters |
| [08-validation.md](./08-validation.md) | @Valid vs @Validated, Bean Validation annotations, custom ConstraintValidator, method-level validation |

## Examples

| Project | Description |
|---------|-------------|
| [RestControllerDemo](./examples/RestControllerDemo/) | Full CRUD REST API demonstrating request mapping, ResponseEntity, and Jackson customization |
| [ExceptionHandlingDemo](./examples/ExceptionHandlingDemo/) | @ControllerAdvice with ProblemDetail responses and custom exception hierarchy |
| [FilterInterceptorDemo](./examples/FilterInterceptorDemo/) | Request logging filter and execution-time interceptor showing lifecycle differences |
| [ValidationDemo](./examples/ValidationDemo/) | Bean Validation on request bodies and path variables with custom ConstraintValidator |
