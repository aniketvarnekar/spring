# Common Interview Questions

---

## Spring Core

**Q1. What is the Spring IoC container and what are its two main implementations?**

The IoC container manages the creation, configuration, and lifecycle of beans. `BeanFactory` is the basic container, providing dependency injection and lazy initialization. `ApplicationContext` extends `BeanFactory` and adds event publishing, internationalization, annotation processing, AOP integration, and eager singleton instantiation at startup. In practice, always use `ApplicationContext` (or its sub-interface `ConfigurableApplicationContext`). `ClassPathXmlApplicationContext` and `AnnotationConfigApplicationContext` are common concrete implementations.

---

**Q2. What is a Spring bean? What is a BeanDefinition?**

A Spring bean is any object whose lifecycle is managed by the Spring container. A `BeanDefinition` is the container's internal description of a bean: its class, scope, constructor arguments, property values, initialization method, destruction method, and whether it is a primary or lazy candidate. `BeanDefinition` instances are created by reading `@Configuration` classes, `@ComponentScan` results, or XML files, and are stored in the `BeanDefinitionRegistry` before any bean instances are created.

---

**Q3. What are the differences between constructor injection, setter injection, and field injection?**

Constructor injection is preferred: dependencies are declared in the constructor signature, the object is fully initialized at creation time, fields can be `final`, and circular dependencies between constructor-injected beans fail fast at startup. Setter injection is appropriate for optional dependencies that have defaults. Field injection (`@Autowired` directly on a field) is the most concise but makes the class difficult to unit test without the Spring context, because there is no public setter or constructor to inject a mock.

---

**Q4. How does `@Autowired` resolve which bean to inject when multiple candidates exist?**

Resolution order:
1. Exact type match. If exactly one bean of the required type exists, it is injected.
2. `@Primary`. If multiple candidates exist, the one marked `@Primary` is chosen.
3. `@Qualifier`. If a `@Qualifier` name is specified, only the bean with that name is considered.
4. Parameter/field name match. If the injection point name matches a bean name, that bean is chosen.
5. `NoUniqueBeanDefinitionException` is thrown if no unique candidate can be determined.

---

**Q5. What is the bean lifecycle in order?**

Instantiation → `BeanNameAware.setBeanName` → `BeanFactoryAware.setBeanFactory` → `ApplicationContextAware.setApplicationContext` → `BeanPostProcessor.postProcessBeforeInitialization` → `@PostConstruct` → `InitializingBean.afterPropertiesSet` → `init-method` → `BeanPostProcessor.postProcessAfterInitialization` → bean is ready → (on shutdown) `@PreDestroy` → `DisposableBean.destroy` → `destroy-method`.

---

**Q6. What is a BeanPostProcessor? How does it differ from a BeanFactoryPostProcessor?**

`BeanPostProcessor` operates on fully instantiated bean instances. It has two hook methods: `postProcessBeforeInitialization` (before init callbacks) and `postProcessAfterInitialization` (after init callbacks). It can return a different object (e.g., a proxy), which is how AOP and `@Async` proxies are created. `BeanFactoryPostProcessor` operates on `BeanDefinition` objects before any bean instances are created. `PropertySourcesPlaceholderConfigurer` is a common example: it resolves `${...}` placeholders in bean definitions before instantiation.

---

**Q7. What are the available bean scopes?**

| Scope | Lifetime |
|---|---|
| `singleton` (default) | One instance per ApplicationContext |
| `prototype` | New instance per `getBean()` call |
| `request` | One instance per HTTP request (web only) |
| `session` | One instance per HTTP session (web only) |
| `application` | One instance per ServletContext (web only) |
| `websocket` | One instance per WebSocket session |

---

**Q8. What is a scoped proxy and when is it needed?**

A scoped proxy is needed when a shorter-lived bean (e.g., `request`-scoped) is injected into a longer-lived bean (e.g., `singleton`). Without a proxy, the singleton holds a reference to the single request-scoped instance that existed at startup. With `@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)`, the singleton holds a proxy that delegates to the correct request-scoped instance on each method call.

---

**Q9. What is the difference between `@Configuration` and `@Component` for configuration classes?**

`@Configuration` with `proxyBeanMethods = true` (default) causes Spring to subclass the configuration class with CGLIB. Inter-bean method calls (one `@Bean` method calling another) are intercepted by the proxy, which returns the existing singleton instead of creating a new instance. This ensures singleton semantics when `@Bean` methods reference each other. `@Component` (or `@Configuration(proxyBeanMethods = false)`) is "lite mode": no CGLIB proxy, inter-bean method calls create new instances. Lite mode is faster to start but requires all bean references to be injected via parameters, not via method calls.

---

**Q10. What is `@Conditional` and how does Spring Boot's `@ConditionalOnMissingBean` use it?**

