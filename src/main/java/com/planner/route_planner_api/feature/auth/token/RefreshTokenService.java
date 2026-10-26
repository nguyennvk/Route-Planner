package com.planner.route_planner_api.feature.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planner.route_planner_api.feature.auth.User;

@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties props;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties props) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.props = props;
    }

    /** New login → new token family. Returns the RAW token; only its hash is stored. */
    @Transactional
    public String issue(User user) {
        return issue(user, UUID.randomUUID());
    }

    private String issue(User user, UUID familyId) {
        // 1. 32 random bytes → URL-safe text, e.g. "Xk3vP9..."
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        // 2. Store only the hash, with an expiry 30 days from now
        Instant expiresAt = Instant.now().plus(props.refreshTokenTtl());
        refreshTokenRepository.save(new RefreshToken(user, hash(raw), familyId, expiresAt));

        // 3. The raw token goes to the client and is never stored
        return raw;
    }

    public static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void revoke(String refreshToken){
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
            .ifPresent(t -> refreshTokenRepository.revokeFamily(t.getFamilyId(), Instant.now()));

    }
}
