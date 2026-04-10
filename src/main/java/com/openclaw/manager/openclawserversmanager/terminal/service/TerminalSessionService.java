package com.openclaw.manager.openclawserversmanager.terminal.service;

import com.openclaw.manager.openclawserversmanager.terminal.config.TerminalConfig;
import com.openclaw.manager.openclawserversmanager.terminal.model.SessionTokenInfo;
import com.openclaw.manager.openclawserversmanager.terminal.model.TerminalSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TerminalSessionService {

    private static final Logger log = LoggerFactory.getLogger(TerminalSessionService.class);

    private final TerminalConfig terminalConfig;
    private final ConcurrentHashMap<String, SessionTokenInfo> pendingTokens = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TerminalSession> activeSessions = new ConcurrentHashMap<>();

    public TerminalSessionService(TerminalConfig terminalConfig) {
        this.terminalConfig = terminalConfig;
    }

    public String generateSessionToken(UUID serverId, UUID userId) {
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(terminalConfig.getTokenExpirySeconds());
        pendingTokens.put(token, new SessionTokenInfo(token, userId, serverId, expiresAt));
        return token;
    }

    public String generateDeploymentToken(UUID serverId, UUID userId, UUID jobId) {
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(terminalConfig.getTokenExpirySeconds());
        pendingTokens.put(token, new SessionTokenInfo(token, userId, serverId, expiresAt, jobId, null));
        return token;
    }

    public String generateReconnectionToken(UUID serverId, UUID userId, UUID jobId, String existingSessionId) {
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(terminalConfig.getTokenExpirySeconds());
        pendingTokens.put(token, new SessionTokenInfo(token, userId, serverId, expiresAt, jobId, existingSessionId));
        return token;
    }

    public String generatePersistentToken(UUID serverId, UUID userId, String sessionId) {
        String token = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plusSeconds(terminalConfig.getTokenExpirySeconds());
        pendingTokens.put(token, new SessionTokenInfo(token, userId, serverId, expiresAt, null, sessionId, true));
        return token;
    }

    public SessionTokenInfo validateAndConsumeToken(String token) {
        SessionTokenInfo info = pendingTokens.remove(token);
        if (info == null || info.isExpired()) {
            return null;
        }
        return info;
    }

    public boolean canOpenSession(UUID userId) {
        // Only count regular sessions — persistent and deployment sessions have their own lifecycle
        long count = activeSessions.values().stream()
                .filter(s -> s.getUserId().equals(userId))
                .filter(s -> !s.isPersistentSession() && !s.isDeploymentSession())
                .count();
        return count < terminalConfig.getMaxSessionsPerUser();
    }

    public void registerSession(String sessionId, TerminalSession session) {
        activeSessions.put(sessionId, session);
        log.info("Terminal session registered: {} (user: {}, server: {})",
                sessionId, session.getUserId(), session.getServerId());
    }

    public TerminalSession removeSession(String sessionId) {
        TerminalSession session = activeSessions.remove(sessionId);
        if (session != null) {
            log.info("Terminal session removed: {}", sessionId);
        }
        return session;
    }

    public TerminalSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    public TerminalSession findSessionByJobId(UUID jobId) {
        for (TerminalSession session : activeSessions.values()) {
            if (session.isDeploymentSession() && jobId.equals(session.getDeploymentJobId())) {
                return session;
            }
        }
        return null;
    }

    public int getActiveSessionCount(UUID userId) {
        return (int) activeSessions.values().stream()
                .filter(s -> s.getUserId().equals(userId))
                .count();
    }

    public List<TerminalSession> findPersistentSessions(UUID serverId) {
        return activeSessions.values().stream()
                .filter(s -> s.isPersistentSession() && s.getServerId().equals(serverId)
                        && s.getSshSession().isConnected())
                .toList();
    }

    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredTokensAndSessions() {
        // Clean expired tokens
        pendingTokens.entrySet().removeIf(entry -> entry.getValue().isExpired());

        // Close inactive sessions
        Instant cutoff = Instant.now().minus(terminalConfig.getSessionTimeoutMinutes(), ChronoUnit.MINUTES);
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, TerminalSession> entry : activeSessions.entrySet()) {
            TerminalSession session = entry.getValue();

            // Skip deployment sessions that are still running (SSH connected + script not completed)
            if (session.isDeploymentSession() && !session.isScriptCompleted()
                    && session.getSshSession().isConnected()) {
                continue;
            }

            // Skip persistent sessions that are connected AND recently active
            // (stale connections where isConnected() returns true but SSH is dead
            // will be cleaned up after the inactivity timeout)
            if (session.isPersistentSession() && session.getSshSession().isConnected()
                    && session.getLastActivityAt().isAfter(cutoff)) {
                continue;
            }

            if (session.getLastActivityAt().isBefore(cutoff) || !session.getSshSession().isConnected()) {
                toRemove.add(entry.getKey());
            }
        }
        for (String sessionId : toRemove) {
            TerminalSession session = activeSessions.remove(sessionId);
            if (session != null) {
                try {
                    session.getSshSession().close();
                } catch (Exception e) {
                    log.debug("Error closing expired session {}: {}", sessionId, e.getMessage());
                }
                log.info("Cleaned up inactive terminal session: {}", sessionId);
            }
        }
    }
}
