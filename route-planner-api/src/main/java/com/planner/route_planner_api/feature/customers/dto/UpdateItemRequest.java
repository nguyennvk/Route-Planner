package com.planner.route_planner_api.feature.customers.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// PATCH: every field is optional, null means "leave unchanged"
public record UpdateItemRequest(
    @Size(max = 254) @Pattern(regexp = ".*\\S.*", message = "must not be blank")
    String name,

    // Unique per company. "" removes the SKU (null can't, it means "unchanged")
    @Size(max = 64)
    String sku,

    @Positive @Digits(integer = 7, fraction = 3)
    BigDecimal weightKg,

    @Positive @Digits(integer = 6, fraction = 4)
    BigDecimal volumeM3
) {}
