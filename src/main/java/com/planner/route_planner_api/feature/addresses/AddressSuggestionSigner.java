package com.planner.route_planner_api.feature.addresses;

import java.time.Duration;
import java.time.Instant;

import javax.crypto.SecretKey;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import com.planner.route_planner_api.common.error.BadRequestException;
import com.planner.route_planner_api.feature.addresses.address_api.GeocodeCandidate;

// Signs each geocoding suggestion before it goes to the client, and checks the signature when it comes back.
// The client can carry the suggestion but can't change it: any edit breaks the signature.
// Same idea (and same secret key) as the access tokens, but a different issuer, so the two can never be swapped:
// the resource server only accepts issuer "route-planner-api", and verify() below only accepts ISSUER.
@Component
public class AddressSuggestionSigner {

    private static final String ISSUER = "route-planner-api/address-suggestion";
    private static final Duration TTL = Duration.ofMinutes(15);      // long enough to pick one and click "Add"

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder decoder;

    public AddressSuggestionSigner(JwtEncoder jwtEncoder, SecretKey jwtSecretKey) {
        this.jwtEncoder = jwtEncoder;
        // Built here, not as a @Bean: the resource server expects exactly one JwtDecoder bean (the access-token one)
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));     // signature + expiry + issuer
        this.decoder = decoder;
    }

    public String sign(Long userId, String rawText, GeocodeCandidate candidate) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(ISSUER)
            .issuedAt(now)
            .expiresAt(now.plus(TTL))
            .claim("userId", userId)        // only the user who asked may use it
            .claim("rawText", rawText)
            .claim("formattedAddress", candidate.formattedAddress())
            .claim("lat", candidate.latitude())
            .claim("lng", candidate.longitude())
            .claim("accuracy", candidate.accuracy())
            .claim("source", candidate.source())
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // For the create-address endpoint: returns the values the server signed, never what the request body claims
    public SignedAddress verify(String token, Long userId) {
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtException e) {
            // Edited, fake, expired, or an access token sent in its place
            throw new BadRequestException("Address suggestion is invalid or expired. Search for the address again.");
        }
        if (!userId.equals(number(jwt, "userId").longValue())) {
            throw new BadRequestException("Address suggestion is invalid or expired. Search for the address again.");
        }
        return new SignedAddress(
            jwt.getClaimAsString("rawText"),
            jwt.getClaimAsString("formattedAddress"),
            number(jwt, "lat").doubleValue(),
            number(jwt, "lng").doubleValue(),
            number(jwt, "accuracy").doubleValue(),
            jwt.getClaimAsString("source"));
    }

    // JSON numbers come back as Long or Double depending on the value (1 vs 0.9), so read them as Number
    private static Number number(Jwt jwt, String claim) {
        return jwt.getClaim(claim);
    }

    public record SignedAddress(
        String rawText,
        String formattedAddress,
        double latitude,
        double longitude,
        double accuracy,
        String source
    ) {}
}
