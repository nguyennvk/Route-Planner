package com.planner.route_planner_api.common.validation;

import com.planner.route_planner_api.common.error.BadRequestException;
import com.planner.route_planner_api.feature.addresses.dto.CreateAddressRequest;

public class PinValidation {
    // Manual pins must land in Canada (the geocoder only searches Canada too).
    // A rough box around the country: catches 0,0 and swapped lat/lng, not every point outside the border.
    private static final double MIN_LATITUDE = 41.6, MAX_LATITUDE = 83.2;
    private static final double MIN_LONGITUDE = -141.1, MAX_LONGITUDE = -52.6;

    public static void requirePin(CreateAddressRequest request) {
        if (request.rawAddress() == null || request.rawAddress().isBlank()
                || request.latitude() == null || request.longitude() == null) {
            throw new BadRequestException("Send a suggestion token, or rawAddress with latitude and longitude");
        }
        double lat = request.latitude();
        double lng = request.longitude();
        if (lat < MIN_LATITUDE || lat > MAX_LATITUDE || lng < MIN_LONGITUDE || lng > MAX_LONGITUDE) {
            throw new BadRequestException("The pin must be in Canada");
        }
    }
    public static void requireInServiceArea(double latitude, double longitude) {
        if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE
                || longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            throw new BadRequestException("The pin must be in Canada");
        }
    }

}
