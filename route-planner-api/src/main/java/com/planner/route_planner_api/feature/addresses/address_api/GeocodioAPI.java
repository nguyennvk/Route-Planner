package com.planner.route_planner_api.feature.addresses.address_api;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.planner.route_planner_api.common.error.ServiceUnavailableException;

import io.github.deansg.jeocodio.GeocodioClient;
import io.github.deansg.jeocodio.GeocodioClientOptions;
import io.github.deansg.jeocodio.GeocodioStatusCodeException;
import io.github.deansg.jeocodio.models.GeocodingRequest;
import io.github.deansg.jeocodio.models.GeocodingRequestBuilder;
import io.github.deansg.jeocodio.models.GeocodingResponse;
import io.github.deansg.jeocodio.models.GeocodingResult;

@Component
public class GeocodioAPI implements AddressAPI {

    private static final Logger log = LoggerFactory.getLogger(GeocodioAPI.class);

    private static final String BASE_URL = "https://api.geocod.io/v2/";   // the library defaults to the older v1.7
    private static final String COUNTRY = "Canada";                         // only Canadian addresses for now
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    // Delivery needs the building itself; anything vaguer (street_center, place, ...) goes to a manual pin
    private static final Set<String> PRECISE_TYPES = Set.of("rooftop", "point", "range_interpolation");
    private static final double MIN_ACCURACY = 0.85;

    private final GeocodioClient client;

    public GeocodioAPI(@Value("${app.geocodio.api-key}") String apiKey) {
        // The library's default HttpClient has no timeout: a stuck connection would block the request forever
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
        this.client = new GeocodioClient(httpClient, apiKey, new GeocodioClientOptions(true, BASE_URL));
    }

    @Override
    public List<GeocodeCandidate> geocode(String rawAddress, int limit) {
        GeocodingRequest request = GeocodingRequestBuilder.builder()
            .q(rawAddress)
            .country(COUNTRY)
            .limit(limit)
            .build();

        GeocodingResponse response;
        try {
            response = client.geocodeAsync(request)
                .orTimeout(TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                .join();
        } catch (CompletionException e) {
            // join() wraps every failure; the real reason is the cause
            if (e.getCause() instanceof GeocodioStatusCodeException status && status.statusCode() == 422) {
                return List.of();       // Geocodio couldn't understand the text: same as "nothing found"
            }
            // 403 bad key, 429 daily quota used up, 5xx Geocodio down, or a timeout/network error
            log.warn("Geocodio request failed", e.getCause());
            throw new ServiceUnavailableException("Address lookup is unavailable right now. Drop a pin on the map instead.");
        }

        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results().stream()
            .filter(r -> r.location() != null && r.location().lat() != null && r.location().lng() != null)
            .map(GeocodioAPI::toCandidate)
            .toList();
    }

    private static GeocodeCandidate toCandidate(GeocodingResult r) {
        double accuracy = r.accuracy() == null ? 0 : r.accuracy();
        boolean precise = PRECISE_TYPES.contains(r.accuracyType()) && accuracy >= MIN_ACCURACY;
        return new GeocodeCandidate(
            r.formattedAddress(),
            r.location().lat(),
            r.location().lng(),
            accuracy,
            precise,
            "geocodio");
    }
}
