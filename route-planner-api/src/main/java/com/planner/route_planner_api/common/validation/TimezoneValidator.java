package com.planner.route_planner_api.common.validation;

import java.time.ZoneId;
import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TimezoneValidator implements ConstraintValidator<ValidTimezone, String> {

    // Every region name Java knows: "Asia/Ho_Chi_Minh", "Europe/Paris", ...
    private static final Set<String> ZONES = ZoneId.getAvailableZoneIds();

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;                  // empty is @NotBlank's job, so it isn't reported twice
        }
        return ZONES.contains(value);
    }
}
