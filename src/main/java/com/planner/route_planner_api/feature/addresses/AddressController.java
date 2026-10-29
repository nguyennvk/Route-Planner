package com.planner.route_planner_api.feature.addresses;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.planner.route_planner_api.feature.addresses.dto.AddressSuggestionResponse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/api/v1")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService){
        this.addressService = addressService;
    }

    @GetMapping("/addresses/suggestion")
    @ResponseStatus(HttpStatus.OK)
    public List<AddressSuggestionResponse> suggest(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam @NotBlank @Size(max = 512) String rawAddress      // 512 = addresses.raw_text limit
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return addressService.suggest(userId, rawAddress);
    }

    


}
