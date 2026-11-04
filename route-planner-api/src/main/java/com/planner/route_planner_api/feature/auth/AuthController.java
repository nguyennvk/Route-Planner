package com.planner.route_planner_api.feature.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.planner.route_planner_api.feature.auth.login.dto.LoginRequest;
import com.planner.route_planner_api.feature.auth.login.dto.LoginResponse;
import com.planner.route_planner_api.feature.auth.logout.dto.LogoutRequest;
import com.planner.route_planner_api.feature.auth.me.dto.MeResponse;
import com.planner.route_planner_api.feature.auth.refresh.dto.RefreshRequest;
import com.planner.route_planner_api.feature.auth.register.dto.RegisterRequest;
import com.planner.route_planner_api.feature.auth.register.dto.RegisterResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);        
    }
    
    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        // jwt is already verified by Spring Security (signature, expiry, issuer) before we get here
        Long userId = Long.valueOf(jwt.getSubject());
        return authService.getCurrentUser(userId);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }
}
