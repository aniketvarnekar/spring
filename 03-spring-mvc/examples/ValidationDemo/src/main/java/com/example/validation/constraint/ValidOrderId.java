/*
 * Custom constraint annotation that validates order ID format.
 *
 * A custom constraint is the right tool when the validation rule cannot be expressed
 * with standard annotations (@Pattern, @Size, etc.) or when the validation requires
 * injected dependencies (database lookup, external service check).
 */
package com.example.validation.constraint;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidOrderIdValidator.class)
public @interface ValidOrderId {
    String message() default "Order ID must match format ORD-{digits}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
