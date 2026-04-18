/*
 * Integration tests for the JWT authentication flow.
 *
 * Tests:
 *   - Successful login returns a non-empty token.
 *   - Protected endpoint returns 401 without a token.
 *   - Protected endpoint returns 200 with a valid token obtained from login.
 *   - Protected endpoint returns 401 with a malformed token.
 */
package com.example.jwtauth;

import com.example.jwtauth.model.LoginRequest;
import com.example.jwtauth.model.TokenResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JwtAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void login_withValidCredentials_returnsToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("alice", "password123"))))
                .andExpect(status().isOk())
                .andReturn();

        TokenResponse tokenResponse = objectMapper.readValue(
                result.getResponse().getContentAsString(), TokenResponse.class);
        assertThat(tokenResponse.token()).isNotBlank();
    }

    @Test
    void login_withInvalidCredentials_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("alice", "wrong"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withValidToken_returnsOk() throws Exception {
        String token = obtainToken("alice", "password123");

        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer " + token))
               .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_withMalformedToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me")
                        .header("Authorization", "Bearer this.is.not.a.valid.token"))
               .andExpect(status().isUnauthorized());
    }

    private String obtainToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), TokenResponse.class).token();
    }
}
