/*
 * Admin endpoints.
 *
 * URL-level authorization (/admin/**) restricts access to ROLE_ADMIN, enforced in SecurityConfig.
 * The action endpoint additionally uses @PreAuthorize for method-level demonstration.
 * Both layers must pass; the URL check runs first (in the filter chain), the method
 * check runs when the controller method is invoked via AOP proxy.
 */
package com.example.securityconfig.controller;

import com.example.securityconfig.service.AdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    public Map<String, String> dashboard() {
        return Map.of("message", "Admin dashboard — URL-level ROLE_ADMIN check passed.");
    }

    @PostMapping("/action")
    public Map<String, String> action() {
        // Delegates to AdminService where @PreAuthorize is enforced.
        return Map.of("result", adminService.performAdminAction());
    }
}
