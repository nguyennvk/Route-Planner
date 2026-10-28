package com.planner.route_planner_api.feature.companies;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planner.route_planner_api.common.error.ForbiddenException;
import com.planner.route_planner_api.common.error.ResourceNotFoundException;
import com.planner.route_planner_api.feature.auth.User;
import com.planner.route_planner_api.feature.auth.UserRepository;
import com.planner.route_planner_api.feature.companies.company.dto.CompanyResponse;
import com.planner.route_planner_api.feature.companies.company.dto.CompanyRequest;
import com.planner.route_planner_api.feature.companies.company.dto.UpdateCompanyRequest;
import com.planner.route_planner_api.feature.companies.member.dto.MemberRequest;
import com.planner.route_planner_api.feature.companies.member.dto.MemberResponse;
import com.planner.route_planner_api.feature.companies.member.dto.UpdateMemberRequest;
import com.planner.route_planner_api.common.error.BadRequestException;
import com.planner.route_planner_api.common.error.ConflictException;

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
    public CompanyResponse createCompany(CompanyRequest request, Long userId){
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Company company = companyRepository.save(new Company(request.name(), request.timezone()));
        companyMemberRepository.save(new CompanyMember(company, user, MemberRole.owner));
        return CompanyResponse.from(company);
    }

    @Transactional 
    public CompanyResponse getCompany(Long userId, Long companyId){
        Company company = companyRepository.findByIdForMember(companyId, userId)
                            .orElseThrow(() -> new ForbiddenException("Cannot perform the action"));
        return CompanyResponse.from(company); 
    }

    @Transactional
    public List<CompanyResponse> getCompanies(Long userId){
        List<Company> companies = companyRepository.findAllByUserId(userId);
        return companies.stream().map((company) -> CompanyResponse.from(company)).toList();
    }

    @Transactional
    public CompanyResponse updateCompany(Long userId, Long companyId, UpdateCompanyRequest request){
        // Non-members get 404 so they can't learn which company ids exist
        CompanyMember membership = companyMemberRepository.findActiveMembership(companyId, userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        // Members who aren't the owner get 403
        if (membership.getRole() != MemberRole.owner) {
            throw new ForbiddenException("Only the owner can update the company");
        }

        Company company = membership.getCompany();

        // PATCH: only touch the fields the client sent
        // No save() needed: Hibernate sees the change and runs the UPDATE when the transaction commits
        if (request.name() != null) {
            company.rename(request.name());
        }
        if (request.timezone() != null) {
            company.changeTimezone(request.timezone());
        }
        return CompanyResponse.from(company);
    }

    @Transactional 
    public MemberResponse addMember(Long userId, Long companyId, MemberRequest request){
        CompanyMember companyMember = companyMemberRepository.findActiveMembership(companyId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));
        if (!MemberRole.owner.equals(companyMember.getRole())){
            throw new ForbiddenException("Cannot perform the action");
        }
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new ResourceNotFoundException("User", normalizedEmail));
        
        Company company = companyMember.getCompany();
        if (companyMemberRepository.existsById(new CompanyMemberId(companyId, user.getId()))) {
            throw new ConflictException("User is already a member of this company");   // 409
        }
        CompanyMember newMember = companyMemberRepository.saveAndFlush(new CompanyMember(company, user, request.role()));
        return MemberResponse.from(newMember);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> getMembers(Long userId, Long companyId, MemberRole role){
        if (!companyMemberRepository.existsByCompanyIdAndUserIdAndRoleAndActiveTrue(companyId, userId, MemberRole.owner)){
            throw new ForbiddenException("Cannot perform the action");
        }
        List<CompanyMember> members;
        if (role == null){
            members = companyMemberRepository.findActiveMembers(companyId);
        }
        else{
            members = companyMemberRepository.findActiveMembersByRole(companyId, role);
        }
        return members.stream().map(MemberResponse::from).toList();

    }

    @Transactional
    public MemberResponse updateMember(Long userId, Long companyId, Long memberId, UpdateMemberRequest request){
        // Non-members get 404, non-owners get 403 (same rules as updateCompany)
        CompanyMember membership = companyMemberRepository.findActiveMembership(companyId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));
        if (membership.getRole() != MemberRole.owner) {
            throw new ForbiddenException("Only the owner can update members");
        }

        // Owners can't demote or deactivate themselves, so every company always keeps at least one active owner
        if (userId.equals(memberId)) {
            throw new BadRequestException("You cannot change your own membership");
        }

        // findById, not findActiveMembership: an inactive member must be findable so they can be reactivated
        CompanyMember member = companyMemberRepository.findById(new CompanyMemberId(companyId, memberId))
            .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));

        // PATCH: only touch the fields the client sent
        // No save() needed: Hibernate sees the change and runs the UPDATE when the transaction commits
        if (request.role() != null) {
            member.changeRole(request.role());
        }
        if (request.active() != null) {
            if (request.active()) {
                member.activate();
            } else {
                member.deactivate();
            }
        }
        return MemberResponse.from(member);
    }

    @Transactional 
    public void deleteMember(Long userId, Long companyId, Long memberId){
        if (userId.equals(memberId)) {
            throw new BadRequestException("You cannot remove yourself from the company");
        }
        if (!companyMemberRepository.existsByCompanyIdAndUserIdAndRoleAndActiveTrue(companyId, userId, MemberRole.owner)){
            throw new ForbiddenException("Cannot perform the action");
        }
        CompanyMember member = companyMemberRepository.findById(new CompanyMemberId(companyId, memberId))
            .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
        member.deactivate();
    }
}
