package com.openclaw.manager.openclawserversmanager.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "login.security")
public class LoginSecurityProperties {

    private int rateLimitMaxAttempts = 10;
    private int rateLimitWindowSeconds = 60;
    private int lockoutThreshold = 5;
    private int lockoutDurationMinutes = 15;

    public int getRateLimitMaxAttempts() { return rateLimitMaxAttempts; }
    public void setRateLimitMaxAttempts(int rateLimitMaxAttempts) { this.rateLimitMaxAttempts = rateLimitMaxAttempts; }

    public int getRateLimitWindowSeconds() { return rateLimitWindowSeconds; }
    public void setRateLimitWindowSeconds(int rateLimitWindowSeconds) { this.rateLimitWindowSeconds = rateLimitWindowSeconds; }

    public int getLockoutThreshold() { return lockoutThreshold; }
    public void setLockoutThreshold(int lockoutThreshold) { this.lockoutThreshold = lockoutThreshold; }

    public int getLockoutDurationMinutes() { return lockoutDurationMinutes; }
    public void setLockoutDurationMinutes(int lockoutDurationMinutes) { this.lockoutDurationMinutes = lockoutDurationMinutes; }
}
