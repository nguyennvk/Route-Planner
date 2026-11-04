package com.planner.route_planner_api.feature.depots.dto;

import com.planner.route_planner_api.feature.addresses.dto.CreateAddressResponse;
import com.planner.route_planner_api.feature.depots.Depot;

public record DepotResponse(
    Long id,
    String name,
    CreateAddressResponse address
) {

    public static DepotResponse from(Depot depot){
        return new DepotResponse(
            depot.getId(), 
            depot.getName(),
            CreateAddressResponse.from(depot.getAddress())
        );
    }
}
