package com.planner.route_planner_api.feature.addresses;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

import com.planner.route_planner_api.common.persistence.BaseEntity;
import com.planner.route_planner_api.feature.addresses.address_api.GeocodeSourceType;
import com.planner.route_planner_api.feature.companies.Company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

// A place a vehicle can drive to: a customer's door or a depot.
// Created in one of two ways, which is why there is no public constructor:
//   geocoded(...)  from a signed Geocodio suggestion
//   pinned(...)    from a point the user dropped on the map
@Entity
@Table(name = "addresses")
@Getter
public class Address extends BaseEntity {

    // SRID 4326 = plain GPS latitude/longitude, the same system the geography column uses
    private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "raw_address", nullable = false, length = 512)
    private String rawAddress;

    @Column(name = "normalized_address", length = 512)
    private String normalizedAddress;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(columnDefinition = "geography(Point, 4326)")
    private Point location;

    // Geocodio's accuracy score (0.00 to 1.00); null for manual pins because no geocoder scored them
    @Column(name = "geocode_confidence", precision = 3, scale = 2)
    private BigDecimal geocodeConfidence;

    // STRING stores the constant's name; GeocodeSourceType's constants are lowercase, so the column holds "geocodio", "manual_pin"
    @Enumerated(EnumType.STRING)
    @Column(name = "geocode_source")
    private GeocodeSourceType geocodeSource;

    protected Address() {}

    private Address(Company company, String rawAddress, String normalizedAddress,
                    double latitude, double longitude, BigDecimal geocodeConfidence, GeocodeSourceType geocodeSource) {
        this.company = company;
        this.rawAddress = rawAddress;
        this.normalizedAddress = normalizedAddress;
        // location and source are always set together (the table has a CHECK for it)
        this.location = GEOMETRY.createPoint(new Coordinate(longitude, latitude));
        this.geocodeConfidence = geocodeConfidence;
        this.geocodeSource = geocodeSource;
    }

    public static Address geocoded(Company company, String rawAddress, String formattedAddress,
                                   double latitude, double longitude, double accuracy) {
        BigDecimal confidence = BigDecimal.valueOf(accuracy).setScale(2, RoundingMode.HALF_UP);   // numeric(3, 2)
        return new Address(company, rawAddress, formattedAddress, latitude, longitude, confidence, GeocodeSourceType.geocodio);
    }

    public static Address pinned(Company company, String rawAddress, double latitude, double longitude) {
        return new Address(company, rawAddress, null, latitude, longitude, null, GeocodeSourceType.manual_pin);
    }

    public Double getLatitude() {
        return location == null ? null : location.getY();
    }

    public Double getLongitude() {
        return location == null ? null : location.getX();
    }

    public void updatePin(double latitude, double longitude){
        this.location = GEOMETRY.createPoint(new Coordinate(longitude, latitude));
        this.geocodeSource = GeocodeSourceType.manual_pin;
        this.geocodeConfidence = null;
    }

}
