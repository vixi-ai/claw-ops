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
    private volatile boolean scriptCompleted;

    // Persistent session flag (SSH survives WS disconnect, output buffered)
    private final boolean persistent;

    // Output buffer — allocated for deployment and persistent sessions
    private final StringBuilder outputBuffer;

    public TerminalSession(String sessionId, UUID serverId, UUID userId, SshSession sshSession) {
        this(sessionId, serverId, userId, sshSession, null, false);
    }

    public TerminalSession(String sessionId, UUID serverId, UUID userId, SshSession sshSession, UUID deploymentJobId) {
        this(sessionId, serverId, userId, sshSession, deploymentJobId, false);
    }

    public TerminalSession(String sessionId, UUID serverId, UUID userId, SshSession sshSession,
                           UUID deploymentJobId, boolean persistent) {
        this.sessionId = sessionId;
        this.serverId = serverId;
        this.userId = userId;
        this.sshSession = sshSession;
        this.createdAt = Instant.now();
        this.lastActivityAt = Instant.now();
        this.deploymentJobId = deploymentJobId;
        this.persistent = persistent;
        this.outputBuffer = (deploymentJobId != null || persistent) ? new StringBuilder() : null;
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
    public boolean isPersistent() { return persistent; }

    public void touch() {
        this.lastActivityAt = Instant.now();
    }

    public boolean isDeploymentSession() {
        return deploymentJobId != null;
    }

    public boolean isPersistentSession() {
        return persistent;
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
