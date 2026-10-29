package com.planner.route_planner_api.feature.addresses.address_api;

import java.util.List;

// One implementation per geocoding provider, so the rest of the app never sees a provider's own types
public interface AddressAPI {

    // Best match first; empty when nothing was found
    List<GeocodeCandidate> geocode(String rawAddress, int limit);
}
