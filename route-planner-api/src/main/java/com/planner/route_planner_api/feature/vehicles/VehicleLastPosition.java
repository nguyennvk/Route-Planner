package com.planner.route_planner_api.feature.vehicles;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import com.planner.route_planner_api.feature.route.Route;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "vehicle_last_positions")
@Getter 
public class VehicleLastPosition {
    private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);

    @Id
    @Column(name = "vehicle_id")
    private Long vehicleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id")
    private Route route;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(columnDefinition = "geography(Point, 4326)")
    private Point location;

    @Column(name = "speed_kmh", precision = 5, scale = 1)
    private BigDecimal speedKmh;

    @Column(nullable = false)
    private Instant recordedAt;

    protected VehicleLastPosition() {}

    public VehicleLastPosition(Route route, Double latitude, Double longitude, BigDecimal speedKmh) {
        this.vehicleId = route.getVehicle().getId();
        this.route = route;
        this.location = GEOMETRY.createPoint(new Coordinate(longitude, latitude));
        this.speedKmh = speedKmh;
        this.recordedAt = Instant.now();
    }

    public void moveTo(Route route, Double latitude, Double longitude, BigDecimal speedKmh) {
        this.route = route;
        this.location = GEOMETRY.createPoint(new Coordinate(longitude, latitude));
        this.speedKmh = speedKmh;
        this.recordedAt = Instant.now();
    }

    public Double getLatitude() {
        return this.location.getY();
    }

    public Double getLongitude() {
        return this.location.getX();
    }
}
