package com.planner.route_planner_api.feature.customers.dto;

import java.time.Instant;

import com.planner.route_planner_api.feature.addresses.dto.CreateAddressResponse;
import com.planner.route_planner_api.feature.customers.Customer;

public record CustomerResponse(
    Long id,
    String name,
    String phone,
    String email,
    CreateAddressResponse defaultAddress,     // null when the customer has none
    Instant createdAt
) {
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
            customer.getId(),
            customer.getName(),
            customer.getPhone(),
            customer.getEmail(),
            customer.getDefaultAddress() == null ? null : CreateAddressResponse.from(customer.getDefaultAddress()),
            customer.getCreatedAt()
        );
    }
}
