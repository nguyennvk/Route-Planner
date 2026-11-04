package com.planner.route_planner_api.feature.addresses.dto;

import com.planner.route_planner_api.feature.addresses.address_api.GeocodeCandidate;

// formattedAddress/latitude/longitude are for showing the option (list, map preview).
// To create the address the client sends back only the token; the server ignores everything else.
public record AddressSuggestionResponse(
    String formattedAddress,
    double latitude,
    double longitude,
    boolean precise,        // false -> can't be added as is; open the pin map at this point instead
    String token,           // null when not precise
    String geocodeSource
) {
    public static AddressSuggestionResponse from(GeocodeCandidate candidate, String token) {
        return new AddressSuggestionResponse(
            candidate.formattedAddress(),
            candidate.latitude(),
            candidate.longitude(),
            candidate.precise(),
            token,
            candidate.source()
        );
    }
}
