package com.openclaw.manager.openclawserversmanager.auth.config;

import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.util.Base64;

@Configuration
public class JwtConfig {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtConfig(
            @Value("${JWT_SECRET:}") String jwtSecret,
            @Value("${JWT_ACCESS_TOKEN_EXPIRATION:900000}") long accessTokenExpiration,
            @Value("${JWT_REFRESH_TOKEN_EXPIRATION:604800000}") long refreshTokenExpiration
    ) {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            this.secretKey = Jwts.SIG.HS256.key().build();
        } else {
            byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);
            this.secretKey = new javax.crypto.spec.SecretKeySpec(keyBytes, "HmacSHA256");
        }
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public SecretKey getSecretKey() {
        return secretKey;
    }

    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }
}
