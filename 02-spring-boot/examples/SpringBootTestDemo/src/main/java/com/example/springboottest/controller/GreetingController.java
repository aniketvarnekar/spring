/*
 * REST controller that delegates to GreetingService.
 * Tested in isolation with @WebMvcTest and with @SpringBootTest.
 */
package com.example.springboottest.controller;

import com.example.springboottest.service.GreetingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/greet")
public class GreetingController {

    private final GreetingService greetingService;

    public GreetingController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping("/{name}")
    public ResponseEntity<String> greet(@PathVariable String name) {
        return ResponseEntity.ok(greetingService.greet(name));
    }
}
