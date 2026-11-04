package com.planner.route_planner_api.feature.customers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planner.route_planner_api.common.error.ConflictException;
import com.planner.route_planner_api.common.error.ResourceNotFoundException;
import com.planner.route_planner_api.common.web.PageResponse;
import com.planner.route_planner_api.feature.addresses.Address;
import com.planner.route_planner_api.feature.addresses.AddressRepository;
import com.planner.route_planner_api.feature.companies.CompanyAccess;
import com.planner.route_planner_api.feature.companies.CompanyMember;
import com.planner.route_planner_api.feature.customers.dto.CustomerRequest;
import com.planner.route_planner_api.feature.customers.dto.CustomerResponse;
import com.planner.route_planner_api.feature.customers.dto.UpdateCustomerRequest;

import jakarta.persistence.criteria.Predicate;

// Every customer endpoint is owner-only, reads included: customers are personal data (phone, email).
// Same rule as orders, which show the customer inside each order.
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;
    private final CompanyAccess companyAccess;

    public CustomerService(
            CustomerRepository customerRepository,
            AddressRepository addressRepository,
            CompanyAccess companyAccess
        ){
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.companyAccess = companyAccess;
    }

    @Transactional
    public CustomerResponse createCustomer(Long userId, Long companyId, CustomerRequest request){
        CompanyMember membership = companyAccess.requireMember(companyId, userId);
        companyAccess.requireOwner(membership);

        String email = clean(request.email());
        if (email != null && customerRepository.existsByCompanyIdAndEmail(companyId, email)) {
            throw new ConflictException("Another customer of this company already uses this email");   // 409
        }
        Address defaultAddress = request.defaultAddressId() == null
            ? null
            : findCompanyAddress(companyId, request.defaultAddressId());

        Customer customer = new Customer(membership.getCompany(), request.name().strip(),
            clean(request.phone()), email, defaultAddress);
        return CustomerResponse.from(customerRepository.save(customer));
    }

    // phone: exact match, to find a returning customer when they call.
    //   In a URL, "+" means a space: the client must send it as %2B (?phone=%2B14165550123).
    // search: case-insensitive "contains" on the name
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> listCustomers(
            Long userId, Long companyId, String phone, String search, int page, int size){
        CompanyMember membership = companyAccess.requireMember(companyId, userId);
        companyAccess.requireOwner(membership);

        // Build the WHERE clause from only the filters the client sent
        Specification<Customer> filters = (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.equal(root.get("company").get("id"), companyId));
            if (phone != null && !phone.isBlank()) {
                conditions.add(cb.equal(root.get("phone"), phone.strip()));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.strip().toLowerCase(Locale.ROOT) + "%";
                conditions.add(cb.like(cb.lower(root.get("name")), pattern));
            }
            return cb.and(conditions.toArray(new Predicate[0]));
        };

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("name", "id"));
        return PageResponse.from(customerRepository.findAll(filters, pageRequest).map(CustomerResponse::from));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(Long userId, Long customerId){
        return CustomerResponse.from(findCustomerForOwner(customerId, userId));
    }

    @Transactional
    public CustomerResponse updateCustomer(Long userId, Long customerId, UpdateCustomerRequest request){
        Customer customer = findCustomerForOwner(customerId, userId);
        Long companyId = customer.getCompany().getId();

        // PATCH: only touch the fields the client sent
        // No save() needed: Hibernate sees the change and runs the UPDATE when the transaction commits
        if (request.name() != null) {
            customer.rename(request.name().strip());
        }
        if (request.phone() != null) {
            customer.changePhone(request.phone().strip());
        }
        if (request.email() != null) {
            String email = clean(request.email());
            if (email != null && customerRepository.existsByCompanyIdAndEmailAndIdNot(companyId, email, customerId)) {
                throw new ConflictException("Another customer of this company already uses this email");
            }
            customer.changeEmail(email);
        }
        if (request.defaultAddressId() != null) {
            customer.changeDefaultAddress(findCompanyAddress(companyId, request.defaultAddressId()));
        }
        return CustomerResponse.from(customer);
    }

    // Customer exists AND the user owns its company; a non-member gets 404, so ids in other companies stay hidden
    private Customer findCustomerForOwner(Long customerId, Long userId){
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        CompanyMember membership = companyAccess.findActiveMember(customer.getCompany().getId(), userId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        companyAccess.requireOwner(membership);
        return customer;
    }

    private Address findCompanyAddress(Long companyId, Long addressId){
        return addressRepository.findByIdAndCompanyId(addressId, companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Address", addressId));
    }

    // Optional text: trimmed, and "" or "   " is stored as null
    private static String clean(String value){
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
