package com.planner.route_planner_api.feature.customers.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ItemRequest(
    @NotBlank @Size(max = 254)
    String name,

    // Weight of ONE unit. @Digits matches the column numeric(10, 3): 7 digits before the point, 3 after
    @NotNull @Positive @Digits(integer = 7, fraction = 3)
    BigDecimal weightKg,

    // Optional; unique per company
    @Size(max = 64)
    String sku,

    // Optional; numeric(10, 4)
    @Positive @Digits(integer = 6, fraction = 4)
    BigDecimal volumeM3
) {}
