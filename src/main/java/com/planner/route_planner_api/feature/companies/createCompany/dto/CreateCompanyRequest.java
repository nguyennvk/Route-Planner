package com.planner.route_planner_api.feature.companies.createCompany.dto;

import com.planner.route_planner_api.common.validation.ValidTimezone;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCompanyRequest(
    @NotBlank @Size(max = 254)
    String name,
    
    @NotBlank 
    @ValidTimezone 
    String timezone
) {
    @Override
    public String toString(){
        return "CreateCompanyRequest[name=" + name + ", timezone=" + timezone + "]";
    }
}
