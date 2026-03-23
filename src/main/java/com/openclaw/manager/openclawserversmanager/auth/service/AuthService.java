package com.openclaw.manager.openclawserversmanager.auth.service;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.auth.config.JwtConfig;
import com.openclaw.manager.openclawserversmanager.auth.dto.LoginRequest;
import com.openclaw.manager.openclawserversmanager.auth.dto.LoginResponse;
import com.openclaw.manager.openclawserversmanager.auth.dto.UserInfoResponse;
import com.openclaw.manager.openclawserversmanager.auth.entity.RefreshToken;
import com.openclaw.manager.openclawserversmanager.auth.repository.RefreshTokenRepository;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.users.entity.User;
import com.openclaw.manager.openclawserversmanager.users.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final JwtConfig jwtConfig;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            JwtConfig jwtConfig,
            PasswordEncoder passwordEncoder,
            AuditService auditService
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.jwtConfig = jwtConfig;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);

        if (user == null) {
            try { auditService.log(AuditAction.USER_LOGIN_FAILED, "USER", null, null,
                    "Failed login attempt for %s".formatted(request.email())); } catch (Exception ignored) {}
            throw new IllegalArgumentException("Invalid email or password");
        }

        if (!user.isEnabled()) {
            try { auditService.log(AuditAction.USER_LOGIN_FAILED, "USER", user.getId(), user.getId(),
                    "Login attempt for disabled account %s".formatted(request.email())); } catch (Exception ignored) {}
            throw new IllegalArgumentException("Account is disabled");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            try { auditService.log(AuditAction.USER_LOGIN_FAILED, "USER", user.getId(), user.getId(),
                    "Failed login attempt for %s".formatted(request.email())); } catch (Exception ignored) {}
            throw new IllegalArgumentException("Invalid email or password");
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenValue = createRefreshToken(user);

        try { auditService.log(AuditAction.USER_LOGIN, "USER", user.getId(), user.getId(),
                "Login from %s".formatted(user.getEmail())); } catch (Exception ignored) {}

        return LoginResponse.of(accessToken, refreshTokenValue, jwtConfig.getAccessTokenExpiration());
    }

    @Transactional
    public LoginResponse refresh(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (!refreshToken.isUsable()) {
            throw new IllegalArgumentException("Refresh token is expired or revoked");
        }

        // Rotate: revoke old, issue new
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        User user = refreshToken.getUser();
        String accessToken = jwtService.generateAccessToken(user);
        String newRefreshTokenValue = createRefreshToken(user);

        return LoginResponse.of(accessToken, newRefreshTokenValue, jwtConfig.getAccessTokenExpiration());
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        try {
            User user = refreshToken.getUser();
            auditService.log(AuditAction.USER_LOGOUT, "USER", user.getId(), user.getId());
        } catch (Exception ignored) {}
    }

    public UserInfoResponse getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " not found"));

        return new UserInfoResponse(user.getId(), user.getEmail(), user.getUsername(), user.getRole());
    }

    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    private String createRefreshToken(User user) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiresAt(Instant.now().plusMillis(jwtConfig.getRefreshTokenExpiration()));
        refreshTokenRepository.save(refreshToken);
        return refreshToken.getToken();
    }
}
