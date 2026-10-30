package com.planner.route_planner_api.feature.orders.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

// PATCH: every field is optional, null means "leave unchanged".
// items, when sent, replaces the whole list of lines (not merged with the old one).
public record UpdateOrderRequest(
    Long addressId,

    @Size(min = 1, max = 100) @Valid
    List<OrderItemRequest> items,

    Instant windowStart,
    Instant windowEnd,
    Boolean windowStrict,

    @Min(0) @Max(480)
    Integer serviceMinutes,

    @Size(max = 2000)
    String notes
) {}
