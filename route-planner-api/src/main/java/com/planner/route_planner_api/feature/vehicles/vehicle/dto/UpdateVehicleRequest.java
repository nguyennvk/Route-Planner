package com.planner.route_planner_api.feature.vehicles.vehicle.dto;

import java.math.BigDecimal;

import com.planner.route_planner_api.feature.vehicles.VehicleType;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// PATCH: every field is optional, null means "leave unchanged".
// The constraints only run on fields that were sent (they all skip null).
public record UpdateVehicleRequest(
    @Size(max = 254)
    @Pattern(regexp = ".*\\S.*", message = "must not be blank")
    String name,

    VehicleType type,

    @Positive @Digits(integer = 8, fraction = 2)
    BigDecimal capacityKg,

    @Positive @Digits(integer = 7, fraction = 3)
    BigDecimal capacityM3,

    Long homeDepotId,

    Boolean active          // Boolean, not boolean: "not sent" must be null, not false
) {
    @Override
    public String toString(){
        return "UpdateVehicleRequest[name=" + name + ", type=" + type + ", capacityKg=" + capacityKg
            + ", capacityM3=" + capacityM3 + ", homeDepotId=" + homeDepotId + ", active=" + active + "]";
    }
}
