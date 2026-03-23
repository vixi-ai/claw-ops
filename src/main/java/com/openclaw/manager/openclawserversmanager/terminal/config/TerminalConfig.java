package com.openclaw.manager.openclawserversmanager.terminal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "terminal")
public class TerminalConfig {

    private int sessionTimeoutMinutes = 30;
    private int maxSessionsPerUser = 5;
    private int tokenExpirySeconds = 60;

    public int getSessionTimeoutMinutes() { return sessionTimeoutMinutes; }
    public void setSessionTimeoutMinutes(int sessionTimeoutMinutes) { this.sessionTimeoutMinutes = sessionTimeoutMinutes; }

    public int getMaxSessionsPerUser() { return maxSessionsPerUser; }
    public void setMaxSessionsPerUser(int maxSessionsPerUser) { this.maxSessionsPerUser = maxSessionsPerUser; }

    public int getTokenExpirySeconds() { return tokenExpirySeconds; }
    public void setTokenExpirySeconds(int tokenExpirySeconds) { this.tokenExpirySeconds = tokenExpirySeconds; }
}
