package com.planner.route_planner_api.feature.customers.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
    @NotBlank @Size(max = 254)
    String name,

    // Optional; E.164 like the customers.phone CHECK: "+" then country code and number, no spaces
    @Pattern(regexp = "^\\+[1-9][0-9]{1,14}$", message = "must be in E.164 format, e.g. +14165550123")
    String phone,

    // Optional; unique per company
    @Email @Size(max = 254)
    String email,

    // Optional; must be an address of the same company
    Long defaultAddressId
) {}
