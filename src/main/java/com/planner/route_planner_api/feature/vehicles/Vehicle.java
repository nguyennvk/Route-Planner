package com.planner.route_planner_api.feature.vehicles;

import java.math.BigDecimal;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.planner.route_planner_api.feature.companies.Company;
import com.planner.route_planner_api.feature.depots.Depot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

// Not a BaseEntity: the vehicles table has no created_at column
@Entity
@Table(name = "vehicles")
@Getter
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many vehicles belong to one company
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 254)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)                    // a real Postgres ENUM, not varchar
    @Column(nullable = false, columnDefinition = "vehicle_type")
    private VehicleType type;

    // BigDecimal, not double: numeric(10, 2) is an exact decimal, double would turn 0.1 into 0.1000000000000000055...
    @Column(name = "capacity_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal capacityKg;

    @Column(name = "capacity_m3", precision = 10, scale = 3)     // optional: not every company tracks volume
    private BigDecimal capacityM3;

    // Where the vehicle starts and ends its routes; many vehicles per depot
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_depot_id", nullable = false)
    private Depot homeDepot;

    @Column(nullable = false)
    private boolean active = true;

    protected Vehicle() {}

    public Vehicle(Company company, String name, VehicleType type,
                   BigDecimal capacityKg, BigDecimal capacityM3, Depot homeDepot) {
        this.company = company;
        this.name = name;
        this.type = type;
        this.capacityKg = capacityKg;
        this.capacityM3 = capacityM3;
        this.homeDepot = homeDepot;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void changeType(VehicleType type) {
        this.type = type;
    }

    public void changeCapacity(BigDecimal capacityKg, BigDecimal capacityM3) {
        this.capacityKg = capacityKg;
        this.capacityM3 = capacityM3;
    }

    public void moveToDepot(Depot homeDepot) {
        this.homeDepot = homeDepot;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }
}
