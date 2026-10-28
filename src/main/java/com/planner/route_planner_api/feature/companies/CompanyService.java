package com.planner.route_planner_api.feature.companies;

import org.springframework.stereotype.Service;

import com.planner.route_planner_api.common.error.ResourceNotFoundException;
import com.planner.route_planner_api.feature.auth.User;
import com.planner.route_planner_api.feature.auth.UserRepository;
import com.planner.route_planner_api.feature.companies.createCompany.dto.CreateCompanyRequest;
import com.planner.route_planner_api.feature.companies.createCompany.dto.CreateCompanyResponse;

import jakarta.transaction.Transactional;

@Service 
public class CompanyService {

    private final CompanyMemberRepository companyMemberRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;

    public CompanyService(
            CompanyRepository companyRepository, 
            CompanyMemberRepository companyMemberRepository,
            UserRepository userRepository
        ){
        this.companyRepository = companyRepository;
        this.companyMemberRepository = companyMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public CreateCompanyResponse createCompany(CreateCompanyRequest request, Long userId){
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("userId", userId));
        Company company = companyRepository.save(new Company(request.name(), request.timezone()));
        companyMemberRepository.save(new CompanyMember(company, user, MemberRole.owner));
        return CreateCompanyResponse.from(company);
    }
}
