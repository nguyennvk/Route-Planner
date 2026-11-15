package com.planner.route_planner_api.feature.feedback.dto;

import java.math.BigDecimal;

import com.planner.route_planner_api.feature.feedback.PriceBand;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

// The feedback form. Name and email are optional (email only so we can reply).
// website is a honeypot: hidden on the form, so only bots fill it in
public record FeedbackRequest(
    @Size(max = 100) String name,
    @Email @Size(max = 254) String email,
    @NotBlank @Size(max = 4000) String message,
    @NotNull PriceBand priceBand,
    @PositiveOrZero @DecimalMax("100000") BigDecimal priceAmount,
    String website
) {}
