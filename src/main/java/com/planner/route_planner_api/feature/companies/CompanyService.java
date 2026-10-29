package com.planner.route_planner_api.feature.companies;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planner.route_planner_api.common.error.ResourceNotFoundException;
import com.planner.route_planner_api.feature.addresses.Address;
import com.planner.route_planner_api.feature.addresses.AddressRepository;
import com.planner.route_planner_api.feature.addresses.AddressSuggestionSigner;
import com.planner.route_planner_api.feature.addresses.AddressSuggestionSigner.SignedAddress;
import com.planner.route_planner_api.feature.addresses.dto.CreateAddressRequest;
import com.planner.route_planner_api.feature.addresses.dto.CreateAddressResponse;
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

// Access rules, same for every method below:
//   not an active member -> 404 (outsiders can't tell which company ids exist)
//   member but not owner -> 403
@Service
public class CompanyService {

    // Manual pins must land in Canada (the geocoder only searches Canada too).
    // A rough box around the country: catches 0,0 and swapped lat/lng, not every point outside the border.
    private static final double MIN_LATITUDE = 41.6, MAX_LATITUDE = 83.2;
    private static final double MIN_LONGITUDE = -141.1, MAX_LONGITUDE = -52.6;

    private final CompanyMemberRepository companyMemberRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final CompanyAccess companyAccess;
    private final AddressSuggestionSigner suggestionSigner;

    public CompanyService(
            CompanyRepository companyRepository,
            CompanyMemberRepository companyMemberRepository,
            UserRepository userRepository,
            AddressRepository addressRepository,
            CompanyAccess companyAccess,
            AddressSuggestionSigner suggestionSigner
        ){
        this.companyRepository = companyRepository;
        this.companyMemberRepository = companyMemberRepository;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.companyAccess = companyAccess;
        this.suggestionSigner = suggestionSigner;
    }

    @Transactional
    public CompanyResponse createCompany(CompanyRequest request, Long userId){
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Company company = companyRepository.save(new Company(request.name(), request.timezone()));
        companyMemberRepository.save(new CompanyMember(company, user, MemberRole.owner));
        return CompanyResponse.from(company);
    }

    @Transactional(readOnly = true)
    public CompanyResponse getCompany(Long userId, Long companyId){
        CompanyMember membership = companyAccess.requireMember(companyId, userId);
        return CompanyResponse.from(membership.getCompany());
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> getCompanies(Long userId){
        List<Company> companies = companyRepository.findAllByUserId(userId);
        return companies.stream().map((company) -> CompanyResponse.from(company)).toList();
    }

    @Transactional
    public CompanyResponse updateCompany(Long userId, Long companyId, UpdateCompanyRequest request){
        CompanyMember membership = companyAccess.requireMember(companyId, userId);
        companyAccess.requireOwner(membership);

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
        CompanyMember membership = companyAccess.requireMember(companyId, userId);
        companyAccess.requireOwner(membership);

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail)
            .orElseThrow(() -> new ResourceNotFoundException("User", normalizedEmail));

        if (companyMemberRepository.existsById(new CompanyMemberId(companyId, user.getId()))) {
            throw new ConflictException("User is already a member of this company");   // 409
        }
        CompanyMember newMember = companyMemberRepository.saveAndFlush(
            new CompanyMember(membership.getCompany(), user, request.role()));
        return MemberResponse.from(newMember);
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> getMembers(Long userId, Long companyId, MemberRole role){
        CompanyMember membership = companyAccess.requireMember(companyId, userId);
        companyAccess.requireOwner(membership);

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
        CompanyMember membership = companyAccess.requireMember(companyId, userId);
        companyAccess.requireOwner(membership);

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
        CompanyMember membership = companyAccess.requireMember(companyId, userId);
        companyAccess.requireOwner(membership);

        if (userId.equals(memberId)) {
            throw new BadRequestException("You cannot remove yourself from the company");
        }
        CompanyMember member = companyMemberRepository.findById(new CompanyMemberId(companyId, memberId))
            .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
        member.deactivate();
    }

    @Transactional
    public CreateAddressResponse postAddress(Long userId, Long companyId, CreateAddressRequest request){
        CompanyMember membership = companyAccess.requireMember(companyId, userId);
        companyAccess.requireOwner(membership);
        Company company = membership.getCompany();

        Address address;
        if (request.token() != null) {
            // From a suggestion: use only the values the server signed. The body's lat/lng are ignored,
            // so a client can't attach made-up coordinates to a real address.
            SignedAddress signed = suggestionSigner.verify(request.token(), userId);
            address = Address.geocoded(company, signed.rawText(), signed.formattedAddress(),
                signed.latitude(), signed.longitude(), signed.accuracy());
        } else {
            // From a map pin: the user is the source, so just check the point is sensible
            requirePin(request);
            address = Address.pinned(company, request.rawAddress().strip(), request.latitude(), request.longitude());
        }
        return CreateAddressResponse.from(addressRepository.save(address));
    }

    private static void requirePin(CreateAddressRequest request) {
        if (request.rawAddress() == null || request.rawAddress().isBlank()
                || request.latitude() == null || request.longitude() == null) {
            throw new BadRequestException("Send a suggestion token, or rawAddress with latitude and longitude");
        }
        double lat = request.latitude();
        double lng = request.longitude();
        if (lat < MIN_LATITUDE || lat > MAX_LATITUDE || lng < MIN_LONGITUDE || lng > MAX_LONGITUDE) {
            throw new BadRequestException("The pin must be in Canada");
        }
    }
}
