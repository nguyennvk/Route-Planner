package com.planner.route_planner_api.feature.planning.optimizer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import ai.timefold.solver.core.api.domain.common.PlanningId;
import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import lombok.Getter;
import lombok.Setter;


@PlanningEntity
@Getter 
@Setter 
public class PlannedVehicle {
    @PlanningId
    private Long shiftId;
    private Long vehicleId;
    private int capacity;
    private Location homeLocation;
    private LocalDateTime departureTime;
    private LocalDateTime shiftEndTime;

    // What the solver decides: which visits this vehicle does, in driving order
    @PlanningListVariable(allowsUnassignedValues = true)
    private List<Visit> visits;
    private String geometry;

    public PlannedVehicle() {
    }

    public PlannedVehicle(Long shiftId, Long vehicleId, int capacity, Location homeLocation, LocalDateTime departureTime, LocalDateTime shiftEndTime) {
        this.shiftId = shiftId;
        this.vehicleId = vehicleId;
        this.capacity = capacity;
        this.homeLocation = homeLocation;
        this.departureTime = departureTime;
        this.shiftEndTime = shiftEndTime;
        this.visits = new ArrayList<>();
    }

    public Double[][] extractRouteLocations() {
        int size = visits.size() + 2;   // the deliveries, plus home at both ends
        Double[] latitudes = new Double[size];
        Double[] longitudes = new Double[size];

        latitudes[0] = latitudes[size - 1] = homeLocation.getLattitude();
        longitudes[0] = longitudes[size - 1] = homeLocation.getLongitude();

        for (int i = 0; i < visits.size(); i++) {
            Location location = visits.get(i).getLocation();
            latitudes[i + 1] = location.getLattitude();
            longitudes[i + 1] = location.getLongitude();
        }
        return new Double[][] { latitudes, longitudes };
    }

    public LocalDateTime getReturnTime() {
        if (visits.isEmpty()) return null;
        Visit last = visits.get(visits.size() - 1);
        return last.getDepartureTime().plusSeconds(last.getLocation().getDrivingTimeTo(homeLocation));
    }

    public Integer getTotalMinutes() {
        LocalDateTime returnTime = getReturnTime();
        if (returnTime == null) return 0;
        return (int) Duration.between(departureTime, returnTime).toMinutes();
    }

    // Paid seconds inside the shift: leaving the depot until back, capped at the shift end.
    // Waiting for a window counts, because the driver is on the clock. 0 for an empty route
    public long getRegularSeconds() {
        LocalDateTime returnTime = getReturnTime();
        if (returnTime == null) return 0;
        LocalDateTime end = returnTime.isAfter(shiftEndTime) ? shiftEndTime : returnTime;
        return Duration.between(departureTime, end).toSeconds();
    }

    // Paid seconds after the shift end; 0 when the vehicle is back in time
    public long getOvertimeSeconds() {
        LocalDateTime returnTime = getReturnTime();
        if (returnTime == null || !returnTime.isAfter(shiftEndTime)) return 0;
        return Duration.between(shiftEndTime, returnTime).toSeconds();
    }

    // Road metres depot → stops → depot; 0 for an empty route. A primitive long so constraints can use it cheaply
    public long getTotalMeters() {
        if (visits.isEmpty()) return 0;
        long total = 0;
        Location previous = homeLocation;
        for (Visit visit : visits) {
            total += previous.getDrivingDistanceTo(visit.getLocation());
            previous = visit.getLocation();
        }
        return total + previous.getDrivingDistanceTo(homeLocation);
    }

    // Kilometres with 2 decimals, for showing and saving; null for an empty route
    public BigDecimal getTotalDistance() {
        if (visits.isEmpty()) return null;
        return BigDecimal.valueOf(getTotalMeters()).divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP);
    }
    
    public Long getTotalWeight() {
        return visits.stream().mapToLong(Visit::getWeightKg).sum();
    }
}
