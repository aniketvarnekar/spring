/*
 * Integration test using @SpringBootTest with a full ApplicationContext.
 *
 * @SpringBootTest with no webEnvironment argument defaults to MOCK mode:
 * the full context is loaded but no real server starts. MockMvc must be
 * auto-configured with @AutoConfigureMockMvc to be available for injection.
 *
 * This test uses the real GreetingService (not a mock) and overrides the
 * greeting.prefix property via @TestPropertySource to verify that property
 * binding affects service behavior.
 */
package com.example.springboottest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
// @TestPropertySource overrides specific properties for this test class only.
// The property takes higher precedence than application.yaml.
@TestPropertySource(properties = "greeting.prefix=Howdy")
class GreetingServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void greet_usesOverriddenPrefix() throws Exception {
        // The real GreetingService reads the "Howdy" prefix from @TestPropertySource.
        mockMvc.perform(get("/greet/Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Howdy, Alice!"));
    }
}
