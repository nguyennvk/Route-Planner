package com.planner.route_planner_api.feature.route;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.planner.route_planner_api.common.error.ConflictException;
import com.planner.route_planner_api.feature.orders.Order;

import jakarta.persistence.*;
import lombok.Getter;

// One visit on a route, in order. planned_* comes from the solver, actual_* from the driver
@Entity
@Table(name = "route_stops")
@Getter
public class RouteStop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Integer sequence;               // 0-based position on the route; unique per route

    private Instant plannedArrival;
    private Instant plannedDeparture;
    private Instant estimatedArrival;       // updated live from GPS while driving
    private Instant actualArrival;
    private Instant actualDeparture;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "stop_status")
    private StopStatus status = StopStatus.pending;

    private String failureReason;
    private String proofPhotoUrl;

    protected RouteStop() {}

    public RouteStop(Route route, Order order, Integer sequence, Instant plannedArrival, Instant plannedDeparture) {
        this.route = route;
        this.order = order;
        this.sequence = sequence;
        this.plannedArrival = plannedArrival;
        this.plannedDeparture = plannedDeparture;
        this.estimatedArrival = plannedArrival;
    }

    public void updateEstimatedArrival(Instant estimatedArrival) {
        this.estimatedArrival = estimatedArrival;
    }

    public void arrive() {
        requireStatus(StopStatus.pending);
        this.status = StopStatus.arrived;
        this.actualArrival = Instant.now();
    }

    public void deliver(String proofPhotoUrl) {
        requireStatus(StopStatus.arrived);
        this.status = StopStatus.delivered;
        this.proofPhotoUrl = proofPhotoUrl;
        this.actualDeparture = Instant.now();
    }

    public void fail(String reason) {
        requireStatus(StopStatus.arrived);
        this.status = StopStatus.failed;
        this.failureReason = reason;
        this.actualDeparture = Instant.now();
    }

    // Driver never went there, e.g. the order was cancelled mid-route
    public void skip() {
        requireStatus(StopStatus.pending);
        this.status = StopStatus.skipped;
    }

    private void requireStatus(StopStatus expected) {
        if (this.status != expected) {
            throw new ConflictException("Stop is " + this.status + ", expected " + expected);
        }
    }
}
