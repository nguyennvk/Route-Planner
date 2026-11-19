package com.planner.route_planner_api.feature.vehicles;

import java.time.Instant;

import com.planner.route_planner_api.common.error.BadRequestException;
import com.planner.route_planner_api.common.error.ConflictException;
import com.planner.route_planner_api.feature.auth.User;
import com.planner.route_planner_api.feature.route.Route;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;

// One row per shift: which vehicle, which driver, from when to when.
// Not a BaseEntity: the vehicle_shifts table has no created_at column.
@Entity
@Table(name = "vehicle_shifts")
@Getter
public class VehicleShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many shifts per vehicle
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    // Optional: a shift can be planned before a driver is assigned
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_user_id")
    private User driver;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    // The route that took this shift when its plan was published; null while the shift is free.
    // Like an order's 'planned' status: set by claim() at publish, cleared by free() when the route is released.
    // Lazy: checking for null needs no query (the id is in this row); only reading the route loads it
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "published_route_id")
    private Route publishedRoute;

    // Two plans published at the same moment both see the shift free; the second UPDATE then finds a newer
    // version and fails, and GlobalExceptionHandler turns that into a 409
    @Version
    private int version;

    protected VehicleShift() {}

    public VehicleShift(Vehicle vehicle, User driver, Instant startAt, Instant endAt) {
        checkTimes(startAt, endAt);
        this.vehicle = vehicle;
        this.driver = driver;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public void assignDriver(User driver) {
        requireFree();
        this.driver = driver;
    }

    public void unassignDriver() {
        requireFree();
        this.driver = null;
    }

    public void reschedule(Instant startAt, Instant endAt) {
        requireFree();
        checkTimes(startAt, endAt);
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public boolean isPublished() {
        return publishedRoute != null;
    }

    // Publishing: this shift now belongs to that route. One published route per shift, so a second plan
    // with the same van and driver can't go out
    public void claim(Route route) {
        if (isPublished()) {
            throw new ConflictException("Shift " + id + " is already on a published plan");
        }
        this.publishedRoute = route;
    }

    // The route was released: the shift can be edited and planned again
    public void free() {
        this.publishedRoute = null;
    }

    // A published route copied this shift's driver and times when it was solved; changing them now would
    // leave the route saying one thing and the shift another. Same idea as Order.isEditable
    public void requireFree() {
        if (isPublished()) {
            throw new ConflictException("This shift is on a published plan, so it can't be changed or deleted");
        }
    }

    // Same rule as the table's CHECK (end_at > start_at), caught here before it reaches the database
    private static void checkTimes(Instant startAt, Instant endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new BadRequestException("Shift must end after it starts");      // 400, not a 500
        }
    }
}
