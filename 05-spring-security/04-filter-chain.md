# Filter Chain

## Overview

Spring Security's behavior is implemented as an ordered list of servlet filters within each `SecurityFilterChain`. Each filter is responsible for one concern: loading the security context, authenticating credentials, handling session management, translating exceptions to HTTP responses, and enforcing authorization. Understanding which filters are present, their ordering, and what each one does is essential for diagnosing unexpected authentication failures, adding custom authentication mechanisms, and knowing where to place custom filters.

The ordering of Spring Security's built-in filters is defined by the `SecurityFilterChain.FILTER_ORDER` constants and the `FilterOrderRegistration` in the framework internals. Filters added via `HttpSecurity.addFilterBefore()`, `addFilterAfter()`, or `addFilterAt()` are placed relative to the built-in filters using these positions. The ordering determines execution precedence: a filter that adds authentication to the `SecurityContext` must run before the `AuthorizationFilter` that checks that context.

`ExceptionTranslationFilter` is the bridge between the security filter chain and the HTTP response for security-related exceptions. It catches `AuthenticationException` (unauthenticated) and `AccessDeniedException` (unauthorized) and translates them to appropriate HTTP responses via the configured `AuthenticationEntryPoint` and `AccessDeniedHandler`. Exceptions thrown before `ExceptionTranslationFilter` or after `AuthorizationFilter` are not handled by it.

Custom authentication mechanisms — API key authentication, JWT validation, multi-factor authentication — are implemented as filters positioned appropriately in the chain. A JWT validation filter, for example, runs before `AuthorizationFilter` to populate the `SecurityContext`, and typically replaces the `UsernamePasswordAuthenticationFilter` position for stateless APIs.

## Key Concepts

### Key Filters and Their Positions

```text
Order  Filter                                    Role
-----  ----------------------------------------  -----------------------------------------
100    DisableEncodeUrlFilter                    Prevents session ID in URL
200    WebAsyncManagerIntegrationFilter          Propagates SecurityContext in async dispatch
300    SecurityContextHolderFilter               Loads SecurityContext; clears after request
400    HeaderWriterFilter                        Adds security headers (CSP, X-Frame, etc.)
500    CorsFilter                                Handles CORS preflight and CORS headers
600    CsrfFilter                                Validates CSRF token for state-changing requests
700    LogoutFilter                              Handles /logout requests
800    UsernamePasswordAuthenticationFilter      Processes form login (/login POST)
900    DefaultLoginPageGeneratingFilter          Generates the default login page
1100  BearerTokenAuthenticationFilter           Processes Bearer token (OAuth2 resource server)
1500  RequestCacheAwareFilter                   Saves/restores request after authentication
1600  SecurityContextHolderAwareRequestFilter   Wraps request with security-aware methods
1700  AnonymousAuthenticationFilter             Sets anonymous token if no auth present
1800  SessionManagementFilter                   Session fixation, concurrent session control
1900  ExceptionTranslationFilter               Handles Auth/Access exceptions
2000  AuthorizationFilter                       Enforces HTTP-level authorization rules
```

### Adding Custom Filters

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        // Place the custom JWT filter before the authorization filter
        .addFilterBefore(jwtAuthenticationFilter(), AuthorizationFilter.class)
        // Place after the SecurityContextHolderFilter — SecurityContext is available
        .addFilterAfter(requestLoggingFilter(), SecurityContextHolderFilter.class)
        // Replace an existing filter at its position
        .addFilterAt(customCsrfFilter(), CsrfFilter.class)
        .build();
}
```

### SecurityContextHolderFilter

This filter replaced `SecurityContextPersistenceFilter` in Spring Security 6. It loads the `SecurityContext` from the `SecurityContextRepository` at the start of the request and clears it after the response is committed:

```text
Request start:
  SecurityContextRepository.loadDeferredContext(request)
  → SecurityContext loaded (from session for stateful apps, empty for stateless)
  SecurityContextHolder.setContext(context)

Request end (in finally block):
  SecurityContextHolder.clearContext()
  SecurityContextRepository.saveContext(context, request, response)
  → Context saved (to session for stateful apps, no-op for stateless)
```

For stateless APIs, configure `NullSecurityContextRepository` to avoid session creation:

```java
http.securityContext(ctx -> ctx.securityContextRepository(
        new NullSecurityContextRepository()));
```

### ExceptionTranslationFilter and Entry Points

```java
// AuthenticationEntryPoint — called when authentication is required
// Default for web: LoginUrlAuthenticationEntryPoint (redirects to /login)
// For APIs: Http401UnauthorizedEntryPoint or custom

@Component
public class JsonAuthEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write("{\"error\": \"Authentication required\"}");
    }
}

// AccessDeniedHandler — called when an authenticated user lacks permissions
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write("{\"error\": \"Access denied\"}");
    }
}

// Registration
http.exceptionHandling(ex -> ex
        .authenticationEntryPoint(jsonAuthEntryPoint)
        .accessDeniedHandler(jsonAccessDeniedHandler));
```

### AnonymousAuthenticationFilter

This filter ensures that `SecurityContextHolder.getContext().getAuthentication()` is never null. If no authentication has been established by earlier filters, it creates an `AnonymousAuthenticationToken` with the role `ROLE_ANONYMOUS`. This means code that checks `authentication != null` will always see a non-null authentication — the correct check for anonymous access is `authentication instanceof AnonymousAuthenticationToken`.

## Gotchas

Adding a custom filter with `addFilterBefore(UsernamePasswordAuthenticationFilter.class)` places it before the form login filter, which means it runs before any credential authentication. If the custom filter attempts to read the authenticated user, the `SecurityContext` may be empty. Ensure the filter's position in the chain matches its dependency on prior filters.

`OncePerRequestFilter` is the correct base class for custom security filters. It prevents the filter from running multiple times for a single request (which can happen due to servlet container forwards and includes). Without it, filters may run twice for error-forwarded requests.

`ExceptionTranslationFilter` only catches exceptions thrown by filters that execute after it (i.e., filters with a higher order number). An `AuthenticationException` thrown by a filter with a lower order number than `ExceptionTranslationFilter` propagates to the servlet container, bypassing the configured `AuthenticationEntryPoint`. Ensure custom authentication filters are positioned after `ExceptionTranslationFilter` if they need its exception handling.

The `CsrfFilter` validates CSRF tokens before the `UsernamePasswordAuthenticationFilter`. A missing or invalid CSRF token causes `CsrfFilter` to block the request with `403 Forbidden` before the authentication filter can run. This means a CSRF validation failure looks like a 403 even when the credentials would have been correct. For stateless JWT APIs, disable CSRF: `http.csrf(csrf -> csrf.disable())`.

`SessionManagementFilter` detects session fixation attacks and rotates the session ID after successful authentication. If the session rotation interferes with a multi-server environment (e.g., sticky sessions not configured correctly), authentication succeeds but the client's session cookie references an old session that no longer exists on the server. Configure session fixation protection to match the deployment topology.
