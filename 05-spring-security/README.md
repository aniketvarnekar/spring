# Spring Security

This section covers Spring Security from the filter chain and security context through authentication mechanisms, authorization models, JWT stateless auth, OAuth2 resource servers, and CSRF/CORS handling. The emphasis is on how the framework's components interact and where the correct extension points are for each customization.

## Notes

| File | Description |
|------|-------------|
| [01-security-architecture.md](./01-security-architecture.md) | SecurityFilterChain, AuthenticationManager, SecurityContextHolder, async propagation |
| [02-authentication.md](./02-authentication.md) | UsernamePasswordAuthenticationToken flow, UserDetailsService, PasswordEncoder, DaoAuthenticationProvider |
| [03-authorization.md](./03-authorization.md) | AuthorizationManager, HttpSecurity.authorizeHttpRequests, request matchers, role vs authority |
| [04-filter-chain.md](./04-filter-chain.md) | Filter positions, key filters and their roles, addFilterBefore/addFilterAfter |
| [05-method-security.md](./05-method-security.md) | @EnableMethodSecurity, @PreAuthorize, @PostAuthorize, @PreFilter, @PostFilter, SpEL |
| [06-jwt.md](./06-jwt.md) | Stateless auth design, JWT validation filter, SecurityContext population, token refresh |
| [07-oauth2.md](./07-oauth2.md) | Resource server config, JWT decoder, opaque token introspection, OAuth2 login, claims mapping |
| [08-csrf-and-cors.md](./08-csrf-and-cors.md) | When to disable CSRF, CsrfTokenRepository, CorsConfigurationSource, preflight and filter chain |

## Examples

| Project | Description |
|---------|-------------|
| [SecurityConfigDemo](./examples/SecurityConfigDemo/) | Form login, in-memory users, role-based HTTP authorization, and method security |
| [JwtAuthDemo](./examples/JwtAuthDemo/) | Stateless JWT authentication with a custom OncePerRequestFilter |
| [MethodSecurityDemo](./examples/MethodSecurityDemo/) | @PreAuthorize and @PostFilter on service methods with custom SpEL expressions |
| [OAuth2Demo](./examples/OAuth2Demo/) | Resource server configuration accepting JWTs from an authorization server |
