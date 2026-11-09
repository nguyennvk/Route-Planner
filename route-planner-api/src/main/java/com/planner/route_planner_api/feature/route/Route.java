package com.planner.route_planner_api.feature.route;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.planner.route_planner_api.common.error.ConflictException;
import com.planner.route_planner_api.feature.auth.User;
import com.planner.route_planner_api.feature.planning.Plan;
import com.planner.route_planner_api.feature.planning.PlanStatus;
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
    @Column(columnDefinition = "assignment_status", nullable = false)
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

    // A driver can change their mind (accept after turning it down, or the other way round)
    // until the route has started or the work is done
    public void accept(){
        requireAnswerOpen();
        this.assignmentStatus = AssignementStatus.accepted;
        this.rejectionReason = null;
        this.respondedAt = Instant.now();
    }

    // Turning it down again just replaces the reason
    public void reject(String reason){
        requireAnswerOpen();
        this.assignmentStatus = AssignementStatus.rejected;
        this.rejectionReason = reason;
        this.respondedAt = Instant.now();
    }

    // Only an accepted route on a live plan can start, and only once. Starting locks the answer (see requireAnswerOpen)
    public void start(){
        if (this.assignmentStatus != AssignementStatus.accepted) {
            throw new ConflictException("Accept the route before starting it; it is " + this.assignmentStatus);
        }
        if (this.plan.getStatus() != PlanStatus.published) {
            throw new ConflictException("This route's plan is " + this.plan.getStatus() + ", so the route can't start");
        }
        if (this.startedAt != null) {
            throw new ConflictException("This route has already started");
        }
        this.startedAt = Instant.now();
    }

    public void finish(){
        if (this.startedAt == null) {
            throw new ConflictException("Start the route before finishing it");
        }
        if (this.finishedAt != null) {
            throw new ConflictException("This route has already finished");
        }
        this.finishedAt = Instant.now();
    }

    // Read by RouteResponse so the app only offers "change answer" when the API would allow it
    public boolean isAnswerOpen(){
        return this.startedAt == null && this.finishedAt == null && this.plan.getStatus() != PlanStatus.completed;
    }

    private void requireAnswerOpen(){
        if (this.finishedAt != null || this.plan.getStatus() == PlanStatus.completed) {
            throw new ConflictException("This route is completed, so its answer can't change");
        }
        if (this.startedAt != null) {
            throw new ConflictException("This route has started, so its answer can't change");
        }
    }

    public void requireInProgress() {
        if (this.startedAt == null) {
            throw new ConflictException("Start the route before finishing it");
        }
    }

    public void setGeometry(String geometry){
        this.geometry = geometry;
    }

    public void setDriver(User driver) {
        this.driver = driver;
    }

}
