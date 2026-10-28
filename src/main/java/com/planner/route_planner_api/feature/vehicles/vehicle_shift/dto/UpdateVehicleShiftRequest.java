package com.planner.route_planner_api.feature.vehicles.vehicle_shift.dto;

import java.time.Instant;

// PATCH: every field is optional, null means "leave unchanged"
public record UpdateVehicleShiftRequest(
    Long driverUserId,
    Instant startAt,
    Instant endAt
) {
    @Override
    public String toString(){
        return "UpdateVehicleShiftRequest[driverUserId=" + driverUserId + ", startAt=" + startAt + ", endAt=" + endAt + "]";
    }
}
