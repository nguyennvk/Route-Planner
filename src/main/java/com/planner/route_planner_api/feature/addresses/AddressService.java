package com.planner.route_planner_api.feature.addresses;

import java.util.List;

import org.springframework.stereotype.Service;

import com.planner.route_planner_api.feature.addresses.address_api.AddressAPI;
import com.planner.route_planner_api.feature.addresses.dto.AddressSuggestionResponse;

@Service
public class AddressService {

    private static final int MAX_SUGGESTIONS = 4;

    private final AddressAPI addressAPI;
    private final AddressSuggestionSigner suggestionSigner;

    public AddressService(AddressAPI addressAPI, AddressSuggestionSigner suggestionSigner){
        this.addressAPI = addressAPI;
        this.suggestionSigner = suggestionSigner;
    }

    public List<AddressSuggestionResponse> suggest(Long userId, String rawAddress){
        String query = rawAddress.strip();
        return addressAPI.geocode(query, MAX_SUGGESTIONS).stream()
            .map(candidate -> AddressSuggestionResponse.from(
                candidate,
                // Only precise matches can be added directly, so only they get a token
                candidate.precise() ? suggestionSigner.sign(userId, query, candidate) : null))
            .toList();
    }
}
