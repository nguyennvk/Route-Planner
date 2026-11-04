package com.planner.route_planner_api.feature.orders.dto;

import java.math.BigDecimal;

import com.planner.route_planner_api.feature.orders.OrderItem;

public record OrderItemResponse(
    Long itemId,
    String name,
    int quantity,
    BigDecimal weightKg         // the whole line: unit weight * quantity
) {
    public static OrderItemResponse from(OrderItem line) {
        return new OrderItemResponse(
            line.getItem().getId(),
            line.getItem().getName(),
            line.getQuantity(),
            line.getWeightKg()
        );
    }
}
