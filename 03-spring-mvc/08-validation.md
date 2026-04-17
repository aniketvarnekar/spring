# Validation

## Overview

Spring integrates with the Bean Validation specification (JSR-380, implemented by Hibernate Validator) to validate method arguments and return values. Placing `@Valid` or `@Validated` on a method parameter tells the framework to validate the argument against the constraint annotations declared on its type. If validation fails, Spring throws `MethodArgumentNotValidException` (for `@RequestBody`) or `ConstraintViolationException` (for `@RequestParam`, `@PathVariable`, or service-layer method parameters), both of which can be handled globally in `@ControllerAdvice`.

`@Valid` and `@Validated` have different scopes. `@Valid` (from Jakarta Bean Validation) enables recursive validation using the standard Bean Validation semantics. `@Validated` (from Spring) additionally supports validation groups, which allow different constraint sets to be applied depending on context (create vs update operations, for example). Either annotation triggers validation when placed on a `@RequestBody`, `@RequestParam`, `@PathVariable`, or `@ModelAttribute` parameter.

Custom validation logic that cannot be expressed with standard constraint annotations is implemented as a `ConstraintValidator`. A `ConstraintValidator` receives the value to be validated and a `ConstraintValidatorContext` through which it can report constraint violations with custom messages. The validator is registered automatically by the Bean Validation provider when the corresponding constraint annotation is present.

Method-level validation on service beans (not just controllers) is enabled by `@EnableMethodValidation` (Spring 6.x) or the `MethodValidationPostProcessor` bean. This allows constraint annotations on service method parameters and return values to be enforced by a Spring AOP proxy, extending the validation boundary beyond the web layer. The same caveats about AOP proxying apply: self-invocation bypasses the proxy and the validation.

## Key Concepts

### @Valid on @RequestBody

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @PostMapping
    public ResponseEntity<UserResponse> create(@RequestBody @Valid UserRequest request) {
        // Validation runs before the method body.
        // If request fails validation, MethodArgumentNotValidException is thrown.
        return ResponseEntity.ok(userService.create(request));
    }
}

public class UserRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between {min} and {max} characters")
    private String username;

    @NotBlank
    @Email(message = "Invalid email address")
    private String email;

    @NotNull
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @Valid  // recurse into nested object
    @NotNull
    private AddressRequest address;
}
```

### @Validated for Path Variables and Request Parameters

`@Valid` does not work directly on `@PathVariable` and `@RequestParam` — these are simple types, not complex objects. Use `@Validated` at the class level (which enables method-level validation via AOP) and apply constraints directly to the parameters:

```java
@RestController
@RequestMapping("/api/products")
@Validated  // enables method-level validation for @PathVariable and @RequestParam
public class ProductController {

    @GetMapping("/{id}")
    public Product get(@PathVariable @Min(1) Long id) {
        // If id < 1, ConstraintViolationException is thrown (not MethodArgumentNotValidException)
        return productService.find(id);
    }

    @GetMapping
    public List<Product> list(
            @RequestParam @Min(0) int page,
            @RequestParam @Max(100) int size) {
        return productService.list(page, size);
    }
}
```

### Custom ConstraintValidator

```java
// 1. Define the constraint annotation
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueUsernameValidator.class)
public @interface UniqueUsername {
    String message() default "Username is already taken";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// 2. Implement the validator
@Component  // allows injection of Spring beans into the validator
public class UniqueUsernameValidator implements ConstraintValidator<UniqueUsername, String> {

    private final UserRepository userRepository;

    public UniqueUsernameValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean isValid(String username, ConstraintValidatorContext context) {
        if (username == null) {
            // Null handling: @NotBlank or @NotNull should cover this separately
            return true;
        }
        return !userRepository.existsByUsername(username);
    }
}

// 3. Apply to a field
public class UserRequest {
    @NotBlank
    @UniqueUsername
    private String username;
}
```

### Validation Groups

Groups allow different constraints to apply in different scenarios:

```java
public interface OnCreate {}
public interface OnUpdate {}

public class UserRequest {

    @Null(groups = OnUpdate.class, message = "ID must be null for creation")
    @NotNull(groups = OnCreate.class, message = "ID is required for updates")
    private Long id;

    @NotBlank(groups = {OnCreate.class, OnUpdate.class})
    private String username;
}

// @Validated(OnCreate.class) activates only the OnCreate group
@PostMapping
public ResponseEntity<UserResponse> create(
        @RequestBody @Validated(OnCreate.class) UserRequest request) { ... }

@PutMapping("/{id}")
public ResponseEntity<UserResponse> update(
        @PathVariable Long id,
        @RequestBody @Validated(OnUpdate.class) UserRequest request) { ... }
```

### Handling Validation Errors

In `@ControllerAdvice`, handle `MethodArgumentNotValidException` for `@RequestBody` failures and `ConstraintViolationException` for method-level validation failures:

```java
@RestControllerAdvice
public class ValidationExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        Map<String, List<String>> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())));

        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Validation Failed");
        detail.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> violations = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        v -> v.getMessage()));

        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Constraint Violation");
        detail.setProperty("violations", violations);
        return ResponseEntity.badRequest().body(detail);
    }
}
```

## Gotchas

`@Validated` at the class level enables method-level validation via AOP proxy. Self-invocation within the same class bypasses the proxy, meaning that a method calling another method in the same class will not trigger validation on the called method's parameters. Move methods that must be validated to a different bean.

`@Valid` on a method parameter and no handler for `MethodArgumentNotValidException` in `@ControllerAdvice` results in Spring's default error response, which may leak field details depending on `server.error.include-binding-errors` configuration. Always handle `MethodArgumentNotValidException` explicitly to control what the client sees.

Custom `ConstraintValidator` implementations annotated with `@Component` are managed by Spring, which allows dependency injection. However, the Bean Validation provider creates a second instance of the validator using reflection (its own instantiation) for validators that are not discovered via Spring's integration. With Hibernate Validator and Spring Boot, Spring integration is configured by default via `LocalValidatorFactoryBean`, which delegates instantiation to the Spring container — so `@Component` validators receive injection correctly. Without this integration, injected fields are null.

Validation does not run for `null` values by default — most constraint annotations consider `null` valid and rely on `@NotNull` or `@NotBlank` to prohibit it. This "null-permissive" design is intentional: it separates "required" from "valid". A value of `null` passes `@Size`, `@Min`, `@Email`, and others unless `@NotNull` is also present.

`@Validated` groups are not composable by default. Specifying `@Validated(OnCreate.class)` does not automatically include the `Default` group's constraints. If constraints without an explicit group (which belong to `Default`) should also apply during create validation, include `Default.class` explicitly: `@Validated({OnCreate.class, Default.class})`.
