/*
 * Entry point for CustomAnnotationAspectDemo.
 *
 * Demonstrates using a custom annotation as an @Around pointcut target.
 * The @Timed annotation records method execution duration.
 * The @RateLimit annotation enforces a maximum calls-per-second limit.
 *
 * Key concepts:
 *   - Defining a custom annotation with @Target and @Retention
 *   - Using @annotation(annotationParam) to bind the annotation instance in advice
 *   - Reading annotation attributes inside @Around advice
 *   - Composing two annotation-driven aspects on the same method
 */
package com.example.customannotation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomAnnotationAspectDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomAnnotationAspectDemoApplication.class, args);
    }
}
