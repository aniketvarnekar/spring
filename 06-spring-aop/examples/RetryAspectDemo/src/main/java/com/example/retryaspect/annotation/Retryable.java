/*
 * Marks a method as eligible for automatic retry on failure.
 *
 * Attributes:
 *   maxAttempts — total number of attempts (including the first); defaults to 3
 *   backoffMs   — initial delay between attempts in milliseconds; linear back-off
 *                 applied as backoffMs * attemptNumber
 *   on          — exception types that trigger a retry; other exceptions propagate immediately
 */
package com.example.retryaspect.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Retryable {

    int maxAttempts() default 3;

    long backoffMs() default 100;

    Class<? extends Exception>[] on() default {Exception.class};
}
