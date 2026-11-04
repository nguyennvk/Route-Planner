package com.planner.route_planner_api.feature.vehicles;

import java.time.Instant;

import com.planner.route_planner_api.common.error.BadRequestException;
import com.planner.route_planner_api.feature.auth.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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

    protected VehicleShift() {}

    public VehicleShift(Vehicle vehicle, User driver, Instant startAt, Instant endAt) {
        checkTimes(startAt, endAt);
        this.vehicle = vehicle;
        this.driver = driver;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public void assignDriver(User driver) {
        this.driver = driver;
    }

    public void unassignDriver() {
        this.driver = null;
    }

    public void reschedule(Instant startAt, Instant endAt) {
        checkTimes(startAt, endAt);
        this.startAt = startAt;
        this.endAt = endAt;
    }

    // Same rule as the table's CHECK (end_at > start_at), caught here before it reaches the database
    private static void checkTimes(Instant startAt, Instant endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new BadRequestException("Shift must end after it starts");      // 400, not a 500
        }
    }
}
