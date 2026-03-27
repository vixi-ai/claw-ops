package com.openclaw.manager.openclawserversmanager.terminal.model;

import com.openclaw.manager.openclawserversmanager.ssh.model.SshSession;

import java.time.Instant;
import java.util.UUID;

public class TerminalSession {

    private static final int MAX_BUFFER_SIZE = 512 * 1024; // 512KB

    private final String sessionId;
    private final UUID serverId;
    private final UUID userId;
    private final SshSession sshSession;
    private final Instant createdAt;
    private volatile Instant lastActivityAt;

    // Deployment-specific fields
    private final UUID deploymentJobId;
    private final StringBuilder outputBuffer;
    private volatile boolean scriptCompleted;

    public TerminalSession(String sessionId, UUID serverId, UUID userId, SshSession sshSession) {
        this(sessionId, serverId, userId, sshSession, null);
    }

    public TerminalSession(String sessionId, UUID serverId, UUID userId, SshSession sshSession, UUID deploymentJobId) {
        this.sessionId = sessionId;
        this.serverId = serverId;
        this.userId = userId;
        this.sshSession = sshSession;
        this.createdAt = Instant.now();
        this.lastActivityAt = Instant.now();
        this.deploymentJobId = deploymentJobId;
        this.outputBuffer = deploymentJobId != null ? new StringBuilder() : null;
        this.scriptCompleted = false;
    }

    public String getSessionId() { return sessionId; }
    public UUID getServerId() { return serverId; }
    public UUID getUserId() { return userId; }
    public SshSession getSshSession() { return sshSession; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastActivityAt() { return lastActivityAt; }
    public UUID getDeploymentJobId() { return deploymentJobId; }
    public boolean isScriptCompleted() { return scriptCompleted; }

    public void touch() {
        this.lastActivityAt = Instant.now();
    }

    public boolean isDeploymentSession() {
        return deploymentJobId != null;
    }

    public void appendToBuffer(String output) {
        if (outputBuffer == null) return;
        synchronized (outputBuffer) {
            outputBuffer.append(output);
            if (outputBuffer.length() > MAX_BUFFER_SIZE) {
                outputBuffer.delete(0, outputBuffer.length() - MAX_BUFFER_SIZE);
            }
        }
    }

    public String getBufferedOutput() {
        if (outputBuffer == null) return "";
        synchronized (outputBuffer) {
            return outputBuffer.toString();
        }
    }

    public void setScriptCompleted(boolean completed) {
        this.scriptCompleted = completed;
    }
}
