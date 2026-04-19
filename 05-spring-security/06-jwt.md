# JWT Authentication

## Overview

JSON Web Tokens (JWT) enable stateless authentication: the server issues a signed token containing claims about the authenticated principal, and the client sends it with each request. The server validates the signature and reads claims directly from the token without consulting a session store. This model suits horizontal scaling because any server instance can verify the token independently.

A JWT consists of three Base64URL-encoded segments separated by dots: header (algorithm and token type), payload (claims), and signature. The server holds a signing key — a symmetric secret for HMAC algorithms (HS256, HS512) or an asymmetric key pair for RSA/ECDSA (RS256, ES256). The signature covers the header and payload, so any modification invalidates it.

Spring Security does not issue JWTs by default. The typical implementation is:

1. A login endpoint accepts credentials, authenticates via `AuthenticationManager`, and returns a signed JWT.
2. A custom `OncePerRequestFilter` intercepts subsequent requests, extracts the token from the `Authorization: Bearer <token>` header, validates it, and populates `SecurityContextHolder` with a pre-authenticated token.
3. `HttpSecurity` is configured as stateless (no session creation) and the JWT filter is inserted before `UsernamePasswordAuthenticationFilter`.

The JJWT library (io.jsonwebtoken) is the most common third-party library for parsing and creating JWTs in Java. Spring Security's own `spring-security-oauth2-resource-server` module provides built-in JWT support for resource servers consuming externally-issued tokens, but for self-issued tokens a custom filter is the clearest approach.

## Key Concepts

### Token Structure and Claims

```text
header.payload.signature

Header:  {"alg":"HS256","typ":"JWT"}
Payload: {"sub":"user@example.com","roles":["ROLE_USER"],"iat":1700000000,"exp":1700003600}
```

Standard registered claims:
- `sub` — subject (the principal identifier)
- `iat` — issued-at timestamp
- `exp` — expiration timestamp
- `jti` — JWT ID (unique identifier, useful for blacklisting)
- `iss` — issuer
- `aud` — audience

### JWT Service

```java
// Wraps JJWT operations: issue tokens and parse/validate them.
// Uses a symmetric HMAC-SHA256 key stored as a Base64-encoded secret.
@Component
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${security.jwt.secret}") String base64Secret,
            @Value("${security.jwt.expiration-ms:3600000}") long expirationMs) {
        byte[] keyBytes = Decoders.BASE64.decode(base64Secret);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    public String issue(Authentication authentication) {
        Instant now = Instant.now();
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Jwts.builder()
                .subject(authentication.getName())
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey)
                .compact();
    }

    // Returns Claims if the token is valid; throws JwtException subtypes if not.
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

### Login Endpoint

```java
// Stateless API login: authenticates credentials and returns a JWT.
// No session is created.
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        String token = jwtService.issue(authentication);
        return ResponseEntity.ok(new TokenResponse(token));
    }
}

public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) {}

public record TokenResponse(String token) {}
```

### JWT Authentication Filter

```java
// Reads the Bearer token from the Authorization header, validates it, and
// populates the SecurityContext so downstream filters and controllers see an
// authenticated principal. Extends OncePerRequestFilter to guarantee single execution.
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            Claims claims = jwtService.parse(token);
            String username = claims.getSubject();

            // Only set authentication if the context is not already populated.
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Verify account status (expired, locked, disabled) before accepting the token.
                if (userDetails.isEnabled() && userDetails.isAccountNonLocked()) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (JwtException ex) {
            // Invalid or expired token: clear context and continue.
            // The AuthorizationFilter will deny access to protected resources.
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid or expired token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip the filter for the login endpoint to avoid unnecessary token lookup.
        return request.getServletPath().equals("/auth/login");
    }
}
```

### Security Configuration for Stateless JWT

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // stateless API: no session, no CSRF needed
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }
}
```

### Token Refresh Strategy

Token refresh can be implemented in two ways:

**Sliding refresh (short-lived access token + long-lived refresh token):**

```java
// Issues a short-lived access token (15 minutes) and a long-lived refresh token (7 days).
// The client stores the refresh token securely (HttpOnly cookie, not localStorage).
public TokenPair issueTokenPair(Authentication authentication) {
    String accessToken = issueAccess(authentication);   // 15 minutes
    String refreshToken = issueRefresh(authentication); // 7 days, stored in DB or Redis
    return new TokenPair(accessToken, refreshToken);
}

// Refresh endpoint: validates the refresh token, issues a new access token.
@PostMapping("/auth/refresh")
public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshRequest request) {
    Claims claims = jwtService.parseRefresh(request.refreshToken());
    // Look up the refresh token in the store to verify it has not been revoked.
    refreshTokenStore.verify(request.refreshToken());
    UserDetails userDetails = userDetailsService.loadUserByUsername(claims.getSubject());
    Authentication auth = new UsernamePasswordAuthenticationToken(
            userDetails, null, userDetails.getAuthorities());
    return ResponseEntity.ok(new TokenResponse(jwtService.issueAccess(auth)));
}
```

### Token Blacklisting Trade-offs

JWTs are self-contained and cannot be invalidated server-side without additional infrastructure. The options are:

| Approach | Mechanism | Trade-off |
|---|---|---|
| Short expiry | Access tokens expire in minutes; rely on refresh token revocation | Simplest; tokens remain valid until expiry |
| Blocklist (Redis) | Store revoked `jti` claims until their expiry time | Requires shared state (Redis); adds a lookup per request |
| Token versioning | Store a `tokenVersion` per user in the database; embed version in token; reject mismatches | Requires a DB read per request; effectively as stateful as sessions |
| Refresh token rotation | Issue a new refresh token on each use; revoke the old one | Limits the blast radius of refresh token theft |

For most applications, short-lived access tokens (15 minutes) combined with refresh token rotation and a blocklist for explicit logout provides a good balance of security and simplicity.

## Gotchas

**Algorithm confusion attacks.** JJWT 0.12+ requires an explicit algorithm on the parser (`.verifyWith(key)` implies HMAC); do not accept the algorithm from the token header. Never accept `none` as an algorithm.

**Secret key length.** HMAC-SHA256 requires at least 256 bits (32 bytes). A short secret is insecure and JJWT will throw a `WeakKeyException`. Generate secrets with `openssl rand -base64 32` or `Keys.secretKeyFor(SignatureAlgorithm.HS256)`.

**Clock skew.** Different servers may have slightly different clocks. Add a small leeway (e.g., 30 seconds) when validating `exp` to prevent spurious rejections. JJWT's `clockSkewSeconds` parser option handles this.

**Token content.** JWTs are Base64-encoded, not encrypted. Do not store sensitive data (passwords, PII beyond the username) in token claims unless using JWE (JSON Web Encryption).

**UserDetails re-load cost.** The filter example re-loads `UserDetails` on every request to verify account status (locked, disabled). For high-throughput APIs, embed enough claims in the JWT (roles, account status) to skip the database lookup and rely solely on the token until expiry.

**SecurityContextHolder with virtual threads.** The default `ThreadLocal` strategy does not propagate across virtual thread boundaries. If using virtual threads, switch to `MODE_INHERITABLETHREADLOCAL` or a `DelegatingSecurityContextExecutor`.
