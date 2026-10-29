package com.planner.route_planner_api.feature.addresses;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planner.route_planner_api.common.error.ResourceNotFoundException;
import com.planner.route_planner_api.common.validation.PinValidation;
import com.planner.route_planner_api.common.web.PageResponse;
import com.planner.route_planner_api.feature.addresses.address_api.AddressAPI;
import com.planner.route_planner_api.feature.addresses.dto.AddressSuggestionResponse;
import com.planner.route_planner_api.feature.addresses.dto.CreateAddressResponse;
import com.planner.route_planner_api.feature.addresses.dto.PinRequest;
import com.planner.route_planner_api.feature.companies.Company;
import com.planner.route_planner_api.feature.companies.CompanyAccess;
import com.planner.route_planner_api.feature.companies.CompanyMember;

import jakarta.persistence.criteria.Predicate;

@Service
public class AddressService {

    private static final int MAX_SUGGESTIONS = 4;

    private final AddressAPI addressAPI;
    private final AddressSuggestionSigner suggestionSigner;
    private final AddressRepository addressRepository;
    private final CompanyAccess companyAccess;

    public AddressService(
            AddressAPI addressAPI,
            AddressSuggestionSigner suggestionSigner,
            AddressRepository addressRepository,
            CompanyAccess companyAccess
        ){
        this.addressAPI = addressAPI;
        this.suggestionSigner = suggestionSigner;
        this.addressRepository = addressRepository;
        this.companyAccess = companyAccess;
    }

    public List<AddressSuggestionResponse> suggest(Long userId, String rawAddress){
        String query = rawAddress.strip();
        return addressAPI.geocode(query, MAX_SUGGESTIONS).stream()
            .map(candidate -> AddressSuggestionResponse.from(
                candidate,
                // Only precise matches can be added directly, so only they get a token
                candidate.precise() ? suggestionSigner.sign(userId, query, candidate) : null))
            .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<CreateAddressResponse> listAddresses(
            Long userId, Long companyId, String search, int page, int size){
        companyAccess.requireMember(companyId, userId);

        // Build the WHERE clause from only the filters the client sent
        Specification<Address> filters = (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.equal(root.get("company").get("id"), companyId));
            if (search != null && !search.isBlank()) {
                // Case-insensitive "contains" on what the user typed or the geocoder's formatted address
                String pattern = "%" + search.strip().toLowerCase(Locale.ROOT) + "%";
                conditions.add(cb.or(
                    cb.like(cb.lower(root.get("rawAddress")), pattern),
                    cb.like(cb.lower(root.get("normalizedAddress")), pattern)));
            }
            return cb.and(conditions.toArray(new Predicate[0]));
        };

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("id"));
        return PageResponse.from(addressRepository.findAll(filters, pageRequest).map(CreateAddressResponse::from));
    }

    @Transactional(readOnly = true)
    public CreateAddressResponse getAddress(Long userId, Long addressId){
        return CreateAddressResponse.from(findAddressForMember(addressId, userId));
    }

    // Address exists AND the user is an active member of its company; otherwise 404 either way,
    // so users can't probe which address ids exist in other companies
    private Address findAddressForMember(Long addressId, Long userId){
        Address address = addressRepository.findById(addressId)
            .orElseThrow(() -> new ResourceNotFoundException("Address", null));
        companyAccess.findActiveMember(address.getCompany().getId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Address", null));
        return address;
    }

    @Transactional 
    public CreateAddressResponse changePin(Long userId, Long addressId, PinRequest request){
        PinValidation.requireInServiceArea(request.latitude(), request.longitude());
        Address address = findAddressForOwner(addressId, userId);
        address.updatePin(request.latitude(), request.longitude());
        return CreateAddressResponse.from(address);
    }

    private Address findAddressForOwner(Long addressId, Long userId){
        Address address = addressRepository.findById(addressId)
            .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));
        CompanyMember membership = companyAccess.findActiveMember(address.getCompany().getId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));
        companyAccess.requireOwner(membership);
        return address;
    }
}
