package com.planner.route_planner_api.feature.companies.createCompany.dto;

import java.time.Instant;

import com.planner.route_planner_api.feature.companies.Company;

public record CreateCompanyResponse(Long id, String name, String timezone, Instant createdAt) {
    public static CreateCompanyResponse from(Company company){
        return new CreateCompanyResponse(company.getId(), company.getName(), company.getTimezone(), company.getCreatedAt());
    }
}
