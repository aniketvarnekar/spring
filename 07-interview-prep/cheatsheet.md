# Spring Cheatsheet

## Core Annotations

| Annotation | Purpose |
|---|---|
| `@SpringBootApplication` | `@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan` |
| `@Configuration` | Declares a class as a source of `@Bean` definitions; CGLIB-proxied by default |
| `@Bean` | Declares a method as a bean factory; return value registered in the container |
| `@Component`, `@Service`, `@Repository`, `@Controller` | Stereotype annotations; detected by `@ComponentScan` |
| `@Autowired` | Inject dependencies; on constructor (preferred), setter, or field |
| `@Qualifier("name")` | Disambiguate when multiple candidates of a type exist |
| `@Primary` | Mark a bean as the default candidate when multiple exist |
| `@Lazy` | Defer bean initialization until first use |
| `@Scope("prototype")` | Create a new bean instance per request to the container |
| `@Profile("dev")` | Register a bean only when the named profile is active |
| `@Conditional(Condition.class)` | Register a bean only when `Condition.matches()` returns true |
| `@DependsOn("beanName")` | Force initialization order |
| `@PostConstruct` | Run after DI is complete; called before AOP advice is applied |
| `@PreDestroy` | Run before the bean is removed from the container |
| `@Value("${prop}")` | Inject a single property value or SpEL expression |
| `@ConfigurationProperties(prefix = "x")` | Bind a group of properties to a Java object |
| `@EnableConfigurationProperties` | Register `@ConfigurationProperties` classes not annotated with `@Component` |
| `@PropertySource("classpath:custom.properties")` | Add a property file to the `Environment` |
| `@Import(SomeConfig.class)` | Import another `@Configuration` class |
| `@ImportResource("classpath:beans.xml")` | Import XML bean definitions |
| `@EventListener` | Declare a method as an application event listener |
| `@Async` | Execute a method asynchronously in a `TaskExecutor` thread |
| `@EnableAsync` | Activate `@Async` processing |
| `@Scheduled` | Schedule a method to run periodically |
| `@EnableScheduling` | Activate `@Scheduled` processing |

---

## Spring MVC Annotations

| Annotation | Purpose |
|---|---|
| `@RestController` | `@Controller` + `@ResponseBody`; all methods return serialized response bodies |
| `@RequestMapping` | Map a URL pattern to a class or method |
| `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping` | HTTP-method-specific mapping shortcuts |
| `@PathVariable` | Bind a URI template variable to a method parameter |
| `@RequestParam` | Bind a query parameter to a method parameter |
| `@RequestBody` | Deserialize the request body into a method parameter |
| `@ResponseBody` | Serialize the return value as the response body |
| `@ResponseStatus(HttpStatus.CREATED)` | Set a fixed HTTP status on a handler method |
| `@ExceptionHandler` | Handle an exception thrown by a controller method |
| `@RestControllerAdvice` | Global `@ExceptionHandler`/`@ModelAttribute` with `@ResponseBody` |
| `@Valid` | Trigger bean validation on a method parameter |
| `@Validated` | Like `@Valid` but enables validation groups; required on the class for path/query param validation |
| `@CrossOrigin` | Enable CORS on a specific controller or method |

---

## Spring Data Annotations

| Annotation | Purpose |
|---|---|
| `@Entity` | JPA entity class |
| `@Table(name = "orders")` | Override the default table name |
| `@Id` | Primary key field |
| `@GeneratedValue(strategy = GenerationType.IDENTITY)` | Auto-increment primary key |
| `@Column(nullable = false, unique = true)` | Column constraints |
| `@ManyToOne`, `@OneToMany`, `@ManyToMany`, `@OneToOne` | JPA relationship mappings |
| `@JoinColumn(name = "fk_col")` | Foreign key column for a relationship |
| `@Embedded` / `@Embeddable` | Map a value object inline into the entity table |
| `@MappedSuperclass` | Share common fields without a separate table |
| `@Inheritance(strategy = InheritanceType.SINGLE_TABLE)` | Map an inheritance hierarchy |
| `@DiscriminatorColumn` / `@DiscriminatorValue` | Discriminator for `SINGLE_TABLE` inheritance |
| `@Query("JPQL")` | Custom JPQL query on a repository method |
| `@Modifying` | Mark a `@Query` as a DML statement (UPDATE/DELETE) |
| `@Param("name")` | Bind a named parameter in a `@Query` |
| `@Transactional` | Declare transaction boundaries |
| `@EnableJpaAuditing` | Activate `@CreatedDate`, `@LastModifiedDate`, etc. |
| `@EntityListeners(AuditingEntityListener.class)` | Apply JPA auditing to an entity |
| `@EnableJpaRepositories` | Configure the repository base package and factory class |

---

## Spring Security Annotations

| Annotation | Purpose |
|---|---|
| `@EnableWebSecurity` | Enable Spring Security's web configuration |
| `@EnableMethodSecurity` | Activate `@PreAuthorize`, `@PostAuthorize`, `@PreFilter`, `@PostFilter` |
| `@PreAuthorize("hasRole('ADMIN')")` | Authorize before method execution using SpEL |
| `@PostAuthorize("returnObject.ownerId == authentication.name")` | Authorize after method execution |
| `@PreFilter` / `@PostFilter` | Filter collection parameters or return values |

