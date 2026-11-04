package com.planner.route_planner_api.feature.customers.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// PATCH: every field is optional, null means "leave unchanged"
public record UpdateCustomerRequest(
    // @Pattern instead of @NotBlank: null must stay allowed ("unchanged"), but "" or "   " must not
    @Size(max = 254) @Pattern(regexp = ".*\\S.*", message = "must not be blank")
    String name,

    @Pattern(regexp = "^\\+[1-9][0-9]{1,14}$", message = "must be in E.164 format, e.g. +14165550123")
    String phone,

    @Email @Size(max = 254)
    String email,

    Long defaultAddressId
) {}
