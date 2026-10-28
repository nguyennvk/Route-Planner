package com.planner.route_planner_api.feature.companies;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.planner.route_planner_api.feature.companies.createCompany.dto.CreateCompanyRequest;
import com.planner.route_planner_api.feature.companies.createCompany.dto.CreateCompanyResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController 
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService){
        this.companyService = companyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateCompanyResponse createCompany (@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateCompanyRequest request) {
        Long userId = Long.valueOf(jwt.getSubject());
        return companyService.createCompany(request, userId);
    }
    
}
