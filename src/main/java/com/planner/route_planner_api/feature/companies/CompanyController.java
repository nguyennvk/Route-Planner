package com.planner.route_planner_api.feature.companies;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.planner.route_planner_api.feature.companies.company.dto.CompanyResponse;
import com.planner.route_planner_api.feature.companies.company.dto.CompanyRequest;
import com.planner.route_planner_api.feature.companies.company.dto.UpdateCompanyRequest;
import com.planner.route_planner_api.feature.companies.member.dto.MemberResponse;
import com.planner.route_planner_api.feature.companies.member.dto.MemberRequest;

import jakarta.validation.Valid;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;




@RestController 
@RequestMapping("/api/v1/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService){
        this.companyService = companyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse createCompany (@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CompanyRequest request) {
        Long userId = Long.valueOf(jwt.getSubject());
        return companyService.createCompany(request, userId);
    }

    @GetMapping("/{companyId}")
    @ResponseStatus(HttpStatus.OK)
    public CompanyResponse getCompany(
        @PathVariable Long companyId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return companyService.getCompany(companyId, userId);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<CompanyResponse> getCompanies(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        return companyService.getCompanies(userId);
    }

    @PatchMapping("/{companyId}")
    @ResponseStatus(HttpStatus.OK)
    public CompanyResponse updateCompany(
            @AuthenticationPrincipal Jwt jwt, 
            @PathVariable Long companyId,
            @RequestBody @Valid UpdateCompanyRequest request){
        Long userId = Long.valueOf(jwt.getSubject());
        return companyService.updateCompany(userId, companyId, request);
    }
    
    @PostMapping("/{companyId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse addMember(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long companyId,
        @Valid @RequestBody MemberRequest request
    ) {       
        Long userId = Long.valueOf(jwt.getSubject()); 
        return companyService.addMember(userId, companyId, request);
    }
    
    
}
