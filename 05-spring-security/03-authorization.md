# Authorization

## Overview

Authorization in Spring Security determines whether an authenticated principal is allowed to perform a requested operation. Spring Security 6 replaced the `AccessDecisionManager` / `AccessDecisionVoter` model with the simpler `AuthorizationManager<T>` interface, which takes the current authentication and the object being secured (a request, a method invocation, or a domain object) and returns an `AuthorizationDecision`. The new model is composable, testable, and eliminates the confusing "grant/deny/abstain" voting semantics.

HTTP-level authorization is configured on `HttpSecurity` using `authorizeHttpRequests()`. Rules are evaluated in declaration order — the first matching rule wins. This means that more specific rules must be declared before more general ones. A common mistake is placing `anyRequest().authenticated()` before more permissive rules for specific paths, blocking access that was intended to be public.

Spring Security distinguishes between roles and authorities. A role is an authority with the `ROLE_` prefix. The methods `hasRole("ADMIN")` and `hasAuthority("ROLE_ADMIN")` are equivalent; `hasRole()` adds the `ROLE_` prefix automatically. Methods like `grantedAuthorities()` and `authorities()` on `User.builder()` expect the full authority string including `ROLE_`. `roles()` adds the prefix. Mixing the two in the same application is a common source of authorization failures.

Request matchers determine which URLs each rule applies to. `AntPathRequestMatcher` uses Ant-style patterns (`/api/**`, `/user/{id}`). `MvcRequestMatcher` uses Spring MVC's path matching (more accurate for MVC applications because it respects the servlet mapping). Since Spring Security 6, `MvcRequestMatcher` is the recommended choice in Spring MVC applications to avoid path-matching inconsistencies.

## Key Concepts

### HttpSecurity.authorizeHttpRequests

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                HandlerMappingIntrospector introspector)
        throws Exception {
    MvcRequestMatcher.Builder mvc = new MvcRequestMatcher.Builder(introspector);

    return http
        .authorizeHttpRequests(auth -> auth
            // Order matters: specific rules before general rules
            .requestMatchers(mvc.pattern("/api/public/**")).permitAll()
            .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/products/**")).permitAll()
            .requestMatchers(mvc.pattern("/api/admin/**")).hasRole("ADMIN")
            .requestMatchers(mvc.pattern("/api/**")).authenticated()
            .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
            .anyRequest().authenticated())
        .build();
}
```

### Role vs Authority

```
GrantedAuthority string | hasRole("X") | hasAuthority("X")
------------------------+---------------+------------------
"ROLE_ADMIN"            | hasRole("ADMIN") = true | hasAuthority("ROLE_ADMIN") = true
"ADMIN"                 | hasRole("ADMIN") = false | hasAuthority("ADMIN") = true
```

```java
// When using User.builder():
User.builder()
    .roles("ADMIN", "USER")          // stores "ROLE_ADMIN", "ROLE_USER"
    .authorities("PERM_READ", "PERM_WRITE")  // stores exactly as given
    .build();

// In HttpSecurity:
.hasRole("ADMIN")          // checks for "ROLE_ADMIN" authority
.hasAuthority("PERM_READ") // checks for "PERM_READ" authority (exact string)
.hasAnyRole("ADMIN", "MOD") // checks for "ROLE_ADMIN" OR "ROLE_MOD"
```

### AuthorizationManager

The new authorization model in Spring Security 6:

```java
// Custom authorization logic via AuthorizationManager
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/orders/**")
            // Custom AuthorizationManager — replaces hasRole and SpEL expressions
            .access(new OrderOwnerAuthorizationManager()))
        .build();
}

public class OrderOwnerAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication,
                                       RequestAuthorizationContext context) {
        String orderId = context.getVariables().get("id");
        String username = authentication.get().getName();

        boolean isOwner = isOrderOwner(username, orderId);
        return new AuthorizationDecision(isOwner);
    }
}
```

### @Secured vs @PreAuthorize

| Annotation | SpEL support | Principal access | Groups/roles only | Recommended |
|---|---|---|---|---|
| `@Secured` | No | No | Roles/authorities | No |
| `@RolesAllowed` | No | No | Roles only | No |
| `@PreAuthorize` | Yes | Yes | Yes | Yes |

`@PreAuthorize` is the most powerful and the recommended choice. It supports full SpEL expressions that can reference the current authentication, method parameters, and custom security beans:

```java
@PreAuthorize("hasRole('ADMIN') or #userId == authentication.name")
public UserProfile getUserProfile(String userId) { ... }

@PreAuthorize("@orderSecurityService.canAccess(authentication, #orderId)")
public Order getOrder(String orderId) { ... }
```

### permitAll(), denyAll(), anonymous()

```
permitAll()           — allows access without authentication (including anonymous)
denyAll()             — denies access to everyone, including authenticated users
authenticated()       — requires authentication (not anonymous)
fullyAuthenticated()  — requires fresh authentication (not from remember-me)
anonymous()           — grants access only to unauthenticated (anonymous) users
```

## Gotchas

Authorization rules are evaluated in declaration order and the first match wins. If `.anyRequest().authenticated()` is declared before `.requestMatchers("/public/**").permitAll()`, the public path rule never applies — all requests are authenticated first. Place specific permissive rules before the catch-all.

`hasRole("ADMIN")` automatically prepends `ROLE_`. If the authority in the database is stored as `ADMIN` without the prefix and `UserDetailsService` loads it verbatim, `hasRole("ADMIN")` will never match (it checks for `ROLE_ADMIN`). Either store `ROLE_ADMIN` in the database, use `hasAuthority("ADMIN")`, or add the prefix in `UserDetailsService` when building the `GrantedAuthority` list.

`MvcRequestMatcher` requires the `HandlerMappingIntrospector` bean. Declaring `authorizeHttpRequests` with `AntPathRequestMatcher` (the old default) can mismatch paths that Spring MVC resolves differently — for example, paths with trailing slashes or path suffixes. In Spring MVC apps, always use `MvcRequestMatcher`.

`denyAll()` is rarely appropriate for most paths but is the correct rule for legacy endpoints that should exist in code but never be accessible. It is safer than deleting the handler (which might 404 silently) because it produces a clear authorization failure.

When `access()` is given a custom `AuthorizationManager`, it replaces the built-in matchers for that path. The custom manager is responsible for all authorization logic for that matcher, including checking authentication status. An `AuthorizationManager` that does not check `authentication.get().isAuthenticated()` will authorize unauthenticated requests.