---

## Spring AOP Annotations

| Annotation | Purpose |
|---|---|
| `@EnableAspectJAutoProxy` | Enable AspectJ auto-proxy support (auto-activated by Spring Boot with `spring-boot-starter-aop`) |
| `@Aspect` | Declare an aspect class |
| `@Pointcut` | Declare a named pointcut expression |
| `@Before` | Before-advice: runs before the method |
| `@After` | After-advice (finally): runs after the method, regardless of outcome |
| `@AfterReturning` | Runs after the method returns normally |
| `@AfterThrowing` | Runs after the method throws |
| `@Around` | Around-advice: controls whether and how the method executes |
| `@Order(n)` | Set precedence when multiple aspects apply to the same join point |

---

## Key Interfaces

| Interface | Purpose |
|---|---|
| `ApplicationContext` | Spring container; extends `BeanFactory` |
| `BeanPostProcessor` | Hooks into bean initialization; can return a proxy |
| `BeanFactoryPostProcessor` | Modifies `BeanDefinition` objects before instantiation |
| `ApplicationEventPublisher` | Publish application events |
| `ApplicationListener<E>` | Receive application events |
| `InitializingBean` | `afterPropertiesSet()` called after DI |
| `DisposableBean` | `destroy()` called on container shutdown |
| `Ordered` / `PriorityOrdered` | Control ordering of beans (interceptors, filters, aspects) |
| `Condition` | `matches()` evaluated for `@Conditional` beans |
| `UserDetailsService` | Load a `UserDetails` by username (Spring Security) |
| `AuthenticationProvider` | Custom authentication logic (Spring Security) |
| `HandlerInterceptor` | `preHandle`/`postHandle`/`afterCompletion` hooks in Spring MVC |
| `WebMvcConfigurer` | Customize Spring MVC: interceptors, CORS, formatters, resource handlers |
| `CorsConfigurationSource` | Provide CORS configuration to Spring Security's filter chain |

---

## Bean Lifecycle — Callback Order

```text
1.  Instantiation (constructor)
2.  BeanNameAware.setBeanName()
3.  BeanFactoryAware.setBeanFactory()
4.  ApplicationContextAware.setApplicationContext()
5.  BeanPostProcessor.postProcessBeforeInitialization()
6.  @PostConstruct
7.  InitializingBean.afterPropertiesSet()
8.  @Bean(initMethod = "...")
9.  BeanPostProcessor.postProcessAfterInitialization()    ← AOP proxy created here
── bean is ready ──
10. @PreDestroy
11. DisposableBean.destroy()
12. @Bean(destroyMethod = "...")
```

---

## PropertySource Priority (Spring Boot — highest to lowest)

1. Command-line arguments (`--server.port=9090`)
2. `SPRING_APPLICATION_JSON` (JSON in an env var)
3. Servlet context/config init parameters
4. JNDI (`java:comp/env`)
5. Java system properties (`-Dkey=value`)
6. OS environment variables (`SERVER_PORT=9090`)
7. `application-{profile}.properties` / `.yaml`
8. `application.properties` / `.yaml`
9. `@PropertySource` on `@Configuration` classes
10. Default properties (`SpringApplication.setDefaultProperties`)

---

## Transaction Propagation Quick Reference

| Propagation | Existing transaction? | Behavior |
|---|---|---|
| `REQUIRED` (default) | Yes | Join it |
| `REQUIRED` | No | Start new |
| `REQUIRES_NEW` | Yes | Suspend; start independent new |
| `REQUIRES_NEW` | No | Start new |
| `NESTED` | Yes | Create savepoint within existing |
| `NESTED` | No | Start new |
| `SUPPORTS` | Yes | Join it |
| `SUPPORTS` | No | Run non-transactionally |
| `NOT_SUPPORTED` | Yes | Suspend; run non-transactionally |
| `MANDATORY` | No | Throw `IllegalTransactionStateException` |
| `NEVER` | Yes | Throw `IllegalTransactionStateException` |

---

## Common Spring Boot Slice Tests

| Annotation | What is loaded |
|---|---|
| `@SpringBootTest` | Full `ApplicationContext` |
| `@WebMvcTest` | Controllers, filters, `@ControllerAdvice`, security config |
| `@DataJpaTest` | JPA repositories, entity scanning, embedded database |
| `@DataMongoTest` | MongoDB repositories |
| `@DataRedisTest` | Redis repositories |
| `@RestClientTest` | `RestTemplate`/`RestClient` + mock server |
| `@JsonTest` | Jackson / Gson serialization |

---

## Pointcut Expression Quick Reference

```text
execution(modifiers? returnType declaringType? methodName(params) throws?)

execution(* com.example..*(..))       — any method in com.example or sub-packages
execution(public * *(..))             — any public method anywhere
execution(* *.save(..))               — any method named "save"
@annotation(com.example.Audited)      — methods annotated with @Audited
@within(org.springframework.stereotype.Service)  — all methods in @Service classes
within(com.example.service..*)        — all methods in the service package
bean(*Service)                        — all beans whose name ends with "Service"
args(Long, ..)                        — methods with a Long as the first argument
```

Combine with: `&&` (and), `||` (or), `!` (not).
