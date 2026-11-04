package com.planner.route_planner_api.feature.vehicles.vehicle.dto;

import java.math.BigDecimal;

import com.planner.route_planner_api.feature.vehicles.VehicleType;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record VehicleRequest(
    @NotBlank @Size(max = 254)
    String name,

    @NotNull
    VehicleType type,

    // @NotNull, not @NotBlank: @NotBlank only works on text (it would make every request a 500)
    // @Digits matches the column numeric(10, 2): up to 8 digits before the point, 2 after
    @NotNull @Positive @Digits(integer = 8, fraction = 2)
    BigDecimal capacityKg,

    @NotNull
    Long homeDepotId,

    // Optional; numeric(10, 3)
    @Positive @Digits(integer = 7, fraction = 3)
    BigDecimal capacityM3
) {
    @Override
    public String toString(){
        return "VehicleRequest[name=" + name + ", type=" + type + ", capacityKg=" + capacityKg
            + ", homeDepotId=" + homeDepotId + ", capacityM3=" + capacityM3 + "]";
    }
}