`@Conditional` takes a `Condition` implementation whose `matches()` method returns true or false. If false, the `@Bean` or `@Configuration` is skipped. `@ConditionalOnMissingBean` is a Spring Boot shorthand: it checks whether a bean of the specified type (or with the specified name) is already registered in the `BeanDefinitionRegistry`. Auto-configuration classes use it so that user-defined beans always take precedence over auto-configured defaults.

---

## Spring Boot

**Q11. What is Spring Boot auto-configuration?**

Auto-configuration is the mechanism by which Spring Boot provides sensible default beans based on what is on the classpath. An auto-configuration class is annotated with `@AutoConfiguration` and listed in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. It uses `@ConditionalOn*` annotations to register beans only when the conditions are met (e.g., a class is on the classpath, a property is set, a bean of a type is absent). The `@SpringBootApplication` annotation includes `@EnableAutoConfiguration` which triggers the loading of all listed auto-configuration classes.

---

**Q12. What does `@SpringBootApplication` expand to?**

It is a composed annotation equivalent to `@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan`. `@SpringBootConfiguration` is itself `@Configuration`. The default `@ComponentScan` scans the package of the annotated class and all its sub-packages.

---

**Q13. What is the PropertySource priority order in Spring Boot?**

High to low: command-line arguments → `SPRING_APPLICATION_JSON` → servlet init/context parameters → JNDI → Java system properties → OS environment variables → Profile-specific `application-{profile}.properties/yaml` → `application.properties/yaml` → `@PropertySource` on `@Configuration` → default properties set with `SpringApplication.setDefaultProperties`. Properties from higher-priority sources override those from lower-priority sources.

---

**Q14. What is `@ConfigurationProperties` and how does it differ from `@Value`?**

`@ConfigurationProperties` binds a group of properties with a common prefix to a Java object (class or record). It supports relaxed binding (camelCase, kebab-case, UPPER_CASE all map to the same field), nested objects, type-safe collections, JSR-380 validation with `@Validated`, and IDE auto-completion via the configuration processor. `@Value` injects a single property value using a SpEL expression. It does not support relaxed binding and cannot be validated with `@Validated`. Use `@ConfigurationProperties` for groups of related properties; use `@Value` for a single, simple value.

---

**Q15. What is the difference between `@SpringBootTest` and `@WebMvcTest`?**

`@SpringBootTest` loads the full `ApplicationContext` (all beans, all auto-configuration). It is an integration test. `@WebMvcTest` is a slice test: it loads only the web layer (controllers, filters, `@ControllerAdvice`, converters, security configuration) and stubs everything else. Services and repositories must be provided via `@MockBean`. `@WebMvcTest` is faster and more focused but cannot test the interaction between layers. Choose `@SpringBootTest` when you need to verify cross-layer behavior; choose `@WebMvcTest` when you are testing controller logic in isolation.

---

## Spring MVC

**Q16. What is the DispatcherServlet and what is its role?**

`DispatcherServlet` is Spring MVC's front controller. All HTTP requests matching its URL mapping pass through it. It delegates to a chain of components: `HandlerMapping` (find the handler), `HandlerAdapter` (invoke the handler), `HandlerInterceptor` (pre/post processing), view resolution or message conversion (for REST responses), and `HandlerExceptionResolver` (translate exceptions to HTTP responses). The `DispatcherServlet` owns the application context that contains web-layer beans.

---

**Q17. What is the difference between a Filter and a HandlerInterceptor?**

Filters are Java EE Servlet API components that operate on the raw `ServletRequest`/`ServletResponse` before the DispatcherServlet sees the request. They run outside the Spring MVC context and are not aware of Spring concepts like `ModelAndView` or handler methods. `HandlerInterceptor` is a Spring MVC concept. Its `preHandle`, `postHandle`, and `afterCompletion` methods have access to the handler method object and the `ModelAndView`. Filters can be registered with `FilterRegistrationBean`; interceptors are registered via `WebMvcConfigurer.addInterceptors`. Filters are more appropriate for cross-cutting concerns that apply to all requests (logging, encoding); interceptors are appropriate for MVC-aware tasks (locale resolution, authentication checks that need handler information).

---

**Q18. What is `ProblemDetail` (RFC 7807)?**

`ProblemDetail` is a standardized JSON error response format: `type` (URI identifier for the problem type), `title`, `status`, `detail`, `instance`, plus arbitrary extension fields. Spring MVC supports it via `ResponseEntityExceptionHandler` and the `spring.mvc.problemdetails.enabled=true` property. `ProblemDetail.forStatusAndDetail(HttpStatus, String)` creates an instance. Custom extensions are added via `setProperty(key, value)`.

---

**Q19. What is `@ControllerAdvice` and when would you use `@RestControllerAdvice`?**

