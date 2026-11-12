package com.planner.route_planner_api.feature.vehicles.vehicle.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.planner.route_planner_api.common.time.ZonedTime;
import com.planner.route_planner_api.feature.vehicles.VehicleLastPosition;

// Same fields as the websocket's PositionMessage plus the vehicle, so the map can start from this
// snapshot and apply live messages on top. recordedAt is on the company's wall clock, like PositionMessage.
public record VehiclePositionResponse(
    Long vehicleId,
    Long routeId,           // null once the route it was sent for is deleted
    Double latitude,
    Double longitude,
    BigDecimal speedKmh,
    LocalDateTime recordedAt
) {
    public static VehiclePositionResponse from(VehicleLastPosition position, ZoneId zone) {
        return new VehiclePositionResponse(
            position.getVehicleId(),
            position.getRoute() == null ? null : position.getRoute().getId(),   // id of a lazy proxy: no query
            position.getLatitude(),
            position.getLongitude(),
            position.getSpeedKmh(),
            ZonedTime.toLocalDateTime(position.getRecordedAt(), zone)
        );
    }
}
