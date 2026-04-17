/*
 * Simple service used as the subject under test in the test demonstrations.
 */
package com.example.springboottest.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    private final String prefix;

    public GreetingService(@Value("${greeting.prefix:Hello}") String prefix) {
        this.prefix = prefix;
    }

    public String greet(String name) {
        return prefix + ", " + name + "!";
    }
}
