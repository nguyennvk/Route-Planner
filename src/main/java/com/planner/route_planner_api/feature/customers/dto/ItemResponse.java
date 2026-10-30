package com.planner.route_planner_api.feature.customers.dto;

import java.math.BigDecimal;

import com.planner.route_planner_api.feature.customers.Item;

public record ItemResponse(
    Long id,
    String sku,
    String name,
    BigDecimal weightKg,
    BigDecimal volumeM3
) {
    public static ItemResponse from(Item item) {
        return new ItemResponse(item.getId(), item.getSku(), item.getName(), item.getWeightKg(), item.getVolumeM3());
    }
}
