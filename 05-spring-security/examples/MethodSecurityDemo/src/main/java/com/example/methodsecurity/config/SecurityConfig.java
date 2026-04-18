/*
 * Security configuration.
 *
 * HTTP security: all requests require authentication (form login for browser testing).
 * Method security: @EnableMethodSecurity activates @PreAuthorize, @PostFilter, and meta-annotations.
 */
package com.example.methodsecurity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .formLogin(form -> form.defaultSuccessUrl("/documents", true).permitAll())
            .csrf(csrf -> csrf.disable()); // disabled for simplified testing with MockMvc
        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder encoder) {
        var alice = User.builder().username("alice").password(encoder.encode("pass")).roles("USER").build();
        var bob   = User.builder().username("bob").password(encoder.encode("pass")).roles("USER").build();
        var carol = User.builder().username("carol").password(encoder.encode("pass")).roles("ADMIN").build();
        return new InMemoryUserDetailsManager(alice, bob, carol);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
