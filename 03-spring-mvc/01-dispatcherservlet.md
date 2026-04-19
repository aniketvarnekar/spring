# DispatcherServlet

## Overview

The `DispatcherServlet` is the front controller at the center of Spring MVC. Every incoming HTTP request handled by Spring passes through it. The servlet delegates to a chain of strategy objects — `HandlerMapping`, `HandlerAdapter`, `HandlerExceptionResolver`, and `ViewResolver` — each responsible for a distinct step in the request processing pipeline. Understanding this delegation model is what allows you to plug in custom behavior at any stage.

The `DispatcherServlet` is itself a `Servlet` and must be registered with the servlet container. In a Spring Boot application, `DispatcherServletAutoConfiguration` registers it automatically by declaring a `DispatcherServletRegistrationBean`, which is a `ServletRegistrationBean` that maps the servlet to `"/"` by default (all URLs). In a traditional servlet application, it is declared in `web.xml` or via `WebApplicationInitializer`.

The `DispatcherServlet` does not implement any handler logic itself — it only orchestrates. When a request arrives, the servlet asks each registered `HandlerMapping` in priority order to identify the handler for the request. The first non-null handler wins. The servlet then selects a `HandlerAdapter` capable of invoking that handler and delegates to it. The adapter handles the reflection, data binding, and return value processing specific to the handler type.

The `DispatcherServlet` maintains its own `WebApplicationContext`, called the "servlet context." In classic Spring MVC, this is a child of a root context that holds the service layer. In Spring Boot, a single context serves both roles. The servlet context exposes the `DispatcherServlet`'s strategy beans and can override root context beans.

## Key Concepts

### Request Processing Pipeline

```text
HTTP Request
     │
     ▼
DispatcherServlet.doDispatch()
     │
     ├─── HandlerMapping.getHandler()
     │      (returns HandlerExecutionChain — handler + interceptors)
     │      Implementations: RequestMappingHandlerMapping, RouterFunctionMapping,
     │                       BeanNameUrlHandlerMapping
     │
     ├─── HandlerAdapter.supports(handler)?
     │      (selects adapter for the handler type)
     │      Implementations: RequestMappingHandlerAdapter (for @Controller methods),
     │                       HttpRequestHandlerAdapter, SimpleControllerHandlerAdapter
     │
     ├─── HandlerInterceptor.preHandle()
     │      (runs before the handler; returning false aborts the chain)
     │
     ├─── HandlerAdapter.handle()
     │      (invokes the handler method, performs data binding, returns ModelAndView)
     │      RequestMappingHandlerAdapter orchestrates:
     │        - Argument resolution (HandlerMethodArgumentResolver)
     │        - Handler method invocation
     │        - Return value handling (HandlerMethodReturnValueHandler)
     │
     ├─── HandlerInterceptor.postHandle()
     │      (runs after handler, before view rendering; ModelAndView is modifiable)
     │
     ├─── View.render() or HttpMessageConverter.write()
     │      (for @ResponseBody, HttpMessageConverter writes directly to response)
     │
     ├─── HandlerInterceptor.afterCompletion()
     │      (always runs after response; used for resource cleanup)
     │
     └─── HandlerExceptionResolver.resolveException()
            (only if an exception escapes the handler or interceptors)
            Implementations: ExceptionHandlerExceptionResolver (@ExceptionHandler),
                             ResponseStatusExceptionResolver (@ResponseStatus),
                             DefaultHandlerExceptionResolver (standard Spring MVC exceptions)
```

### HandlerMapping

`HandlerMapping` maps an incoming request to a handler and a list of interceptors that should run around it. The result is a `HandlerExecutionChain`.

`RequestMappingHandlerMapping` is the primary implementation. At startup, it scans all `@Controller` beans and builds an internal map of `RequestMappingInfo` → handler method. At request time it matches the request against this map using URL pattern, HTTP method, parameters, headers, and media type constraints.

Priority among `HandlerMapping` beans is determined by `@Order` or `Ordered`. Lower values are checked first. Spring Boot auto-configures `RequestMappingHandlerMapping` with order `0`. A custom `HandlerMapping` that should take precedence over annotation-driven mappings must have an order less than `0`.

### HandlerAdapter

`HandlerAdapter` knows how to invoke a particular type of handler. `RequestMappingHandlerAdapter` handles methods annotated with `@RequestMapping` (and its composed variants). It orchestrates argument resolution (converting request parameters, path variables, and request bodies into method arguments), invokes the method, and processes the return value (writing it to the response or producing a `ModelAndView`).

`HandlerMethodArgumentResolver` and `HandlerMethodReturnValueHandler` are the two extension points within `RequestMappingHandlerAdapter`. Custom `@RequestMapping` method arguments (e.g., a custom `@CurrentUser` annotation that resolves the logged-in user from `SecurityContext`) are implemented as `HandlerMethodArgumentResolver` beans.

### Servlet Registration in Spring Boot

```java
// DispatcherServletAutoConfiguration does this automatically.
// Shown here to make the registration explicit.
@Bean
public DispatcherServletRegistrationBean dispatcherServletRegistration(
        DispatcherServlet dispatcherServlet) {
    DispatcherServletRegistrationBean registration =
            new DispatcherServletRegistrationBean(dispatcherServlet, "/");
    registration.setLoadOnStartup(1);
    return registration;
}
```

To map a second `DispatcherServlet` to a different path (e.g., a legacy API at `/legacy/*`), declare a second `DispatcherServlet` bean with a different name and a corresponding `DispatcherServletRegistrationBean`.

### Default Servlet Handling

When no handler matches an incoming request, the `DispatcherServlet` can forward the request to the container's default servlet (which serves static resources). This is enabled by `<mvc:default-servlet-handler/>` in XML or `configurer.enable()` in `WebMvcConfigurer.configureDefaultServletHandling()`. In Spring Boot, static resources are handled by `ResourceHttpRequestHandler` mapped at `/`, which takes precedence over the default servlet by default.

## Gotchas

The `DispatcherServlet` catches all exceptions thrown during handler invocation and delegates to the registered `HandlerExceptionResolver` chain. If no resolver handles the exception, the raw exception propagates to the servlet container, which produces an error page. Application-level exceptions should always be caught by an `@ControllerAdvice` method, not the container.

`HandlerInterceptor.postHandle()` is not called if the handler throws an exception. `afterCompletion()` is always called. Code that must run after every request regardless of outcome — such as resource cleanup or timing measurement — belongs in `afterCompletion()`, not `postHandle()`.

The `DispatcherServlet`'s strategy beans (`HandlerMapping`, `HandlerAdapter`, etc.) are looked up by type in the servlet's `WebApplicationContext`. If a custom bean of one of these strategy types is declared, it supplements or replaces the default. When a custom `HandlerMapping` is declared without also declaring a `RequestMappingHandlerMapping`, the `DispatcherServlet` does not register a default one, and annotation-driven controllers stop working. Always be explicit when customizing strategy beans.

Multipart request parsing is not enabled by default in all configurations. The `DispatcherServlet` checks for a `MultipartResolver` bean named `multipartResolver`. Spring Boot auto-configures `StandardServletMultipartResolver` when `spring.servlet.multipart.enabled=true` (the default). The maximum part size, request size, and storage location are configured via `spring.servlet.multipart.*` properties.

The `DispatcherServlet` processes requests on the thread from the container's thread pool. Blocking that thread with a slow I/O operation (database query, external API call) holds a thread from the pool for the duration. For applications with high concurrency and many slow operations, consider Spring WebFlux (Reactor Netty) instead of Spring MVC on Tomcat.
