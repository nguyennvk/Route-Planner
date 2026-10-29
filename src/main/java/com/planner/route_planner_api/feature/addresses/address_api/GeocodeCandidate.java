package com.planner.route_planner_api.feature.addresses.address_api;

// One possible match for the typed address, in provider-neutral terms
public record GeocodeCandidate(
    String formattedAddress,
    double latitude,
    double longitude,
    double accuracy,        // 0..1, how sure the provider is about the match
    boolean precise,        // points at the building itself; false -> the user must drop a pin
    String source           // goes into addresses.geocode_source, e.g. "geocodio"
) {
}
