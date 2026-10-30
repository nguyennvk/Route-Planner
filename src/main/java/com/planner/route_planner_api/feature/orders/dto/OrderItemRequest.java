package com.planner.route_planner_api.feature.orders.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
    @NotNull
    Long itemId,

    // order_items.quantity is an int with CHECK (quantity > 0); the upper limit just catches typos like 30000
    @NotNull @Min(1) @Max(10000)
    Integer quantity
) {}
