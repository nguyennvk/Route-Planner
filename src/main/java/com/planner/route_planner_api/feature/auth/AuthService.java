package com.planner.route_planner_api.feature.auth;

import com.planner.route_planner_api.feature.auth.token.JwtProperties;
import com.planner.route_planner_api.feature.auth.token.RefreshTokenRepository;

import java.time.Instant;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.planner.route_planner_api.common.error.BadCredentialException;
import com.planner.route_planner_api.common.error.ConflictException;
import com.planner.route_planner_api.common.error.IllegalActionException;
import com.planner.route_planner_api.common.error.ResourceNotFoundException;
import com.planner.route_planner_api.feature.auth.login.dto.LoginResponse;
import com.planner.route_planner_api.feature.auth.me.dto.MeResponse;
import com.planner.route_planner_api.feature.auth.login.dto.LoginRequest;
import com.planner.route_planner_api.feature.auth.register.dto.RegisterRequest;
import com.planner.route_planner_api.feature.auth.register.dto.RegisterResponse;
import com.planner.route_planner_api.feature.auth.token.JwtService;
import com.planner.route_planner_api.feature.auth.token.RefreshToken;
import com.planner.route_planner_api.feature.auth.token.RefreshTokenService;

import jakarta.transaction.Transactional;

@Service
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder, 
        JwtService jwtService, 
        RefreshTokenService refreshTokenService, JwtProperties jwtProperties, RefreshTokenRepository refreshTokenRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.jwtProperties = jwtProperties;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // 1. Normalize, so "Khoi@Mail.com " and "khoi@mail.com" are treated as the same account
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        // 2. Friendly duplicate check
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }

        // 3. Hash the password: the raw password is never stored anywhere
        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(email, passwordHash, request.fullname().trim(), request.phone());
        System.out.println(user);

        return RegisterResponse.from(userRepository.save(user));
    }

    @Transactional 
    public LoginResponse login(LoginRequest request){
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!userRepository.existsByEmail(email)){
            throw new BadCredentialException("Invalid credential");
        }
        User user = userRepository.findByEmail(email).orElseThrow(() -> new BadCredentialException("Invalid credential"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())){
            throw new BadCredentialException("Invalid credential");
        }

        // Generate access token and refresh token
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.issue(user);

        return LoginResponse.returnToken(accessToken, refreshToken, "Bearer", jwtProperties.accessTokenTtl().toSeconds());

    }

    public MeResponse getCurrentUser(Long userId) {
        // The token can outlive the account (e.g. user deleted 5 minutes ago), so look it up
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return MeResponse.from(user);
    }

    @Transactional 
    public void logout(String refreshToken){
        refreshTokenService.revoke(refreshToken);
    }
}
