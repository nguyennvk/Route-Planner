package com.planner.route_planner_api.feature.depots.dto;

import com.planner.route_planner_api.feature.addresses.dto.CreateAddressRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DepotRequest(
    @NotBlank @Size(max = 254)      // depots.name limit
    String name,

    @NotNull @Valid 
    CreateAddressRequest address
) {}
