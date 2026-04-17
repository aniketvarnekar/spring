# Filters and Interceptors

## Overview

Filters and interceptors are both mechanisms for intercepting HTTP requests, but they operate at different levels of the stack and have fundamentally different capabilities. Filters are part of the servlet specification (`jakarta.servlet.Filter`) and execute before the `DispatcherServlet` sees the request. Interceptors (`HandlerInterceptor`) are Spring MVC constructs that execute inside the `DispatcherServlet`, after handler resolution but before and after handler execution.

Because filters execute outside Spring MVC, they can intercept any request handled by the servlet container — not just those mapped to a `DispatcherServlet`. A filter can read or modify the request and response, wrap them in custom decorators, or abort the chain entirely. Filters do not have access to the resolved handler or any Spring MVC context (path variables, request attributes set by MVC). They are appropriate for cross-cutting concerns that apply at the HTTP level: CORS, authentication, request logging, compression, rate limiting.

Interceptors have access to the resolved handler (`HandlerMethod`), which means they can inspect annotations on the controller or method. They also receive the `ModelAndView` after handler execution (in `postHandle`), enabling response enrichment before view rendering. Because they execute within the `DispatcherServlet`, they only apply to requests handled by Spring MVC. Interceptors are appropriate for concerns like authorization (checking the handler's `@Secured` annotation), execution-time logging, and audit trails that need method-level context.

The key behavioral difference is in the exception path: `HandlerInterceptor.postHandle()` is not called if the handler throws an exception, but `afterCompletion()` is always called. Filters see all responses including error responses because they wrap the entire dispatch cycle.

## Key Concepts

### Filter Implementation

```java
@Component
@Order(1)  // Lower value = executed earlier in the filter chain
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        // Attach request ID to the response before the chain runs
        response.setHeader("X-Request-Id", requestId);

        try {
            // Must call doFilter to pass control to the next filter/servlet
            filterChain.doFilter(request, response);
        } finally {
            // finally block ensures this runs even if the chain throws
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[{}] {} {} → {} ({}ms)",
                    requestId,
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsed);
        }
    }
}
```

`OncePerRequestFilter` guarantees that `doFilterInternal` is called exactly once per request, even in scenarios where the servlet container dispatches the same request multiple times (e.g., `forward:` or `error:` dispatching). Plain `Filter` implementations may be called multiple times.

`OncePerRequestFilter` also provides `shouldNotFilter(HttpServletRequest)` for conditional exclusion:

```java
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    // Exclude health check and actuator requests from logging
    String path = request.getServletPath();
    return path.startsWith("/actuator");
}
```

### Filter Registration Without Spring Component Scanning

For finer control over URL patterns, use `FilterRegistrationBean`:

```java
@Bean
public FilterRegistrationBean<RateLimitingFilter> rateLimitFilter() {
    FilterRegistrationBean<RateLimitingFilter> bean = new FilterRegistrationBean<>();
    bean.setFilter(new RateLimitingFilter());
    bean.addUrlPatterns("/api/*");  // apply only to /api/** paths
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE);  // run before other filters
    return bean;
}
```

`FilterRegistrationBean` is also how Spring Security adds its `DelegatingFilterProxy` — the Spring Security filter chain is itself a registered servlet filter.

### HandlerInterceptor

```java
@Component
public class ExecutionTimeInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ExecutionTimeInterceptor.class);
    private static final String START_TIME = "startTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                              Object handler) {
        // Returning false aborts further processing — the handler is not called.
        // Store start time in request attributes for access in afterCompletion.
        request.setAttribute(START_TIME, System.nanoTime());
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
        // Called after handler execution, before view rendering.
        // NOT called if the handler threw an exception.
        // Can modify the ModelAndView here.
        if (handler instanceof HandlerMethod hm) {
            log.debug("Handler method: {}", hm.getMethod().getName());
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // Always called, even if preHandle returned false or an exception was thrown.
        // ex is non-null if the handler threw an uncaught exception.
        long startTime = (Long) request.getAttribute(START_TIME);
        long elapsed = (System.nanoTime() - startTime) / 1_000_000;
        log.info("{} completed in {}ms (status {})",
                request.getRequestURI(), elapsed, response.getStatus());
    }
}
```

Register interceptors in `WebMvcConfigurer`:

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ExecutionTimeInterceptor executionTimeInterceptor;

    public WebConfig(ExecutionTimeInterceptor executionTimeInterceptor) {
        this.executionTimeInterceptor = executionTimeInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(executionTimeInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/health");
    }
}
```

### Filter vs HandlerInterceptor Comparison

| Capability | Filter | HandlerInterceptor |
|---|---|---|
| Access to resolved handler | No | Yes |
| Access to handler annotations | No | Yes (via HandlerMethod) |
| Access to ModelAndView | No | Yes (postHandle only) |
| Applies to non-MVC requests | Yes | No |
| Wraps full error response | Yes | Partial (afterCompletion gets ex) |
| Can modify request/response body | Yes (with wrapper) | No |
| Spring bean injection | Via FilterRegistrationBean or @Component | Yes (it is a bean) |
| Async dispatch | Participates | Participates with AsyncHandlerInterceptor |

## Gotchas

A filter that fails to call `filterChain.doFilter()` silently drops the request — the response is sent with whatever status the filter sets (or 200 with no body if it set nothing). Always ensure `doFilter()` is called or a complete response is explicitly written when aborting the chain.

Interceptors registered via `InterceptorRegistry` apply only to requests handled by the `DispatcherServlet`. A request for a static resource served by `ResourceHttpRequestHandler` (mapped to `/`) bypasses handler interceptors — the interceptor's `preHandle` is never called. Use a filter for concerns that must apply to all requests including static resources.

The `HandlerInterceptor` execution order is determined by the order in which they are registered in `InterceptorRegistry.addInterceptor()`, not by `@Order`. For multiple interceptors, `preHandle` runs in registration order and `postHandle`/`afterCompletion` run in reverse registration order — mirroring a nested call stack.

`OncePerRequestFilter.shouldNotFilter()` is evaluated once per dispatch. If the filter excludes a request based on the servlet path, it may still run for an error dispatch (when the container forwards to `/error`) because the error dispatch produces a new dispatch with a different servlet path. Override `shouldNotFilterErrorDispatch()` to also exclude error dispatches.

Filters in Spring Security's `SecurityFilterChain` run inside a `DelegatingFilterProxy`, which itself is a regular servlet filter. Custom filters added with `HttpSecurity.addFilterBefore()` or `addFilterAfter()` are placed within the security filter chain, not before it. A filter added outside the security chain (via `@Component` or `FilterRegistrationBean`) may run before the security chain and therefore before authentication.
