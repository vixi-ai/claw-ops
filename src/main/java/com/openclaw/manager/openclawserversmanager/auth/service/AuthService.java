package com.openclaw.manager.openclawserversmanager.auth.service;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.auth.config.JwtConfig;
import com.openclaw.manager.openclawserversmanager.auth.config.LoginSecurityProperties;
import com.openclaw.manager.openclawserversmanager.auth.dto.LoginRequest;
import com.openclaw.manager.openclawserversmanager.auth.dto.LoginResponse;
import com.openclaw.manager.openclawserversmanager.auth.dto.UserInfoResponse;
import com.openclaw.manager.openclawserversmanager.auth.entity.RefreshToken;
import com.openclaw.manager.openclawserversmanager.auth.repository.RefreshTokenRepository;
import com.openclaw.manager.openclawserversmanager.common.exception.RateLimitExceededException;
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
    private final LoginRateLimiterService rateLimiterService;
    private final LoginSecurityProperties loginSecurityProperties;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            JwtConfig jwtConfig,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            LoginRateLimiterService rateLimiterService,
            LoginSecurityProperties loginSecurityProperties
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.jwtConfig = jwtConfig;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.rateLimiterService = rateLimiterService;
        this.loginSecurityProperties = loginSecurityProperties;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress) {
        // 1. Check IP rate limit
        if (rateLimiterService.isRateLimited(ipAddress)) {
            try { auditService.log(AuditAction.USER_LOGIN_FAILED, "USER", null, null,
                    "Rate limited login attempt from IP %s for %s".formatted(ipAddress, request.email())); } catch (Exception ignored) {}
            throw new RateLimitExceededException("Too many login attempts. Try again later.");
        }
        rateLimiterService.recordAttempt(ipAddress);

        // 2. Find user
        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null) {
            try { auditService.log(AuditAction.USER_LOGIN_FAILED, "USER", null, null,
                    "Failed login attempt for %s".formatted(request.email())); } catch (Exception ignored) {}
            throw new IllegalArgumentException("Invalid email or password");
        }

        // 3. Check account lockout
        if (user.isAccountLocked()) {
            try { auditService.log(AuditAction.USER_LOGIN_FAILED, "USER", user.getId(), user.getId(),
                    "Login attempt for locked account %s".formatted(request.email())); } catch (Exception ignored) {}
            throw new IllegalArgumentException("Account is temporarily locked. Try again later.");
        }

        // 4. Auto-unlock if lockout period expired
        if (user.getLockedUntil() != null && !user.isAccountLocked()) {
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }

        // 5. Check if account is disabled
        if (!user.isEnabled()) {
            try { auditService.log(AuditAction.USER_LOGIN_FAILED, "USER", user.getId(), user.getId(),
                    "Login attempt for disabled account %s".formatted(request.email())); } catch (Exception ignored) {}
            throw new IllegalArgumentException("Account is disabled");
        }

        // 6. Verify password
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleFailedLogin(user);
            throw new IllegalArgumentException("Invalid email or password");
        }

        // 7. Success — reset failed attempts
        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }

        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenValue = createRefreshToken(user);
        userRepository.save(user);

        try { auditService.log(AuditAction.USER_LOGIN, "USER", user.getId(), user.getId(),
                "Login from %s".formatted(user.getEmail())); } catch (Exception ignored) {}

        return LoginResponse.of(accessToken, refreshTokenValue, jwtConfig.getAccessTokenExpiration());
    }

    private void handleFailedLogin(User user) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);

        if (user.getFailedLoginAttempts() >= loginSecurityProperties.getLockoutThreshold()) {
            user.setLockedUntil(Instant.now().plusSeconds(
                    loginSecurityProperties.getLockoutDurationMinutes() * 60L));
            userRepository.save(user);

            try { auditService.log(AuditAction.USER_ACCOUNT_LOCKED, "USER", user.getId(), user.getId(),
                    "Account locked after %d failed attempts for %s".formatted(
                            user.getFailedLoginAttempts(), user.getEmail())); } catch (Exception ignored) {}
        } else {
            userRepository.save(user);

            try { auditService.log(AuditAction.USER_LOGIN_FAILED, "USER", user.getId(), user.getId(),
                    "Failed login attempt %d/%d for %s".formatted(
                            user.getFailedLoginAttempts(), loginSecurityProperties.getLockoutThreshold(),
                            user.getEmail())); } catch (Exception ignored) {}
        }
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