`@ControllerAdvice` declares global exception handlers (`@ExceptionHandler`), `@ModelAttribute` methods, and `@InitBinder` methods that apply to all (or a subset of) controllers. `@RestControllerAdvice` is composed of `@ControllerAdvice` + `@ResponseBody`, so exception handler return values are serialized as the response body (JSON/XML) rather than being treated as view names. Use `@RestControllerAdvice` for REST APIs.

---

**Q20. What is bean validation and how does it integrate with Spring MVC?**

Bean validation (JSR-380) uses annotations like `@NotNull`, `@Size`, `@Pattern`, `@Min`, `@Max` on fields or constructor parameters. In Spring MVC, adding `@Valid` (or `@Validated`) on a `@RequestBody`, `@ModelAttribute`, or `@PathVariable` parameter triggers validation. Validation errors throw `MethodArgumentNotValidException` (for `@RequestBody` and `@ModelAttribute`) or `ConstraintViolationException` (for `@RequestParam`, `@PathVariable` with `@Validated` on the class). Both can be handled in `@ExceptionHandler` methods in a `@RestControllerAdvice`.

---

## Spring Data

**Q21. What is the difference between `CrudRepository`, `PagingAndSortingRepository`, and `JpaRepository`?**

`CrudRepository` provides CRUD operations: `save`, `findById`, `findAll`, `delete`, `count`, `existsById`. `PagingAndSortingRepository` extends it with `findAll(Pageable)` and `findAll(Sort)`. `JpaRepository` extends both and adds JPA-specific operations: `flush`, `saveAndFlush`, `saveAllAndFlush`, `deleteAllInBatch`, `getById` (entity reference without a database hit), and `findAll(Example)`. In most Spring Data JPA projects, repositories extend `JpaRepository`.

---

**Q22. What is the self-invocation problem with `@Transactional`?**

`@Transactional` is implemented via an AOP proxy. When a class calls one of its own `@Transactional` methods from within another method in the same class, the call bypasses the proxy. The transaction attributes (propagation, isolation, rollbackFor) on the called method are silently ignored. The most reliable fix is to move the called method to a separate bean. Alternatively, inject the bean's own proxy via `ApplicationContext.getBean(self.class)` or enable AspectJ weaving.

---

**Q23. What is the difference between `REQUIRES_NEW` and `NESTED` propagation?**

`REQUIRES_NEW` always starts a new physical transaction, suspending the current one if there is one. The inner transaction commits or rolls back independently. If the inner transaction rolls back, it does not affect the outer transaction (unless the exception propagates to the outer transaction and causes it to roll back too). `NESTED` uses a savepoint within the existing physical transaction. If the inner work rolls back, only the savepoint is rolled back; the outer transaction continues. `NESTED` requires the JDBC driver to support savepoints; `REQUIRES_NEW` requires the transaction manager to support suspension. For independent audit logging, `REQUIRES_NEW` is the correct choice.

---

**Q24. What are JPA projections and when would you use a DTO projection?**

A projection is a query result that contains only a subset of an entity's fields. Interface projections (an interface with getter methods matching field names) use Spring Data's proxy mechanism and are convenient but add proxy overhead. Class projections (a class or record with a matching constructor) map directly to a constructor expression in JPQL and have no proxy overhead. DTO projections are preferable when the result is read-only, performance matters, or the projection crosses entity boundaries. Avoid loading full entities when only a few fields are needed.

---

## Spring Security

**Q25. How does Spring Security's filter chain work?**

Spring Security registers a `DelegatingFilterProxy` as a Servlet filter. It delegates to a `FilterChainProxy` bean in the Spring context, which holds an ordered list of `SecurityFilterChain` instances. For each request, `FilterChainProxy` finds the first `SecurityFilterChain` whose request matcher matches the request and runs its filters in order. Key filters in the default chain (in execution order): `SecurityContextHolderFilter`, `UsernamePasswordAuthenticationFilter`, `AnonymousAuthenticationFilter`, `ExceptionTranslationFilter`, `AuthorizationFilter`.

---

**Q26. What is `SecurityContextHolder` and what storage strategies does it support?**

`SecurityContextHolder` stores the current `SecurityContext` (which contains the `Authentication` object) and makes it available throughout the call stack. Default strategy: `MODE_THREADLOCAL` — a `ThreadLocal` that is isolated to the current thread. Other strategies: `MODE_INHERITABLETHREADLOCAL` — propagates to child threads (useful for `@Async` if not using `DelegatingSecurityContextExecutor`); `MODE_GLOBAL` — a single shared context (for single-threaded applications only).

---

**Q27. What is the difference between a role and an authority in Spring Security?**

