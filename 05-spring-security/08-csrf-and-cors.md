# CSRF and CORS

## Overview

CSRF (Cross-Site Request Forgery) and CORS (Cross-Origin Resource Sharing) are distinct mechanisms that are often confused because both involve cross-origin requests, but they operate at different layers and serve opposite purposes.

**CSRF** is an attack where a malicious site causes a victim's browser to send a credentialed request (with cookies or HTTP Basic credentials) to a target application. Spring Security defends against it by requiring a secret token that the attacker cannot read from a different origin. CSRF protection is relevant whenever the browser automatically attaches credentials — i.e., when the application uses cookies or HTTP Basic authentication.

**CORS** is a browser security policy that blocks JavaScript from reading responses to cross-origin requests unless the server explicitly permits it. CORS configuration tells the browser which origins, methods, and headers are allowed. CORS is a browser enforcement mechanism; it does not protect the server from server-to-server or non-browser requests.

The two configurations interact because a CORS preflight (`OPTIONS`) request must be permitted before the actual request can be authorized. Spring Security must allow preflights to pass without CSRF validation or authentication.

## Key Concepts

### When to Disable CSRF

CSRF protection should be **disabled** only when all of the following are true:
- The API is stateless and uses token-based authentication (e.g., JWT in the `Authorization` header).
- Credentials are never sent via browser-managed mechanisms (cookies, HTTP Basic).
- There are no endpoints accessible to browser-based clients that store session cookies.

CSRF protection should be **kept enabled** when:
- The application uses form login, cookie-based sessions, or HTTP Basic authentication with browsers.
- The API is consumed by a browser application that shares the same origin or uses cookies.

```java
// Stateless REST API: disable CSRF.
http.csrf(csrf -> csrf.disable());

// Traditional web app with session cookies: keep CSRF enabled (default).
// Spring Security's defaults are correct; no explicit configuration needed.
```

### CsrfTokenRepository

When CSRF is enabled, Spring Security stores and validates the CSRF token using a `CsrfTokenRepository`. The default is `HttpSessionCsrfTokenRepository`, which stores the token in the HTTP session. For SPAs that make Ajax requests, `CookieCsrfTokenRepository` is more practical: it writes the token to a cookie that JavaScript can read and send back in a custom header.

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf
            // withHttpOnlyFalse() makes the cookie readable by JavaScript.
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            // Spring Security 6: explicitly load the token on each request.
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
    return http.build();
}
```

The SPA reads the `XSRF-TOKEN` cookie and sends it back as the `X-XSRF-TOKEN` header. The server validates the header value against the token in the repository.

### CsrfToken in Templates

For server-rendered templates (Thymeleaf, FreeMarker), the CSRF token is automatically included in forms via Spring Security's template integration. For explicit inclusion:

```html
<!-- Thymeleaf automatically adds _csrf to forms via th:action -->
<form th:action="@{/submit}" method="post">
    <input type="hidden" name="_csrf" th:value="${_csrf.token}"/>
    ...
</form>
```

### CORS Configuration

CORS configuration can be applied at three levels: individual methods (`@CrossOrigin`), globally via `WebMvcConfigurer`, or via a Spring Security `CorsConfigurationSource`. Only the last approach integrates correctly with the Spring Security filter chain, including preflight handling.

```java
// Spring Security CORS configuration — applies before security filters process the request.
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("https://app.example.com"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
    config.setExposedHeaders(List.of("X-Total-Count"));  // headers the browser is allowed to read
    config.setAllowCredentials(true);   // required if cookies or Authorization header are sent
    config.setMaxAge(3600L);            // preflight result cache duration in seconds

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}

@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
    return http.build();
}
```

### Preflight Request Handling

An `OPTIONS` preflight request must be allowed through without authentication or CSRF validation. Spring Security's `CorsFilter` (activated by `.cors(...)`) intercepts preflight requests and responds immediately without passing them to downstream filters. This is why CORS must be configured in the Spring Security filter chain rather than only in Spring MVC — MVC's `CorsInterceptor` is never reached if the request is rejected by the security filter chain first.

```java
// If CORS is configured at the Spring Security level, preflights are handled automatically.
// Do NOT add a separate permitAll() for OPTIONS if using cors() — it is redundant.

// If CORS is NOT configured at the security level, OPTIONS must be explicitly permitted:
.authorizeHttpRequests(auth -> auth
    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
    .anyRequest().authenticated())
```

### allowedOrigins vs allowedOriginPatterns

`setAllowedOrigins(List.of("*"))` cannot be combined with `setAllowCredentials(true)` — the browser requires an explicit origin, not a wildcard, when credentials are included. Use `setAllowedOriginPatterns` for wildcard matching with credentials:

```java
config.setAllowedOriginPatterns(List.of("https://*.example.com"));
config.setAllowCredentials(true);
```

### Per-Endpoint CORS

For different CORS policies on different path prefixes:

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

    CorsConfiguration publicConfig = new CorsConfiguration();
    publicConfig.setAllowedOrigins(List.of("*"));
    publicConfig.setAllowedMethods(List.of("GET"));
    source.registerCorsConfiguration("/public/**", publicConfig);

    CorsConfiguration apiConfig = new CorsConfiguration();
    apiConfig.setAllowedOrigins(List.of("https://app.example.com"));
    apiConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
    apiConfig.setAllowCredentials(true);
    source.registerCorsConfiguration("/api/**", apiConfig);

    return source;
}
```

### Interaction Between CSRF and CORS

| Scenario | CSRF | CORS |
|---|---|---|
| Server-rendered app, cookie session | Enabled | Typically same-origin; minimal CORS config |
| SPA on same domain, cookie session | Enabled with `CookieCsrfTokenRepository` | Same origin; CORS not needed |
| SPA on different domain, JWT | Disabled | Enabled with explicit allowed origins |
| Mobile/desktop client, JWT | Disabled | Not applicable (no browser) |
| API consumed by both SPA and mobile | Disabled | Enabled for SPA origins |

## Gotchas

**CORS is not security.** CORS prevents browsers from reading cross-origin responses, but it does not prevent requests from being sent. A server-side CSRF attack, Postman, `curl`, or any server-to-server call completely bypasses CORS. CORS protects the client's data, not the server.

**Spring Security `cors()` vs MVC `addCorsMappings`.** Both configurations can coexist but can conflict if configured differently. Prefer a single `CorsConfigurationSource` bean registered in Spring Security. MVC's `addCorsMappings` runs inside the DispatcherServlet, which is too late for requests that are rejected by the security filter chain.

**`SameSite` cookie attribute.** Modern browsers enforce the `SameSite` attribute on cookies. `SameSite=Strict` or `SameSite=Lax` prevents cookies from being sent on cross-origin requests, which mitigates CSRF independently of Spring Security's CSRF token mechanism. `SameSite=None` (required for cross-origin cookies) requires `Secure` and must be accompanied by CSRF protection.

**Preflight caching.** `setMaxAge(3600L)` tells the browser to cache preflight results for one hour. Reducing this during development avoids stale CORS responses when the configuration changes, but keep it high in production to reduce preflight overhead.

**Double CORS filter.** If `spring-boot-starter-web` and a custom `FilterRegistrationBean<CorsFilter>` both register CORS filters, preflights may be handled twice with inconsistent results. Register CORS in exactly one place.
