/*
 * Marks a method for execution time measurement.
 * The aspect logs duration at DEBUG level with the provided operation name.
 */
package com.example.customannotation.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Timed {

    /**
     * Logical name for the operation, used in log output and metrics tags.
     * Defaults to an empty string, which causes the aspect to use the method name.
     */
    String value() default "";
}
