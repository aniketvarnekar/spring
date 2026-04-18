/*
 * Validator implementation for @ValidOrderId.
 *
 * ConstraintValidator<AnnotationType, ValueType> — the two type parameters
 * bind this validator to the @ValidOrderId annotation and to String values.
 *
 * The validator is a Spring @Component so it can receive @Autowired dependencies
 * (e.g., a repository to check existence). Spring Boot's LocalValidatorFactoryBean
 * integrates with the Spring container for validator instantiation.
 */
package com.example.validation.constraint;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class ValidOrderIdValidator implements ConstraintValidator<ValidOrderId, String> {

    // Order IDs must follow the format: ORD- followed by 1–10 digits
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("^ORD-\\d{1,10}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Null values are considered valid here — @NotNull or @NotBlank handles nullability separately.
        if (value == null) {
            return true;
        }
        return ORDER_ID_PATTERN.matcher(value).matches();
    }
}
