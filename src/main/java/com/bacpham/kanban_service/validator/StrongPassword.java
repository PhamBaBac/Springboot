package com.bacpham.kanban_service.validator;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrongPasswordValidator.class)
@Documented
public @interface StrongPassword {
    String message() default "Password must be at least 8 characters and include uppercase, lowercase, number, and special character";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
