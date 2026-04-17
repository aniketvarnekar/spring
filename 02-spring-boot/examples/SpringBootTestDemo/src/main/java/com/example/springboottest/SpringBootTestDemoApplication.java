/*
 * Entry point for SpringBootTestDemo.
 *
 * The application itself is minimal — a single REST controller and a service.
 * The interesting content is in the test directory, which demonstrates:
 *
 *   - @SpringBootTest (full context, MockMvc in MOCK mode)
 *   - @WebMvcTest (web layer only, service mocked with @MockBean)
 *   - @MockBean vs @SpyBean distinction
 *   - @TestPropertySource for per-test property overrides
 */
package com.example.springboottest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringBootTestDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBootTestDemoApplication.class, args);
    }
}
