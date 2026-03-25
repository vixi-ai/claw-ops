package com.openclaw.manager.openclawserversmanager.auth.service;

import com.openclaw.manager.openclawserversmanager.auth.config.LoginSecurityProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class LoginRateLimiterService {

    private final LoginSecurityProperties properties;
    private final ConcurrentMap<String, RateLimitEntry> attempts = new ConcurrentHashMap<>();

    public LoginRateLimiterService(LoginSecurityProperties properties) {
        this.properties = properties;
    }

    public boolean isRateLimited(String ipAddress) {
        RateLimitEntry entry = attempts.get(ipAddress);
        if (entry == null) return false;

        if (entry.isExpired(properties.getRateLimitWindowSeconds())) {
            attempts.remove(ipAddress);
            return false;
        }

        return entry.getCount() >= properties.getRateLimitMaxAttempts();
    }

    public void recordAttempt(String ipAddress) {
        attempts.compute(ipAddress, (key, existing) -> {
            if (existing == null || existing.isExpired(properties.getRateLimitWindowSeconds())) {
                return new RateLimitEntry();
            }
            existing.increment();
            return existing;
        });
    }

    @Scheduled(fixedRate = 300_000) // Every 5 minutes
    public void cleanupExpiredEntries() {
        int windowSeconds = properties.getRateLimitWindowSeconds();
        attempts.entrySet().removeIf(entry -> entry.getValue().isExpired(windowSeconds));
    }

    private static class RateLimitEntry {
        private final Instant firstAttempt;
        private final AtomicInteger count;

        RateLimitEntry() {
            this.firstAttempt = Instant.now();
            this.count = new AtomicInteger(1);
        }

        boolean isExpired(int windowSeconds) {
            return Instant.now().isAfter(firstAttempt.plusSeconds(windowSeconds));
        }

        int getCount() {
            return count.get();
        }

        void increment() {
            count.incrementAndGet();
        }
    }
}
