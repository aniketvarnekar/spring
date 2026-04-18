/*
 * Minimal controller whose requests are processed by both the filter and the interceptor.
 * Make requests to /api/hello and /api/fail to see the different lifecycle paths.
 */
package com.example.filterinterceptor.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class DemoController {

    @GetMapping("/hello")
    public Map<String, String> hello() {
        return Map.of("message", "Hello from the controller");
    }

    @GetMapping("/fail")
    public Map<String, String> fail() {
        // Throws an exception to show that postHandle is skipped but afterCompletion runs.
        throw new RuntimeException("Simulated failure");
    }
}
