package com.planner.route_planner_api.feature.orders.dto;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderRequest(
    @NotNull
    Long customerId,

    // @Valid: also check each line's own annotations (itemId, quantity)
    @NotEmpty @Size(max = 100) @Valid
    List<OrderItemRequest> items,

    // Optional: when missing, the order goes to the customer's default address
    Long addressId,

    // Optional; ISO-8601 with a zone, e.g. "2026-10-26T02:00:00Z"
    Instant windowStart,
    Instant windowEnd,

    // Optional, default false
    Boolean windowStrict,

    // Optional, default 5 (same as the column default)
    @Min(0) @Max(480)
    Integer serviceMinutes,

    @Size(max = 2000)
    String notes
) {}
