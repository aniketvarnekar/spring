/*
 * Integration tests for the security configuration.
 *
 * Uses @WithMockUser to simulate authenticated requests without going through
 * the actual authentication flow. Tests both URL-level and method-level
 * security decisions.
 */
package com.example.securityconfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicEndpoint_isAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/public/hello"))
               .andExpect(status().isOk());
    }

    @Test
    void userEndpoint_redirectsToLoginWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/user/profile"))
               .andExpect(status().is3xxRedirection()); // redirects to /login
    }

    @Test
    @WithMockUser(roles = "USER")
    void userEndpoint_isAccessibleByRoleUser() throws Exception {
        mockMvc.perform(get("/user/profile"))
               .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminEndpoint_isForbiddenForRoleUser() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
               .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminEndpoint_isAccessibleByRoleAdmin() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
               .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminAction_methodSecurityPassesForAdmin() throws Exception {
        // csrf() post-processor adds the required CSRF token (CSRF is enabled in this app).
        mockMvc.perform(post("/admin/action").with(csrf()))
               .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminAction_methodSecurityDeniesRoleUser() throws Exception {
        mockMvc.perform(post("/admin/action").with(csrf()))
               .andExpect(status().isForbidden());
    }
}
