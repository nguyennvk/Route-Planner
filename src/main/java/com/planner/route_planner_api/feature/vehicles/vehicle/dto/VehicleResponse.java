package com.planner.route_planner_api.feature.vehicles.vehicle.dto;

import java.math.BigDecimal;

import com.planner.route_planner_api.feature.vehicles.Vehicle;
import com.planner.route_planner_api.feature.vehicles.VehicleType;

public record VehicleResponse(
    Long id,
    String name,
    VehicleType type,
    BigDecimal capacityKg,
    BigDecimal capacityM3,
    Long homeDepotId,
    boolean active
) {
    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(
            vehicle.getId(),
            vehicle.getName(),
            vehicle.getType(),
            vehicle.getCapacityKg(),
            vehicle.getCapacityM3(),
            vehicle.getHomeDepot().getId(),     // reading only the id of a lazy proxy doesn't run a query
            vehicle.isActive()
        );
    }
}
