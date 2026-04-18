/*
 * Integration tests for the OAuth2 resource server.
 *
 * Uses SecurityMockMvcRequestPostProcessors.jwt() from spring-security-test to inject
 * mock JWT principals without issuing real tokens or starting an authorization server.
 * This approach tests authorization decisions independently of token parsing.
 */
package com.example.oauth2demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ResourceServerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicEndpoint_isAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/api/public"))
               .andExpect(status().isOk());
    }

    @Test
    void meEndpoint_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void meEndpoint_withValidJwt_returnsSubject() throws Exception {
        mockMvc.perform(get("/api/me")
                        .with(jwt().jwt(builder -> builder.subject("user@example.com"))))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.subject").value("user@example.com"));
    }

    @Test
    void adminEndpoint_withoutAdminScope_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin")
                        .with(jwt().jwt(builder -> builder.subject("user@example.com"))))
               .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoint_withAdminScope_returnsOk() throws Exception {
        mockMvc.perform(get("/api/admin")
                        .with(jwt()
                            .jwt(builder -> builder.subject("admin@example.com"))
                            .authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.message").value("Admin-only endpoint. Subject: admin@example.com"));
    }
}
