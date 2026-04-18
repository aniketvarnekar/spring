/*
 * Marks a method as rate-limited to a maximum number of calls per second.
 * The aspect uses a per-method token bucket (simplified) to enforce the limit.
 */
package com.example.customannotation.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Maximum allowed calls per second. */
    int callsPerSecond() default 10;
}
