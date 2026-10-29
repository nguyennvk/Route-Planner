package com.planner.route_planner_api.feature.addresses.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
    @NotBlank @Size(max = 254)
    String rawText
) {

}
