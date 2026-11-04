package com.planner.route_planner_api.feature.addresses.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

// Two ways to create an address, one request shape:
//   from a suggestion:  { "token": "..." }
//       everything (text, lat/lng, confidence, source) comes from the signed token; other fields are ignored
//   from a map pin:     { "rawAddress": "45 Adamson St", "latitude": 43.59, "longitude": -79.64 }
// No geocodeSource or geocodeConfidence here: the server decides those, never the client.
public record CreateAddressRequest(
    String token,

    @Size(max = 512)
    String rawAddress,

    // Double, not double: null means "not sent", which is how the token case looks
    @DecimalMin("-90.0") @DecimalMax("90.0")
    Double latitude,

    @DecimalMin("-180.0") @DecimalMax("180.0")
    Double longitude
) {}
