package com.planner.route_planner_api.feature.auth.register.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Email @Size(max = 254)
    String email, 

    @NotBlank @Size(min = 8, max=72)
    String password, 

    @NotBlank
    String fullname,
    
    @NotBlank 
    String phone
) {
    @Override
    public String toString() {                  // never print the password
        return "RegisterRequest[email=" + email + ", password=***, fullname=" + fullname + ", phone=" + phone + "]";
    }
}
