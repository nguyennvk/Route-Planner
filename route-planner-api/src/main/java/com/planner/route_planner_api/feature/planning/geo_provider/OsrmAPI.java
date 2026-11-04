package com.planner.route_planner_api.feature.planning.geo_provider;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.planner.route_planner_api.common.error.ServiceUnavailableException;
import com.planner.route_planner_api.feature.planning.optimizer.PlannedVehicle;

@Component 
public class OsrmAPI {
    
    private static final Logger log = LoggerFactory.getLogger(OsrmAPI.class);

    private final RestClient restClient;


    public OsrmAPI(OsrmProperties props){
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(20));
        requestFactory.enableCompression(false);

        // Built once and reused; every request starts from baseUrl
        this.restClient = RestClient.builder()
            .baseUrl(props.baseUrl())
            .requestFactory(requestFactory)
            .build();

    }

    public OsrmTableResponse generateDistanceTable(Double[] longitudes, Double[] latitudes){
        if (longitudes.length != latitudes.length) {
            throw new IllegalArgumentException("longitudes and latitudes must have the same length");
        }

        StringJoiner coordinates = new StringJoiner(";");
        for (int i = 0; i < longitudes.length; i++) {
            coordinates.add(longitudes[i] + "," + latitudes[i]);
        }
        OsrmTableResponse response;
        try {
            response = restClient.get()
                .uri("/table/v1/driving/" + coordinates + "?annotations=duration,distance")
                .retrieve()
                .body(OsrmTableResponse.class);      // Jackson turns the JSON into your record
        } catch (RestClientException e) {
            // OSRM rejected the request, is down, or timed out
            log.warn("OSRM table request failed", e);
            throw new ServiceUnavailableException("Travel times are unavailable right now. Try again later.");
        }

        // OSRM reports problems in "code" too; "Ok" is the only success value
        if (response == null || !"Ok".equals(response.code())) {
            throw new ServiceUnavailableException("Travel times are unavailable right now. Try again later.");
        }
        return response;

    }

    public String getRoute(PlannedVehicle vehicle){
        Double[][] locations = vehicle.extractRouteLocations();
        Double[] latitudes = locations[0];
        Double[] longitudes = locations[1];
        if (longitudes.length != latitudes.length) {
            throw new IllegalArgumentException("longitudes and latitudes must have the same length");
        }

        StringJoiner coordinates = new StringJoiner(";");
        for (int i = 0; i < longitudes.length; i++) {
            coordinates.add(longitudes[i] + "," + latitudes[i]);
        }
        OsrmRouteResponse response;
        try {
            response = restClient.get()
                .uri("/route/v1/driving/" + coordinates + "?overview=full&geometries=polyline")
                .retrieve()
                .body(OsrmRouteResponse.class);      // Jackson turns the JSON into your record
        } catch (RestClientException e) {
            // OSRM rejected the request, is down, or timed out
            log.warn("OSRM table request failed", e);
            throw new ServiceUnavailableException("Precise route presentation is unavailable right now. Try again later.");
        }

        // OSRM reports problems in "code" too; "Ok" is the only success value
        if (response == null || !"Ok".equals(response.code())) {
            throw new ServiceUnavailableException("Precise route presentation is unavailable right now. Try again later.");
        }
        return response.routes().get(0).geometry();
    }

}
