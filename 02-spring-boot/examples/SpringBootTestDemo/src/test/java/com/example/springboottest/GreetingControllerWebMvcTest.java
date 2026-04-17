/*
 * Slice test using @WebMvcTest — loads only the web layer.
 *
 * @WebMvcTest(GreetingController.class) creates a minimal context containing:
 *   - GreetingController and its MVC infrastructure (@ControllerAdvice, filters, etc.)
 *   - MockMvc (auto-configured)
 *   - Jackson ObjectMapper
 *
 * GreetingService is NOT in the context because it is a @Service, not part of the
 * web layer. @MockBean replaces it with a Mockito mock registered in the context.
 *
 * This test is faster than @SpringBootTest because it does not initialize the full
 * application context — only the Spring MVC infrastructure.
 */
package com.example.springboottest;

import com.example.springboottest.controller.GreetingController;
import com.example.springboottest.service.GreetingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GreetingController.class)
class GreetingControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    // @MockBean registers a Mockito mock in the ApplicationContext.
    // Every test class that uses @MockBean with a different set of mocked beans
    // requires a new context — @MockBean breaks context caching.
    @MockBean
    private GreetingService greetingService;

    @Test
    void greet_returnsOkWithMessage() throws Exception {
        given(greetingService.greet("Alice")).willReturn("Hello, Alice!");

        mockMvc.perform(get("/greet/Alice"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, Alice!"));
    }

    @Test
    void greet_propagatesServiceResponse() throws Exception {
        given(greetingService.greet("Bob")).willReturn("Bonjour, Bob!");

        mockMvc.perform(get("/greet/Bob"))
                .andExpect(status().isOk())
                .andExpect(content().string("Bonjour, Bob!"));
    }
}
