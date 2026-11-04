package com.planner.route_planner_api.feature.companies.company.dto;

import java.time.Instant;

import com.planner.route_planner_api.feature.companies.Company;

public record CompanyResponse(
    Long id, 
    String name,
    String timezone,
    Instant createdAt
) {
    public static CompanyResponse from(Company company){
        return new CompanyResponse(company.getId(), company.getName(), company.getTimezone(), company.getCreatedAt());
    }
}
