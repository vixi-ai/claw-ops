package com.openclaw.manager.openclawserversmanager.terminal.model;

import com.openclaw.manager.openclawserversmanager.ssh.model.SshSession;

import java.time.Instant;
import java.util.UUID;

public class TerminalSession {

    private final String sessionId;
    private final UUID serverId;
    private final UUID userId;
    private final SshSession sshSession;
    private final Instant createdAt;
    private volatile Instant lastActivityAt;

    public TerminalSession(String sessionId, UUID serverId, UUID userId, SshSession sshSession) {
        this.sessionId = sessionId;
        this.serverId = serverId;
        this.userId = userId;
        this.sshSession = sshSession;
        this.createdAt = Instant.now();
        this.lastActivityAt = Instant.now();
    }

    public String getSessionId() { return sessionId; }
    public UUID getServerId() { return serverId; }
    public UUID getUserId() { return userId; }
    public SshSession getSshSession() { return sshSession; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActivityAt() { return lastActivityAt; }

    public void touch() {
        this.lastActivityAt = Instant.now();
    }
}
