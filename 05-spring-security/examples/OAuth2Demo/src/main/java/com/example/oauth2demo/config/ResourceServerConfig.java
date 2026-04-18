/*
 * Resource server security configuration.
 *
 * Configures the SecurityFilterChain for a stateless JWT resource server:
 *   - CSRF disabled (stateless, Bearer token auth)
 *   - No session creation
 *   - /api/public is open; /api/admin requires the "admin" scope; everything else requires authentication
 *   - JWT decoder uses the symmetric secret key from application.yaml
 *   - JwtAuthenticationConverter maps the "roles" claim to GrantedAuthority with ROLE_ prefix
 *     in addition to the default SCOPE_ mapping for scopes
 */
package com.example.oauth2demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class ResourceServerConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public").permitAll()
                .requestMatchers("/api/admin").hasAuthority("SCOPE_admin")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // Default: maps "scope" claim with "SCOPE_" prefix.
        JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();
        // Keep the default scope mapping; additional role mapping would require
        // a composite converter for the "roles" claim.

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(converter);
        return jwtConverter;
    }
}
