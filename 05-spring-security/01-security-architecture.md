# Security Architecture

## Overview

Spring Security is a servlet filter chain. Every HTTP request that reaches a Spring Security-protected application passes through a `DelegatingFilterProxy`, which delegates to a `FilterChainProxy`. The `FilterChainProxy` holds one or more `SecurityFilterChain` instances, each containing an ordered list of security filters and a request matcher that determines which requests the chain applies to. This layered delegation keeps the security infrastructure isolated from the servlet container while remaining pluggable.

The `SecurityContextHolder` is the thread-local store for the currently authenticated principal. Every security decision in the framework reads from or writes to it. The default storage strategy uses `ThreadLocal`, which binds the `SecurityContext` to the thread that processes the request. For asynchronous code, this has significant implications: spawned threads do not inherit the parent's `SecurityContext` unless explicitly propagated.

`AuthenticationManager` is the single entry point for authentication decisions. Its primary implementation is `ProviderManager`, which delegates to a list of `AuthenticationProvider` implementations in order. Each provider is responsible for one authentication mechanism (username/password, LDAP, OAuth2 token, etc.). `ProviderManager` iterates through providers until one returns a non-null `Authentication` or throws an `AuthenticationException`.

The `Authentication` object carries three pieces of information: the principal (the identity — typically a `UserDetails` or a string), the credentials (password or token — cleared after authentication), and the collection of granted authorities. A fully authenticated `Authentication` has `isAuthenticated()` returning `true` and is stored in the `SecurityContext` for the duration of the request.

## Key Concepts

### SecurityFilterChain

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain apiSecurityChain(HttpSecurity http) throws Exception {
        return http
            // Apply this chain to API requests only
            .securityMatcher("/api/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .anyRequest().authenticated())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .build();
    }

    // A second chain for other (non-API) requests
    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/css/**").permitAll()
                .anyRequest().authenticated())
            .formLogin(Customizer.withDefaults())
            .build();
    }
}
```

Multiple `SecurityFilterChain` beans are ordered by `@Order`. The first chain whose `securityMatcher` matches the request handles it; subsequent chains are skipped.

### AuthenticationManager and ProviderManager

```
Request with credentials
        │
        ▼
AuthenticationManager.authenticate(token)
        │
        ▼
ProviderManager iterates AuthenticationProviders:
  ├── DaoAuthenticationProvider.authenticate()  (handles UsernamePasswordAuthenticationToken)
  │     ├── UserDetailsService.loadUserByUsername(username)
  │     ├── PasswordEncoder.matches(raw, encoded)
  │     └── Returns fully authenticated token (with UserDetails as principal)
  │
  ├── JwtAuthenticationProvider.authenticate()  (handles BearerTokenAuthenticationToken)
  └── LdapAuthenticationProvider.authenticate()
```

### SecurityContextHolder Storage Strategies

| Strategy | Thread safety | Use case |
|---|---|---|
| `MODE_THREADLOCAL` (default) | Per-thread | Synchronous servlet requests |
| `MODE_INHERITABLETHREADLOCAL` | Inherited by child threads | Threads spawned with `Thread` directly |
| `MODE_GLOBAL` | Shared globally | Single-threaded applications |

```java
// Read the current authentication
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
String username = auth.getName();

// Set authentication programmatically (e.g., in a test or filter)
SecurityContext context = SecurityContextHolder.createEmptyContext();
context.setAuthentication(authentication);
SecurityContextHolder.setContext(context);
```

### SecurityContext in Async Code

`ThreadLocal` storage means that threads spawned from a request thread do not carry the authentication:

```java
@Service
public class AsyncService {

    @Async
    public void doAsyncWork() {
        // SecurityContextHolder.getContext().getAuthentication() is null here
        // because this runs in a thread pool thread that was not the request thread.
    }
}
```

Solutions:

1. Configure `SecurityContextHolder` to use `MODE_INHERITABLETHREADLOCAL` — inherited by threads spawned via `Thread` but not by `TaskExecutor` pools.

2. Use `DelegatingSecurityContextExecutor` to wrap the `TaskExecutor`:

```java
@Bean
public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.initialize();
    // Wraps the executor to propagate SecurityContext to submitted tasks
    return new DelegatingSecurityContextExecutor(executor);
}
```

3. Capture the context manually before submitting work:

```java
SecurityContext context = SecurityContextHolder.getContext();
taskExecutor.execute(SecurityContextRunnable.create(() -> doWork(), context));
```

### The FilterChainProxy Architecture

```
HTTP Request
    │
    ▼
DelegatingFilterProxy (registered in servlet container)
    │  (delegates to Spring-managed bean)
    ▼
FilterChainProxy
    │
    ├── SecurityFilterChain[0] (matches /api/**)
    │     ├── SecurityContextHolderFilter
    │     ├── UsernamePasswordAuthenticationFilter
    │     ├── ExceptionTranslationFilter
    │     └── AuthorizationFilter
    │
    └── SecurityFilterChain[1] (matches /**)
          ├── SecurityContextHolderFilter
          ├── LogoutFilter
          ├── UsernamePasswordAuthenticationFilter
          ├── DefaultLoginPageGeneratingFilter
          ├── ExceptionTranslationFilter
          └── AuthorizationFilter
```

## Gotchas

Configuring `SecurityContextHolder.setStrategyName(MODE_INHERITABLETHREADLOCAL)` propagates the security context to child threads created via `new Thread()`, but not to threads from a `ThreadPoolExecutor` — pooled threads are not children of the request thread. The `DelegatingSecurityContextExecutor` is the correct solution for thread pool propagation.

When multiple `SecurityFilterChain` beans are declared without `@Order`, Spring resolves the order arbitrarily (usually by bean declaration order). The first matching chain handles the request. If the more general chain (matching `/**`) is ordered before the more specific one (matching `/api/**`), the specific chain is never reached. Always specify `@Order` explicitly on multiple chains.

`@EnableWebSecurity` disables Spring Boot's auto-configured `SecurityFilterChain`. Without it, Boot's default chain (requiring authentication for all requests) is active. Adding the annotation requires that the application provides at least one `SecurityFilterChain` bean, otherwise no security chain is configured and all requests are permitted without authentication.

The `SecurityContextPersistenceFilter` (deprecated in Spring Security 6, replaced by `SecurityContextHolderFilter`) was responsible for loading and saving the `SecurityContext` from the `HttpSession` between requests. The replacement `SecurityContextHolderFilter` delegates to `SecurityContextRepository`. For stateless APIs, configure `HttpSessionSecurityContextRepository.setAllowSessionCreation(false)` or use `SessionCreationPolicy.STATELESS` to prevent session creation entirely.

Calling `SecurityContextHolder.clearContext()` explicitly in a filter or after authentication is correct for cleanup but should be done in a `finally` block to ensure it runs even if the subsequent code throws. Leaving a `SecurityContext` in `ThreadLocal` when a thread pool reuses the thread will cause the previous request's authentication to leak into the next request.
