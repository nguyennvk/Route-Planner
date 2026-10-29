package com.planner.route_planner_api.feature.addresses;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.planner.route_planner_api.common.web.PageResponse;
import com.planner.route_planner_api.feature.addresses.dto.AddressSuggestionResponse;
import com.planner.route_planner_api.feature.addresses.dto.CreateAddressResponse;
import com.planner.route_planner_api.feature.addresses.dto.PinRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Mapped at /api/v1 because the routes mix /companies/{id}/addresses (list)
// and /addresses/{id} (one address, whose company is known from the address itself).
// Creating an address is POST /companies/{id}/addresses in CompanyController.
@RestController
@RequestMapping("/api/v1")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService){
        this.addressService = addressService;
    }

    // "suggestion" is a fixed path, so Spring matches it before /addresses/{addressId}
    @GetMapping("/addresses/suggestion")
    @ResponseStatus(HttpStatus.OK)
    public List<AddressSuggestionResponse> suggest(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam @NotBlank @Size(max = 512) String rawAddress      // 512 = addresses.raw_address limit
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return addressService.suggest(userId, rawAddress);
    }

    @GetMapping("/companies/{companyId}/addresses")
    @ResponseStatus(HttpStatus.OK)
    public PageResponse<CreateAddressResponse> listAddresses(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long companyId,
        @RequestParam(required = false) @Size(max = 512) String search,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return addressService.listAddresses(userId, companyId, search, page, size);
    }

    @GetMapping("/addresses/{addressId}")
    @ResponseStatus(HttpStatus.OK)
    public CreateAddressResponse getAddress(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long addressId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return addressService.getAddress(userId, addressId);
    }

    @PatchMapping("/addresses/{addressId}/location")
    @ResponseStatus(HttpStatus.OK)
    public CreateAddressResponse changePin(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long addressId,
        @Valid @RequestBody PinRequest request
    ){
        Long userId = Long.valueOf(jwt.getSubject());
        return addressService.changePin(userId, addressId, request);
    }
}
