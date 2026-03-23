package com.openclaw.manager.openclawserversmanager.auth.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        String tokenType
) {
    public static LoginResponse of(String accessToken, String refreshToken, long expiresIn) {
        return new LoginResponse(accessToken, refreshToken, expiresIn, "Bearer");
    }
}
