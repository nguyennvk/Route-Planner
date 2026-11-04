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

import com.planner.route_planner_api.common.error.InvalidTokenException;
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

    @Transactional
    public void revoke(String refreshToken){
        refreshTokenRepository.findByTokenHash(hash(refreshToken))
            .ifPresent(t -> refreshTokenRepository.revokeFamily(t.getFamilyId(), Instant.now()));

    }

    /** The user the new pair belongs to, plus the new RAW refresh token. */
    public record Rotation(User user, String refreshToken) {}

    /**
     * Exchange a refresh token for a new one (rotation). Each token works exactly once.
     * noRollbackFor: on reuse we revoke the family and THEN throw; without it the
     * exception would roll back the revoke and the protection would silently do nothing.
     */
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public Rotation rotate(String rawToken) {
        Instant now = Instant.now();

        // 1. Find it by hash. Unknown token → 401
        RefreshToken current = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Refresh token is invalid or expired"));

        // 2. Already used/revoked → someone replayed it. Assume theft: kill the whole login
        if (current.isRevoked()) {
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            throw new InvalidTokenException("Refresh token is invalid or expired");
        }

        // 3. Past its 30 days → 401, the user must log in again
        if (current.isExpired(now)) {
            throw new InvalidTokenException("Refresh token is invalid or expired");
        }

        // 4. Valid: retire it, and issue its replacement in the SAME family
        current.revoke(now);
        String next = issue(current.getUser(), current.getFamilyId());
        return new Rotation(current.getUser(), next);
    }
}
