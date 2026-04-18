# OAuth2 and OpenID Connect

## Overview

OAuth2 is an authorization framework that allows a client application to obtain limited access to a resource server on behalf of a user. OpenID Connect (OIDC) is a thin identity layer on top of OAuth2 that adds authentication: it introduces the ID token (a JWT) and the `userinfo` endpoint, enabling clients to verify who the user is in addition to what they can access.

Spring Security provides first-class support for two OAuth2 roles:

- **Resource server** — an API that accepts OAuth2 access tokens (JWTs or opaque tokens) issued by a trusted authorization server. This is the most common case for backend APIs.
- **OAuth2 Login (client)** — a web application that delegates authentication to an external provider (Google, GitHub, Okta). Users log in via the provider's consent screen and Spring Security receives an authorization code, exchanges it for tokens, and creates an authenticated session.

Spring Security does not implement an authorization server. The Spring Authorization Server project (`spring-authorization-server`) is a separate project for that purpose.

## Key Concepts

### Resource Server with JWT

A resource server validates incoming JWTs against an authorization server's JWKS (JSON Web Key Set) endpoint. Spring Security fetches public keys from the JWKS URI and verifies the token signature and claims automatically.

```xml
<!-- pom.xml dependency -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

```yaml
# application.yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          # Spring Security fetches keys from this URI to verify JWT signatures.
          # Replace with the actual issuer-uri if the provider supports OIDC discovery.
          jwk-set-uri: https://auth.example.com/.well-known/jwks.json
          # Alternatively, use issuer-uri for auto-discovery (requires OIDC-compatible server):
          # issuer-uri: https://auth.example.com
```

```java
@Configuration
@EnableWebSecurity
public class ResourceServerConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .anyRequest().authenticated())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
}
```

After this configuration, `SecurityContextHolder` contains a `JwtAuthenticationToken` whose principal is a `Jwt` object. Controllers access it via `@AuthenticationPrincipal Jwt jwt`.

```java
@RestController
@RequestMapping("/api")
public class ProfileController {

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        return Map.of(
            "subject",  jwt.getSubject(),
            "email",    jwt.getClaimAsString("email"),
            "roles",    jwt.getClaimAsStringList("roles")
        );
    }
}
```

### Custom JWT Claims Mapping

By default, Spring Security maps JWT claims to `GrantedAuthority` values using the `scope` claim with a `SCOPE_` prefix. To use custom claims (e.g., `roles`), provide a `JwtAuthenticationConverter`:

```java
@Bean
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
    converter.setAuthoritiesClaimName("roles");   // use "roles" claim instead of "scope"
    converter.setAuthorityPrefix("ROLE_");         // prefix maps roles to Spring Security's convention

    JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
    jwtConverter.setJwtGrantedAuthoritiesConverter(converter);
    return jwtConverter;
}

// Wire it into the resource server configuration:
http.oauth2ResourceServer(oauth2 -> oauth2
    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
```

### Opaque Token Introspection

Some authorization servers issue opaque (non-JWT) tokens. Validation requires a call to the introspection endpoint on each request.

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        opaquetoken:
          introspection-uri: https://auth.example.com/oauth2/introspect
          client-id: my-resource-server
          client-secret: secret
```

```java
http.oauth2ResourceServer(oauth2 -> oauth2
    .opaqueToken(Customizer.withDefaults()));
```

The authenticated principal is an `OAuth2AuthenticatedPrincipal`. Opaque tokens require a network call per request; JWTs only require a key fetch (cached). Prefer JWTs for performance-sensitive APIs.

### OAuth2 Login (Client Role)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid, profile, email
          github:
            client-id: ${GITHUB_CLIENT_ID}
            client-secret: ${GITHUB_CLIENT_SECRET}
            scope: read:user, user:email
        provider:
          github:
            user-info-uri: https://api.github.com/user
            user-name-attribute: login
```

```java
@Configuration
@EnableWebSecurity
public class OAuth2LoginConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login").permitAll()
                .anyRequest().authenticated())
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")                      // custom login page listing providers
                .defaultSuccessUrl("/dashboard", true)
                .userInfoEndpoint(userInfo -> userInfo
                    .userService(customOAuth2UserService())) // custom attribute extraction
            );
        return http.build();
    }
}
```

After a successful login the principal is an `OAuth2User` (or `OidcUser` for OIDC providers). Access it via `@AuthenticationPrincipal OAuth2User user`.

### Custom OAuth2UserService

```java
// Loads and transforms OAuth2 user attributes after the provider's userinfo call.
// Useful for mapping provider-specific attributes to application roles or creating
// a local user record on first login.
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(request);

        String email = oAuth2User.getAttribute("email");
        String provider = request.getClientRegistration().getRegistrationId();

        // Provision local account on first login.
        userRepository.findByEmail(email).orElseGet(() ->
            userRepository.save(new AppUser(email, provider)));

        Set<GrantedAuthority> authorities = new HashSet<>(oAuth2User.getAuthorities());
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        return new DefaultOAuth2User(authorities, oAuth2User.getAttributes(), "email");
    }
}
```

### JWT vs Opaque Token Comparison

| Aspect | JWT (self-contained) | Opaque token |
|---|---|---|
| Validation | Local: verify signature with public key | Remote: introspection endpoint call |
| Revocation | Not possible without blocklist | Immediate: authorization server controls validity |
| Payload | Claims embedded, readable by resource server | Opaque to resource server |
| Performance | Fast (no network call after key fetch) | Network call per request |
| Key rotation | Fetch new JWKS; brief window of old-key acceptance | Transparent (introspection always hits AS) |

## Gotchas

**JWKS caching and key rotation.** Spring Security caches JWKS keys. During key rotation, there is a brief period where new tokens signed with the new key fail until the cache refreshes. Configure `NimbusJwtDecoder` with `jwkSetUri` and appropriate cache settings, or use `issuer-uri` with OIDC discovery to pick up rotation hints from `Cache-Control` headers.

**Audience validation.** Always validate the `aud` claim on resource servers to prevent token confusion attacks where a token issued for service A is replayed against service B.

```java
@Bean
public JwtDecoder jwtDecoder() {
    NimbusJwtDecoder decoder = NimbusJwtDecoder
            .withJwkSetUri("https://auth.example.com/.well-known/jwks.json")
            .build();

    OAuth2TokenValidator<Jwt> audienceValidator =
            new JwtClaimValidator<List<String>>("aud", aud -> aud.contains("my-api"));
    OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer("https://auth.example.com");
    OAuth2TokenValidator<Jwt> combined = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);
    decoder.setJwtValidator(combined);
    return decoder;
}
```

**State parameter.** The OAuth2 login flow uses a `state` parameter to prevent CSRF. Spring Security handles this automatically with `HttpSessionOAuth2AuthorizationRequestRepository`. Do not disable CSRF for the OAuth2 login flow.

**Scope vs role.** OAuth2 scopes (`read`, `write`) and application roles (`ROLE_ADMIN`) serve different purposes. Map scopes to authorities carefully; do not use scopes as a substitute for fine-grained authorization.

**Token leakage in logs.** Access tokens appear in HTTP headers. Ensure that request logging (e.g., `CommonsRequestLoggingFilter`) does not log the `Authorization` header in production.
