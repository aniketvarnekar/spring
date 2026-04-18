/*
 * Service demonstrating method-level security with @PreAuthorize.
 *
 * @EnableMethodSecurity in SecurityConfig activates AOP proxying of this bean.
 * The @PreAuthorize expression is evaluated before the method body runs.
 * If the caller does not hold ROLE_ADMIN, AccessDeniedException is thrown.
 */
package com.example.securityconfig.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

    @PreAuthorize("hasRole('ADMIN')")
    public String performAdminAction() {
        return "Admin action executed — @PreAuthorize ROLE_ADMIN check passed.";
    }
}
