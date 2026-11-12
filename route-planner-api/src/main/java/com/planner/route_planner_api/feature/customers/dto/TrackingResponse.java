package com.planner.route_planner_api.feature.customers.dto;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.planner.route_planner_api.common.time.ZonedTime;
import com.planner.route_planner_api.feature.addresses.Address;
import com.planner.route_planner_api.feature.companies.Company;
import com.planner.route_planner_api.feature.customers.TrackingStage;
import com.planner.route_planner_api.feature.orders.Order;
import com.planner.route_planner_api.feature.orders.OrderStatus;
import com.planner.route_planner_api.feature.route.RouteStop;
import com.planner.route_planner_api.feature.vehicles.VehicleLastPosition;

// What a customer sees behind their tracking link (no login). Only data about their own order:
// never other stops, and the van only once it's coming to them.
public record TrackingResponse(
    TrackingStage stage,
    OrderStatus orderStatus,
    String companyName,
    LocalDate planDate,
    String address,
    Double latitude, Double longitude,              // the customer's pin
    LocalDateTime windowStart, LocalDateTime windowEnd,
    LocalDateTime etaStart, LocalDateTime etaEnd,    // company wall clock, like the rest of the API
    int delayMinutes,                                // how much later than planned the delivery now looks; 0 when on time
    int stopsBefore,
    Double vehicleLatitude, Double vehicleLongitude, // null unless stage is next or arrived
    LocalDateTime vehicleRecordedAt,
    String geometry,                                 // van → customer, only when next
    LocalDateTime deliveredAt
) {
    // The service decides the stage, the count, the ETA and the path; this only shapes them.
    // stop must come with route → plan → company loaded (findForTracking fetches them).
    // position and geometry may be null: no fix yet, or OSRM had no path.
    public static TrackingResponse from(
        RouteStop stop,
        TrackingStage stage,
        int stopsBefore,
        Instant etaStart,
        Instant etaEnd,
        Duration delay,
        VehicleLastPosition position,
        String geometry
    ) {
        Order order = stop.getOrder();
        Address address = order.getAddress();
        Company company = stop.getRoute().getPlan().getCompany();
        ZoneId zone = company.zoneId();

        // Enforced here rather than trusted to every caller: while other customers come first, the van is
        // standing at their door, so its position would tell this customer where someone else lives
        boolean showVehicle = position != null && (stage == TrackingStage.next || stage == TrackingStage.arrived);

        return new TrackingResponse(
            stage,
            order.getStatus(),
            company.getName(),
            stop.getRoute().getPlan().getPlanDate(),
            address.getNormalizedAddress() != null ? address.getNormalizedAddress() : address.getRawAddress(),
            address.getLatitude(),
            address.getLongitude(),
            ZonedTime.toLocalDateTime(order.getWindowStart(), zone),
            ZonedTime.toLocalDateTime(order.getWindowEnd(), zone),
            ZonedTime.toLocalDateTime(etaStart, zone),
            ZonedTime.toLocalDateTime(etaEnd, zone),
            (int) delay.toMinutes(),
            stopsBefore,
            showVehicle ? position.getLatitude() : null,
            showVehicle ? position.getLongitude() : null,
            showVehicle ? ZonedTime.toLocalDateTime(position.getRecordedAt(), zone) : null,
            stage == TrackingStage.next ? geometry : null,
            // The driver leaves the stop when the parcel is handed over, so departure is the delivery time
            stage == TrackingStage.delivered ? ZonedTime.toLocalDateTime(stop.getActualDeparture(), zone) : null
        );
    }
}
