package com.planner.route_planner_api.feature.auth.login.dto;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    Long expiresIn
) {
    public static LoginResponse returnToken(String accessToken, String refreshToken, String tokenType, Long expiresIn){
        return new LoginResponse(accessToken, refreshToken, tokenType, expiresIn);
    }
}
