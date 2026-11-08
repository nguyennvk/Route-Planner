package com.planner.route_planner_api.feature.route;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.planner.route_planner_api.common.error.ConflictException;
import com.planner.route_planner_api.feature.auth.User;
import com.planner.route_planner_api.feature.planning.Plan;
import com.planner.route_planner_api.feature.vehicles.Vehicle;
import com.planner.route_planner_api.feature.vehicles.VehicleShift;

import jakarta.persistence.*;
import lombok.Getter;

@Entity 
@Table(name = "routes")
@Getter
public class Route {
    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shift_id", nullable = false)
    private VehicleShift vehicleShift;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_user_id")
    private User driver;

    
    private Integer totalMinutes;

    @Column(precision = 8, scale = 2)
    private BigDecimal totalKm;

    @Column(nullable = false)
    private String shareToken;

    private Instant plannedStart;           // from the solver
    private Instant plannedEnd;
    private Instant startedAt;              // set when the driver actually starts / finishes
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "assignment_status")
    private AssignementStatus assignmentStatus = AssignementStatus.pending;
    
    private Instant respondedAt;
    private String rejectionReason;
    private String geometry;
    
    @Version 
    private int version;

    protected Route() {}

    public Route(
        Plan plan,
        Vehicle vehicle, 
        VehicleShift vehicleShift, 
        User driver,
        Integer totalMinutes,
        BigDecimal totalKm,
        Instant plannedStart,
        Instant plannedEnd
    ){
        this.plan = plan;
        this.vehicle = vehicle;
        this.vehicleShift = vehicleShift;
        this.driver = driver;
        this.totalMinutes = totalMinutes;
        this.totalKm = totalKm;
        this.shareToken = UUID.randomUUID().toString();
        this.plannedStart = plannedStart;
        this.plannedEnd = plannedEnd;
    }

    public void accept(){
        requirePending();
        this.assignmentStatus = AssignementStatus.accepted;
        this.respondedAt = Instant.now();
    }

    public void reject(String reason){
        requirePending();
        this.assignmentStatus = AssignementStatus.rejected;
        this.rejectionReason = reason;
        this.respondedAt = Instant.now();
    }

    // A driver answers once; accepting after rejecting (or vice versa) is a conflict
    private void requirePending(){
        if (this.assignmentStatus != AssignementStatus.pending) {
            throw new ConflictException("Route was already " + this.assignmentStatus);
        }
    }

    public void setGeometry(String geometry){
        this.geometry = geometry;
    }

    public void setDriver(User driver) {
        this.driver = driver;
    }

}
