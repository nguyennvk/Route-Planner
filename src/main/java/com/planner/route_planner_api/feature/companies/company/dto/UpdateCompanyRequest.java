package com.planner.route_planner_api.feature.companies.company.dto;

import com.planner.route_planner_api.common.validation.ValidTimezone;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// PATCH: every field is optional, null means "leave unchanged".
// No @NotBlank (it rejects null); @Pattern skips null but rejects "" and "   " when the field is sent.
public record UpdateCompanyRequest(
    @Size(max = 254)
    @Pattern(regexp = ".*\\S.*", message = "must not be blank")
    String name,

    @Pattern(regexp = ".*\\S.*", message = "must not be blank")
    @ValidTimezone
    String timezone
) {
    @Override
    public String toString(){
        return "UpdateCompanyRequest[name=" + name + ", timezone=" + timezone + "]";
    }
}
