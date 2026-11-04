package com.planner.route_planner_api.feature.vehicles.vehicle_shift.dto;

import java.time.Instant;

import com.planner.route_planner_api.feature.auth.User;
import com.planner.route_planner_api.feature.vehicles.VehicleShift;

public record VehicleShiftResponse(
    Long id,
    Long vehicleId,
    String vehicleName,
    Long driverUserId,
    String driverName,
    Instant startAt,
    Instant endAt
) {
    // Expects vehicle and driver to be loaded already (join fetch), otherwise each call runs extra queries
    public static VehicleShiftResponse from(VehicleShift shift) {
        User driver = shift.getDriver();
        return new VehicleShiftResponse(
            shift.getId(),
            shift.getVehicle().getId(),
            shift.getVehicle().getName(),
            driver == null ? null : driver.getId(),
            driver == null ? null : driver.getFullName(),
            shift.getStartAt(),
            shift.getEndAt()
        );
    }
}
