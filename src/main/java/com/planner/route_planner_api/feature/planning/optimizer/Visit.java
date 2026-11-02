package com.planner.route_planner_api.feature.planning.optimizer;

import java.time.Duration;
import java.time.LocalDateTime;

import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.timefold.solver.core.api.domain.variable.PreviousElementShadowVariable;
import ai.timefold.solver.core.api.domain.variable.ShadowSources;
import ai.timefold.solver.core.api.domain.variable.ShadowVariable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@PlanningEntity
@Getter
@Setter
@NoArgsConstructor
public class Visit {

    @PlanningId
    private Long orderId;                   // orders.id, so the result maps straight back to route_stops.order_id
    private String name;                    // customer name, for reading results
    private Location location;              // the order's address
    private long weightKg;               
    private LocalDateTime minStartTime;     // window opens; null = any time
    private LocalDateTime maxEndTime;       // window closes; null = no deadline
    private boolean windowStrict;           // true: being late breaks a hard rule; false: only costs points
    private Duration serviceDuration;       // orders.service_minutes


    @InverseRelationShadowVariable(sourceVariableName = "visits")
    private PlannedVehicle vehicle;

    @PreviousElementShadowVariable(sourceVariableName = "visits")
    private Visit previousVisit;

    @ShadowVariable(supplierName = "arrivalTimeSupplier")
    private LocalDateTime arrivalTime;

    public Visit(Long orderId, String name, Location location, long weightKg,
                 LocalDateTime minStartTime, LocalDateTime maxEndTime, boolean windowStrict,
                 Duration serviceDuration) {
        this.orderId = orderId;
        this.name = name;
        this.location = location;
        this.weightKg = weightKg;
        this.minStartTime = minStartTime;
        this.maxEndTime = maxEndTime;
        this.windowStrict = windowStrict;
        this.serviceDuration = serviceDuration;
    }

    @ShadowSources({"vehicle", "previousVisit.arrivalTime"})
    public LocalDateTime arrivalTimeSupplier() {
        if (vehicle == null) {
            return null;
        }
        if (previousVisit == null) {
            return vehicle.getDepartureTime().plusSeconds(vehicle.getHomeLocation().getDrivingTimeTo(location));
        }
        LocalDateTime previousDeparture = previousVisit.getDepartureTime();
        if (previousDeparture == null) {
            return null;
        }
        return previousDeparture.plusSeconds(previousVisit.getLocation().getDrivingTimeTo(location));
    }

    // Arrive early → wait for the window to open, then serve. Null until the visit is on a route
    public LocalDateTime getDepartureTime() {
        if (arrivalTime == null) {
            return null;
        }
        LocalDateTime serviceStart = (minStartTime != null && arrivalTime.isBefore(minStartTime))
            ? minStartTime
            : arrivalTime;
        return serviceStart.plus(serviceDuration);
    }

    public long getMinutesLate() {
        LocalDateTime departure = getDepartureTime();
        if (departure == null || maxEndTime == null || !departure.isAfter(maxEndTime)) {
            return 0;
        }
        long seconds = Duration.between(maxEndTime, departure).toSeconds();
        return (seconds + 59) / 60;
    }
}
