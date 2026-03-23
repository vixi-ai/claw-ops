package com.openclaw.manager.openclawserversmanager.common.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {

    private String allowedOrigins = "*";
    private String allowedMethods = "GET,POST,PATCH,DELETE,OPTIONS";
    private String allowedHeaders = "Authorization,Content-Type,X-Requested-With,Accept,Origin";
    private String exposeHeaders = "";
    private boolean allowCredentials = false;
    private long maxAge = 3600;

    public String getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }

    public String getAllowedMethods() { return allowedMethods; }
    public void setAllowedMethods(String allowedMethods) { this.allowedMethods = allowedMethods; }

    public String getAllowedHeaders() { return allowedHeaders; }
    public void setAllowedHeaders(String allowedHeaders) { this.allowedHeaders = allowedHeaders; }

    public String getExposeHeaders() { return exposeHeaders; }
    public void setExposeHeaders(String exposeHeaders) { this.exposeHeaders = exposeHeaders; }

    public boolean isAllowCredentials() { return allowCredentials; }
    public void setAllowCredentials(boolean allowCredentials) { this.allowCredentials = allowCredentials; }

    public long getMaxAge() { return maxAge; }
    public void setMaxAge(long maxAge) { this.maxAge = maxAge; }

    public List<String> getAllowedOriginsList() {
        return parseCommaSeparated(allowedOrigins);
    }

    public List<String> getAllowedMethodsList() {
        return parseCommaSeparated(allowedMethods);
    }

    public List<String> getAllowedHeadersList() {
        return parseCommaSeparated(allowedHeaders);
    }

    public List<String> getExposeHeadersList() {
        return parseCommaSeparated(exposeHeaders);
    }

    public String[] getAllowedOriginsArray() {
        return getAllowedOriginsList().toArray(String[]::new);
    }

    private List<String> parseCommaSeparated(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
