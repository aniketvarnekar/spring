/*
 * Publicly accessible endpoints — no authentication required.
 * Matches the /public/** pattern configured as permitAll in SecurityConfig.
 */
package com.example.securityconfig.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public")
public class PublicController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello, anonymous visitor!";
    }
}
