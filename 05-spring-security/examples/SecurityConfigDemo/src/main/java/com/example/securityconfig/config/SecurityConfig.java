/*
 * Spring Security configuration.
 *
 * Configures:
 *   - A SecurityFilterChain with form login and role-based URL authorization
 *   - An InMemoryUserDetailsManager with two users (one USER, one ADMIN)
 *   - BCryptPasswordEncoder as the PasswordEncoder bean
 *   - @EnableMethodSecurity to activate @PreAuthorize processing
 */
package com.example.securityconfig.config;

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
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/user/**").hasAnyRole("USER", "ADMIN")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/user/profile", true)
                .permitAll())
            .logout(logout -> logout
                .logoutSuccessUrl("/public/hello")
                .permitAll());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        // In production, replace InMemoryUserDetailsManager with a UserDetailsService
        // that loads users from a database (JPA, JDBC, etc.).
        var regularUser = User.builder()
                .username("alice")
                .password(passwordEncoder.encode("password123"))
                .roles("USER")
                .build();

        var adminUser = User.builder()
                .username("bob")
                .password(passwordEncoder.encode("admin456"))
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(regularUser, adminUser);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
