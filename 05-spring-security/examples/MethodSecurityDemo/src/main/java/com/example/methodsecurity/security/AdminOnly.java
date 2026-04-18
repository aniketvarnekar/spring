/*
 * Meta-annotation that encapsulates a @PreAuthorize("hasRole('ADMIN')") check.
 *
 * Apply @AdminOnly to any method or type that should be restricted to admins.
 * This avoids repeating the same SpEL string and makes intent explicit at the call site.
 */
package com.example.methodsecurity.security;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('ADMIN')")
public @interface AdminOnly {
}
