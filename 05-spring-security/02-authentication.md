# Authentication

## Overview

Authentication is the process of establishing who is making a request. Spring Security separates the representation of a credential (the `Authentication` token), the lookup of user details (the `UserDetailsService`), and the validation logic (the `AuthenticationProvider`). This separation allows different authentication mechanisms — database-backed users, LDAP, OAuth2 tokens, API keys — to plug into the same `AuthenticationManager` infrastructure.

The most common authentication flow for web applications is username/password: the client submits credentials, `UsernamePasswordAuthenticationFilter` extracts them and creates an `UsernamePasswordAuthenticationToken`, which is passed to `AuthenticationManager`. The manager delegates to `DaoAuthenticationProvider`, which loads the user from `UserDetailsService`, verifies the password with `PasswordEncoder`, and returns a fully authenticated token with the user's `GrantedAuthority` collection.

`UserDetails` is the interface that represents a loaded user. Spring Security ships `User` as the default implementation. Applications typically provide a custom `UserDetails` implementation that exposes application-specific fields (user ID, email, roles, etc.) and a `UserDetailsService` (or `UserDetailsPasswordService`) that loads the user from the application's persistence layer.

Password encoding is not optional. Storing plain-text passwords in any production system is indefensible. Spring Security's `PasswordEncoder` interface abstracts the hashing algorithm, and `DelegatingPasswordEncoder` supports multiple algorithms simultaneously by prefixing encoded values with an algorithm identifier (`{bcrypt}`, `{argon2}`, `{scrypt}`). This enables algorithm migration: old passwords remain valid under their original algorithm while new passwords use the current recommended one.

## Key Concepts

### UsernamePasswordAuthenticationToken Flow

```
POST /login  with username + password
        │
        ▼
UsernamePasswordAuthenticationFilter.attemptAuthentication()
        │
        ├── Creates UsernamePasswordAuthenticationToken(username, password) — not yet authenticated
        │
        ▼
AuthenticationManager.authenticate(token)
        │
        ▼
DaoAuthenticationProvider.authenticate(token)
        │
        ├── UserDetailsService.loadUserByUsername(username)
        │     → Returns UserDetails (or throws UsernameNotFoundException)
        │
        ├── PasswordEncoder.matches(rawPassword, encodedPassword)
        │     → Returns boolean
        │
        ├── Checks UserDetails.isEnabled(), isAccountNonLocked(), etc.
        │
        └── Returns UsernamePasswordAuthenticationToken(userDetails, null, authorities)
                                                         — authenticated=true

        │
        ▼
AuthenticationSuccessHandler.onAuthenticationSuccess()
  (stores in SecurityContext, redirects or returns response)
```

### UserDetailsService

```java
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .map(user -> User.builder()
                        .username(user.getUsername())
                        .password(user.getPasswordHash())   // already encoded
                        .roles(user.getRole().name())        // ROLE_ prefix added automatically
                        .accountLocked(user.isLocked())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
```

### PasswordEncoder

```java
@Bean
public PasswordEncoder passwordEncoder() {
    // DelegatingPasswordEncoder uses BCrypt by default but can verify
    // passwords encoded with any registered algorithm — supports migration.
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
}

// Encoding a new password:
String encoded = passwordEncoder.encode(rawPassword);
// Result: "{bcrypt}$2a$10$..."

// Verifying: DelegatingPasswordEncoder reads the prefix to select the decoder
boolean matches = passwordEncoder.matches(rawPassword, encoded);
```

**Algorithm comparison:**

| Algorithm | Strength | Notes |
|---|---|---|
| BCrypt | High | Default; well-tested; fixed 72-char input limit |
| Argon2 | Very High | Memory-hard; recommended for new systems; requires Bouncy Castle |
| SCrypt | Very High | Memory-hard; requires Bouncy Castle |
| PBKDF2 | Medium | FIPS-compliant; slower than Argon2 |
| Plain/MD5/SHA | Insecure | Legacy only; never use for new systems |

### Custom AuthenticationProvider

```java
@Component
public class ApiKeyAuthenticationProvider implements AuthenticationProvider {

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyAuthenticationProvider(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String key = (String) authentication.getCredentials();

        return apiKeyRepository.findByKey(key)
                .map(apiKey -> new ApiKeyAuthenticationToken(
                        apiKey.getOwner(),
                        key,
                        apiKey.getAuthorities()))
                .orElseThrow(() -> new BadCredentialsException("Invalid API key"));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        // Only handle ApiKeyAuthenticationToken — not username/password tokens
        return ApiKeyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
```

### AuthenticationSuccessHandler and AuthenticationFailureHandler

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http,
        AuthenticationSuccessHandler successHandler,
        AuthenticationFailureHandler failureHandler) throws Exception {
    return http
        .formLogin(form -> form
            .loginPage("/login")
            .successHandler(successHandler)
            .failureHandler(failureHandler))
        .build();
}

// Custom success handler — returns JSON instead of redirecting
@Component
public class JsonAuthSuccessHandler implements AuthenticationSuccessHandler {
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                         HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        response.setContentType("application/json");
        response.getWriter().write("{\"status\": \"ok\", \"user\": \"" +
                authentication.getName() + "\"}");
    }
}
```

### In-Memory Users for Development

```java
@Bean
public UserDetailsService inMemoryUsers() {
    // Only for development and tests — never use in-memory users in production
    UserDetails admin = User.builder()
            .username("admin")
            .password("{bcrypt}" + new BCryptPasswordEncoder().encode("admin"))
            .roles("ADMIN", "USER")
            .build();

    UserDetails user = User.builder()
            .username("user")
            .password("{noop}password")  // {noop} = no encoding (dev only)
            .roles("USER")
            .build();

    return new InMemoryUserDetailsManager(admin, user);
}
```

## Gotchas

`UsernameNotFoundException` is internally converted to `BadCredentialsException` by `DaoAuthenticationProvider` by default (`hideUserNotFoundExceptions = true`). This prevents username enumeration attacks where an attacker could distinguish "wrong password" from "user not found." Do not override this behavior without understanding the security implications.

`PasswordEncoder.matches()` is designed to be timing-safe — it completes in a constant time regardless of whether the password matches or not, preventing timing attacks. Do not implement a `PasswordEncoder` that returns early on mismatch.

`UserDetails.isEnabled()`, `isAccountNonExpired()`, `isAccountNonLocked()`, and `isCredentialsNonExpired()` are checked by `DaoAuthenticationProvider` after password verification. If any return `false`, the corresponding `AuthenticationException` subtype is thrown (`DisabledException`, `AccountExpiredException`, etc.). These exceptions are handled separately by `AuthenticationFailureHandler`, allowing different responses for "wrong password" vs "account locked."

When implementing a custom `UserDetails` that adds application-specific fields, do not store mutable state in it. `UserDetails` objects are cached by `UserDetailsService` implementations and `SessionManagementFilter`. A mutable custom `UserDetails` that reflects database state will become stale in the session cache. Store the user's database ID in the `UserDetails` and re-load fresh data from the database when needed using the ID.

`AuthenticationManager` is a bean but `HttpSecurity.getSharedObject(AuthenticationManagerBuilder.class)` provides a builder-based way to configure it. In Spring Security 5.7+, the recommended approach is to declare an `AuthenticationManager` bean directly rather than using the builder, for clarity and testability. The builder approach remains supported but is more opaque.
