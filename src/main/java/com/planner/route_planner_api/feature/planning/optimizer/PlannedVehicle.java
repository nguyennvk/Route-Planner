package com.planner.route_planner_api.feature.planning.optimizer;

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


}