An authority is any string granted to a principal. A role is an authority with the prefix `ROLE_`. Methods like `hasRole("ADMIN")` automatically prepend `ROLE_`, so they check for `ROLE_ADMIN`. `hasAuthority("ROLE_ADMIN")` checks the full string. When using `User.roles("ADMIN")`, Spring Security automatically stores `ROLE_ADMIN`. When using `User.authorities("ROLE_ADMIN")`, the prefix is stored as-is. The convention is to use `hasRole` for coarse-grained roles and `hasAuthority` for fine-grained permissions.

---

**Q28. What is `@EnableMethodSecurity` and what annotations does it activate?**

`@EnableMethodSecurity` activates AOP-based method security. With default settings it enables `@PreAuthorize`, `@PostAuthorize`, `@PreFilter`, and `@PostFilter`. Legacy `@Secured` and `@RolesAllowed` (JSR-250) are not enabled by default; they require `securedEnabled = true` and `jsr250Enabled = true`. It replaces the older `@EnableGlobalMethodSecurity`.

---

## Spring AOP

**Q29. What is the self-invocation problem in Spring AOP?**

Spring AOP proxies wrap Spring-managed beans. When a method in class A calls another method in the same class A, the call goes directly to the target object, not through the proxy. Any AOP advice (including `@Transactional`, `@Cacheable`, `@Async`, `@PreAuthorize`) on the called method is never applied. Solutions: move the called method to a separate Spring-managed bean; use `AopContext.currentProxy()` with `exposeProxy = true`; use full AspectJ load-time weaving.

---

**Q30. When would you choose `@Around` over `@Before` + `@AfterReturning`?**

Use `@Around` when you need to: control whether the target method executes at all (retry, circuit breaker); modify the method's arguments before the call; modify or replace the return value; measure elapsed time (you need `System.nanoTime()` at both entry and exit, which can only be done in a single method with `@Around`); or catch and handle exceptions without re-throwing. Use `@Before` or `@AfterReturning` when their narrower semantics match exactly — they are clearer in intent and safer (you cannot accidentally forget to call `proceed()`).

---

**Q31. What is the difference between JDK dynamic proxy and CGLIB proxy?**

JDK dynamic proxy is used when the bean implements at least one interface and `proxyTargetClass = false`. The proxy implements the same interface(s) and delegates method calls to the target. CGLIB proxy is used when the bean does not implement any interface, or when `proxyTargetClass = true`. CGLIB generates a subclass of the target class at runtime. Spring Boot 2.x+ defaults to CGLIB (`spring.aop.proxy-target-class=true`). CGLIB cannot proxy `final` classes or `final` methods. JDK proxies cannot be cast to the concrete class.

---

**Q32. What is `@Transactional(readOnly = true)` and why use it?**

`readOnly = true` is a hint to the transaction manager and the underlying JPA provider. For JPA: Hibernate's dirty checking (automatic change detection at flush time) is disabled, which reduces memory and CPU overhead for queries. For the transaction manager: some managers (e.g., Spring Data's `@EnableTransactionManagement` with a replication-aware data source) can route read-only transactions to a read replica. For databases that support it: the JDBC driver can set the connection to read-only mode, which may optimize locking. It does not guarantee that write operations are prevented at the application level.

---

**Q33. How does `@Async` work and what are its limitations?**

`@Async` is implemented via AOP. A method annotated with `@Async` is executed in a separate thread from a `TaskExecutor`. The method must be on a Spring-managed bean, not the same class that calls it (self-invocation problem). The return type must be `void`, `Future<T>`, `CompletableFuture<T>`, or `ListenableFuture<T>`. Exceptions thrown in `@Async` methods with a `void` return type are passed to `AsyncUncaughtExceptionHandler`. The `SecurityContext` is not propagated to the async thread by default; use `DelegatingSecurityContextExecutor`.

---

**Q34. What happens when you inject a prototype-scoped bean into a singleton?**

The singleton is created once. Its prototype-scoped dependency is also created once, at the time the singleton is initialized, and is held as a field. The prototype bean is effectively a singleton from the caller's perspective — a new instance is never created again after the first injection. Solutions: inject `ObjectProvider<ProtoBean>` and call `getObject()` on each use; use `@Lookup` method injection (Spring overrides the method to call `getBean()` each time); use a scoped proxy with `proxyMode = ScopedProxyMode.TARGET_CLASS`.

---

**Q35. What is `CommandLineRunner` and `ApplicationRunner`? When do they run?**

Both are functional interfaces that are called by `SpringApplication` after the `ApplicationContext` is fully refreshed and before `SpringApplication.run()` returns. `CommandLineRunner.run(String... args)` receives raw command-line arguments as strings. `ApplicationRunner.run(ApplicationArguments args)` receives an `ApplicationArguments` object that parses option arguments (`--key=value`) and non-option arguments separately. Multiple runners are ordered with `@Order`. Use them for initialization logic that should run once at startup (seeding data, verifying external services).
