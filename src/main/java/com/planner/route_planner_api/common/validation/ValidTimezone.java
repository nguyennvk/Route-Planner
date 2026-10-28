package com.planner.route_planner_api.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = TimezoneValidator.class)       // which class does the checking
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)                       // keep it visible while the app runs
public @interface ValidTimezone {
    String message() default "must be a valid IANA timezone, e.g. Asia/Ho_Chi_Minh";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
