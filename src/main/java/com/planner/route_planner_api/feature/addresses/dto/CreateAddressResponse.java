package com.planner.route_planner_api.feature.addresses.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.planner.route_planner_api.feature.addresses.Address;
import com.planner.route_planner_api.feature.addresses.address_api.GeocodeSourceType;

public record CreateAddressResponse(
    Long id,
    String rawAddress,
    String normalizedAddress,
    Double latitude,
    Double longitude,
    BigDecimal geocodeConfidence,
    GeocodeSourceType geocodeSource,
    Instant createdAt
){
    
	public static CreateAddressResponse from(Address address){
        return new CreateAddressResponse(
                address.getId(), 
                address.getRawAddress(),
                address.getNormalizedAddress(),
                address.getLatitude(), 
                address.getLongitude(), 
                address.getGeocodeConfidence(), 
                address.getGeocodeSource(), 
                address.getCreatedAt()
            );
    }
}
