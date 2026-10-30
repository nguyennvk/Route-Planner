package com.planner.route_planner_api.feature.customers;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.planner.route_planner_api.common.web.PageResponse;
import com.planner.route_planner_api.feature.customers.dto.CustomerRequest;
import com.planner.route_planner_api.feature.customers.dto.CustomerResponse;
import com.planner.route_planner_api.feature.customers.dto.UpdateCustomerRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

// Mapped at /api/v1 because the routes mix /companies/{id}/customers (create, list)
// and /customers/{id} (one customer, whose company is known from the customer itself)
@RestController
@RequestMapping("/api/v1")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService){
        this.customerService = customerService;
    }

    @PostMapping("/companies/{companyId}/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse createCustomer(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long companyId,
        @Valid @RequestBody CustomerRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return customerService.createCustomer(userId, companyId, request);
    }

    @GetMapping("/companies/{companyId}/customers")
    @ResponseStatus(HttpStatus.OK)
    public PageResponse<CustomerResponse> listCustomers(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long companyId,
        @RequestParam(required = false) String phone,       // send "+" as %2B
        @RequestParam(required = false) @Size(max = 254) String search,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return customerService.listCustomers(userId, companyId, phone, search, page, size);
    }

    @GetMapping("/customers/{customerId}")
    @ResponseStatus(HttpStatus.OK)
    public CustomerResponse getCustomer(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long customerId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return customerService.getCustomer(userId, customerId);
    }

    @PatchMapping("/customers/{customerId}")
    @ResponseStatus(HttpStatus.OK)
    public CustomerResponse updateCustomer(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long customerId,
        @Valid @RequestBody UpdateCustomerRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return customerService.updateCustomer(userId, customerId, request);
    }
}
