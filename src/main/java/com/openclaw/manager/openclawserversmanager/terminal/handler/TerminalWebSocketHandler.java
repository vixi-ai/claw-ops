package com.openclaw.manager.openclawserversmanager.terminal.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.deployment.service.InteractiveDeploymentService;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.service.ServerService;
import com.openclaw.manager.openclawserversmanager.ssh.model.SshSession;
import com.openclaw.manager.openclawserversmanager.ssh.service.SshService;
import com.openclaw.manager.openclawserversmanager.terminal.model.SessionTokenInfo;
import com.openclaw.manager.openclawserversmanager.terminal.model.TerminalMessage;
import com.openclaw.manager.openclawserversmanager.terminal.model.TerminalOutput;
import com.openclaw.manager.openclawserversmanager.terminal.model.TerminalSession;
import com.openclaw.manager.openclawserversmanager.terminal.service.TerminalSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TerminalWebSocketHandler.class);

    private final TerminalSessionService terminalSessionService;
    private final InteractiveDeploymentService interactiveDeploymentService;
    private final SshService sshService;
    private final ServerService serverService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // wsSessionId -> terminalSession
    private final ConcurrentHashMap<String, TerminalSession> wsSessionMap = new ConcurrentHashMap<>();
    // terminalSessionId -> active WebSocketSession (for deployment sessions)
    private final ConcurrentHashMap<String, WebSocketSession> deploymentWsSessions = new ConcurrentHashMap<>();
    // terminalSessionId -> active WebSocketSession (for persistent sessions)
    private final ConcurrentHashMap<String, WebSocketSession> persistentWsSessions = new ConcurrentHashMap<>();

    public TerminalWebSocketHandler(TerminalSessionService terminalSessionService,
                                     InteractiveDeploymentService interactiveDeploymentService,
                                     SshService sshService,
                                     ServerService serverService,
                                     AuditService auditService) {
        this.terminalSessionService = terminalSessionService;
        this.interactiveDeploymentService = interactiveDeploymentService;
        this.sshService = sshService;
        this.serverService = serverService;
        this.auditService = auditService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) throws Exception {
        String query = wsSession.getUri() != null ? wsSession.getUri().getQuery() : null;
        Map<String, String> params = parseQueryParams(query);

        String token = params.get("token");
        if (token == null || token.isBlank()) {
            wsSession.close(new CloseStatus(4001, "Missing session token"));
            return;
        }

        SessionTokenInfo tokenInfo = terminalSessionService.validateAndConsumeToken(token);
        if (tokenInfo == null) {
            wsSession.close(new CloseStatus(4001, "Invalid or expired session token"));
            return;
        }

        String mode = params.get("mode");

        // Persistent session (new or reconnection)
        if ("persistent".equals(mode) && tokenInfo.isPersistentToken()) {
            handlePersistentConnection(wsSession, tokenInfo);
            return;
        }

        // Deployment reconnection
        if ("deployment".equals(mode) && tokenInfo.isReconnectionToken()) {
            handleDeploymentReconnection(wsSession, tokenInfo);
            return;
        }

        // Deployment new connection
        if ("deployment".equals(mode) && tokenInfo.isDeploymentToken()) {
            handleDeploymentNewConnection(wsSession, tokenInfo);
            return;
        }

        // Regular terminal session
        if (!terminalSessionService.canOpenSession(tokenInfo.userId())) {
            sendMessage(wsSession, new TerminalOutput("ERROR", "Maximum terminal session limit reached. Close other terminals and try again."));
            wsSession.close(new CloseStatus(4002, "Maximum session limit reached"));
            return;
        }

        Server server;
        try {
            server = serverService.getServerEntity(tokenInfo.serverId());
        } catch (ResourceNotFoundException e) {
            wsSession.close(new CloseStatus(4003, "Server not found"));
            return;
        }

        SshSession sshSession;
        try {
            int cols = params.containsKey("cols") ? Integer.parseInt(params.get("cols")) : 120;
            int rows = params.containsKey("rows") ? Integer.parseInt(params.get("rows")) : 40;
            sshSession = sshService.openInteractiveSession(server, cols, rows);
        } catch (Exception e) {
            log.error("Failed to open SSH session to server {}: {}", server.getName(), e.getMessage());
            sendMessage(wsSession, new TerminalOutput("ERROR", "Failed to connect: " + e.getMessage()));
            wsSession.close(new CloseStatus(4004, "SSH connection failed"));
            return;
        }

        TerminalSession terminalSession = new TerminalSession(
                sshSession.getSessionId(), tokenInfo.serverId(), tokenInfo.userId(), sshSession);
        terminalSessionService.registerSession(terminalSession.getSessionId(), terminalSession);
        wsSessionMap.put(wsSession.getId(), terminalSession);

        try {
            auditService.log(AuditAction.TERMINAL_SESSION_OPENED, "SERVER", tokenInfo.serverId(),
                    tokenInfo.userId(), "Terminal session opened on '%s'".formatted(server.getName()));
        } catch (Exception ignored) {
        }

        Thread.ofVirtual().name("terminal-" + terminalSession.getSessionId()).start(() ->
                streamOutput(wsSession, terminalSession));
    }

    private void handleDeploymentNewConnection(WebSocketSession wsSession, SessionTokenInfo tokenInfo) throws IOException {
        TerminalSession terminalSession = terminalSessionService.findSessionByJobId(tokenInfo.jobId());
        if (terminalSession == null) {
            wsSession.close(new CloseStatus(4005, "Deployment session not found"));
            return;
        }

        wsSessionMap.put(wsSession.getId(), terminalSession);
        deploymentWsSessions.put(terminalSession.getSessionId(), wsSession);

        // Replay any buffered output (e.g. welcome banner that appeared before WS connected)
        String buffered = terminalSession.getBufferedOutput();
        if (!buffered.isEmpty()) {
            sendMessage(wsSession, new TerminalOutput("OUTPUT", buffered));
        }

        // Start direct SSH output streaming (reads from SSH, sends to WS, AND buffers)
        Thread.ofVirtual().name("deploy-stream-" + terminalSession.getSessionId()).start(() ->
                streamDeploymentOutput(wsSession, terminalSession));

        // Trigger script execution now that the WebSocket is ready
        interactiveDeploymentService.executeScript(
                terminalSession.getDeploymentJobId(), terminalSession.getSshSession());

        log.info("Deployment terminal connected for job {}", tokenInfo.jobId());
    }

    private void handleDeploymentReconnection(WebSocketSession wsSession, SessionTokenInfo tokenInfo) throws IOException {
        TerminalSession terminalSession = terminalSessionService.getSession(tokenInfo.existingSessionId());
        if (terminalSession == null || !terminalSession.isDeploymentSession()) {
            wsSession.close(new CloseStatus(4005, "Deployment session not found or expired"));
            return;
        }

        wsSessionMap.put(wsSession.getId(), terminalSession);
        deploymentWsSessions.put(terminalSession.getSessionId(), wsSession);
        terminalSession.touch();

        // Replay full buffered output
        String buffered = terminalSession.getBufferedOutput();
        if (!buffered.isEmpty()) {
            sendMessage(wsSession, new TerminalOutput("OUTPUT", buffered));
        }

        if (terminalSession.isScriptCompleted()) {
            sendMessage(wsSession, new TerminalOutput("DEPLOYMENT_COMPLETE", "Script has finished"));
        } else {
            // Resume direct streaming
            Thread.ofVirtual().name("deploy-stream-" + terminalSession.getSessionId()).start(() ->
                    streamDeploymentOutput(wsSession, terminalSession));

            // Trigger script execution if it hasn't been sent yet (first connect always comes through
            // reconnection path because the session is created before the WebSocket connects)
            interactiveDeploymentService.executeScript(
                    terminalSession.getDeploymentJobId(), terminalSession.getSshSession());
        }

        log.info("Deployment terminal reconnected for job {}", tokenInfo.jobId());
    }

    private void handlePersistentConnection(WebSocketSession wsSession, SessionTokenInfo tokenInfo) throws IOException {
        TerminalSession terminalSession = terminalSessionService.getSession(tokenInfo.existingSessionId());
        if (terminalSession == null || !terminalSession.isPersistentSession()) {
            wsSession.close(new CloseStatus(4005, "Persistent session not found or expired"));
            return;
        }

        if (!terminalSession.getSshSession().isConnected()) {
            wsSession.close(new CloseStatus(4005, "Persistent session SSH connection lost"));
            return;
        }

        wsSessionMap.put(wsSession.getId(), terminalSession);
        persistentWsSessions.put(terminalSession.getSessionId(), wsSession);
        terminalSession.touch();

        // Replay buffered output
        String buffered = terminalSession.getBufferedOutput();
        if (!buffered.isEmpty()) {
            sendMessage(wsSession, new TerminalOutput("OUTPUT", buffered));
        }

        // Start streaming (reads SSH output → sends to WS + buffers)
        Thread.ofVirtual().name("persistent-stream-" + terminalSession.getSessionId()).start(() ->
                streamPersistentOutput(wsSession, terminalSession));

        log.info("Persistent terminal connected for session {}", terminalSession.getSessionId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) throws Exception {
        TerminalSession terminalSession = wsSessionMap.get(wsSession.getId());
        if (terminalSession == null) {
            return;
        }

        TerminalMessage msg = objectMapper.readValue(message.getPayload(), TerminalMessage.class);
        terminalSession.touch();

        switch (msg.type()) {
            case "INPUT" -> {
                if (msg.data() != null) {
                    terminalSession.getSshSession().getOutputStream()
                            .write(msg.data().getBytes(StandardCharsets.UTF_8));
                    terminalSession.getSshSession().getOutputStream().flush();
                }
            }
            case "RESIZE" -> {
                log.debug("Resize requested ({}x{}) — not supported by sshj", msg.cols(), msg.rows());
            }
            case "PING" -> sendMessage(wsSession, new TerminalOutput("PONG", ""));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        TerminalSession terminalSession = wsSessionMap.remove(wsSession.getId());
        if (terminalSession == null) {
            return;
        }

        // Persistent sessions: detach WebSocket, keep SSH alive, start background buffer reader
        if (terminalSession.isPersistentSession()) {
            persistentWsSessions.remove(terminalSession.getSessionId());
            if (terminalSession.getSshSession().isConnected()) {
                Thread.ofVirtual().name("persistent-bg-" + terminalSession.getSessionId()).start(() ->
                        backgroundBufferPersistent(terminalSession));
            }
            log.info("Persistent terminal detached for session {} (SSH stays alive)",
                    terminalSession.getSessionId());
            return;
        }

        // Deployment sessions: detach WebSocket, keep SSH alive, start background buffer reader
        if (terminalSession.isDeploymentSession()) {
            deploymentWsSessions.remove(terminalSession.getSessionId());
            if (!terminalSession.isScriptCompleted()) {
                // Start a background thread to keep buffering SSH output for reconnection
                Thread.ofVirtual().name("deploy-bg-" + terminalSession.getSessionId()).start(() ->
                        backgroundBuffer(terminalSession));
            }
            log.info("Deployment terminal detached for session {} (SSH stays alive)",
                    terminalSession.getSessionId());
            return;
        }

        // Regular session: full cleanup
        Duration duration = Duration.between(terminalSession.getCreatedAt(), Instant.now());
        terminalSessionService.removeSession(terminalSession.getSessionId());

        try {
            terminalSession.getSshSession().close();
        } catch (IOException e) {
            log.debug("Error closing SSH session: {}", e.getMessage());
        }

        try {
            auditService.log(AuditAction.TERMINAL_SESSION_CLOSED, "SERVER", terminalSession.getServerId(),
                    terminalSession.getUserId(),
                    "Terminal session closed (duration: %ds)".formatted(duration.toSeconds()));
        } catch (Exception ignored) {
        }

        log.info("Terminal session closed: {} (duration: {}s)", terminalSession.getSessionId(), duration.toSeconds());
    }

    @Override
    public void handleTransportError(WebSocketSession wsSession, Throwable exception) {
        log.error("WebSocket transport error for session {}: {}", wsSession.getId(), exception.getMessage());
        TerminalSession terminalSession = wsSessionMap.remove(wsSession.getId());
        if (terminalSession != null) {
            if (terminalSession.isPersistentSession()) {
                persistentWsSessions.remove(terminalSession.getSessionId());
                // Start background buffer so SSH stays alive for reconnection;
                // if SSH is already dead, buffer will exit immediately and clean up
                if (terminalSession.getSshSession().isConnected()) {
                    Thread.ofVirtual().name("persistent-bg-" + terminalSession.getSessionId()).start(() ->
                            backgroundBufferPersistent(terminalSession));
                } else {
                    terminalSessionService.removeSession(terminalSession.getSessionId());
                }
            } else if (terminalSession.isDeploymentSession()) {
                deploymentWsSessions.remove(terminalSession.getSessionId());
            } else {
                terminalSessionService.removeSession(terminalSession.getSessionId());
                try {
                    terminalSession.getSshSession().close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    // ── Regular terminal: direct SSH stream → WebSocket ──

    private void streamOutput(WebSocketSession wsSession, TerminalSession terminalSession) {
        try {
            byte[] buffer = new byte[4096];
            int bytesRead;
            InputStream in = terminalSession.getSshSession().getInputStream();
            while ((bytesRead = in.read(buffer)) != -1) {
                if (!wsSession.isOpen()) {
                    break;
                }
                String output = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                sendMessage(wsSession, new TerminalOutput("OUTPUT", output));
            }
        } catch (IOException e) {
            if (wsSession.isOpen()) {
                try {
                    sendMessage(wsSession, new TerminalOutput("CLOSED", "Session ended"));
                } catch (Exception ignored) {
                }
            }
        }
    }

    // ── Deployment terminal: direct SSH stream → WebSocket + buffer ──

    private void streamDeploymentOutput(WebSocketSession wsSession, TerminalSession terminalSession) {
        try {
            byte[] buffer = new byte[4096];
            int bytesRead;
            InputStream in = terminalSession.getSshSession().getInputStream();
            while ((bytesRead = in.read(buffer)) != -1) {
                String output = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                terminalSession.appendToBuffer(output);

                // Send to attached WebSocket if still open
                WebSocketSession ws = deploymentWsSessions.get(terminalSession.getSessionId());
                if (ws != null && ws.isOpen()) {
                    sendMessage(ws, new TerminalOutput("OUTPUT", output));
                }

                // If WebSocket disconnected, keep reading into buffer (background mode)
                // A new streamDeploymentOutput will be started on reconnection
                if (ws == null || !ws.isOpen()) {
                    // Continue buffering without a WebSocket
                    backgroundBuffer(terminalSession);
                    return;
                }
            }
        } catch (IOException e) {
            log.debug("SSH stream ended for deployment session {}", terminalSession.getSessionId());
        }

        // Stream ended — script completed
        handleDeploymentCompletion(terminalSession);
    }

    // ── Background buffer: reads SSH output when no WebSocket is attached ──

    private void backgroundBuffer(TerminalSession terminalSession) {
        try {
            byte[] buffer = new byte[4096];
            int bytesRead;
            InputStream in = terminalSession.getSshSession().getInputStream();
            while ((bytesRead = in.read(buffer)) != -1) {
                String output = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                terminalSession.appendToBuffer(output);

                // If a WebSocket reconnected, hand off to streamDeploymentOutput
                WebSocketSession ws = deploymentWsSessions.get(terminalSession.getSessionId());
                if (ws != null && ws.isOpen()) {
                    // WebSocket reconnected — replay what we buffered since disconnect,
                    // then continue as direct stream
                    return; // The reconnection handler starts a new streamDeploymentOutput
                }
            }
        } catch (IOException e) {
            log.debug("SSH stream ended during background buffering for session {}",
                    terminalSession.getSessionId());
        }

        handleDeploymentCompletion(terminalSession);
    }

    private void handleDeploymentCompletion(TerminalSession terminalSession) {
        if (terminalSession.isScriptCompleted()) return; // already handled

        terminalSession.setScriptCompleted(true);
        String logs = terminalSession.getBufferedOutput();

        // Update job in database
        interactiveDeploymentService.completeJob(terminalSession.getDeploymentJobId(), logs);

        // Notify attached WebSocket
        WebSocketSession ws = deploymentWsSessions.get(terminalSession.getSessionId());
        if (ws != null && ws.isOpen()) {
            sendMessage(ws, new TerminalOutput("DEPLOYMENT_COMPLETE", "Script has finished"));
        }
    }

    // ── Persistent terminal: SSH stream → WebSocket + buffer ──

    private void streamPersistentOutput(WebSocketSession wsSession, TerminalSession terminalSession) {
        try {
            byte[] buffer = new byte[4096];
            int bytesRead;
            InputStream in = terminalSession.getSshSession().getInputStream();
            while ((bytesRead = in.read(buffer)) != -1) {
                String output = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                terminalSession.appendToBuffer(output);
                terminalSession.touch(); // Keep session alive while output is flowing

                WebSocketSession ws = persistentWsSessions.get(terminalSession.getSessionId());
                if (ws != null && ws.isOpen()) {
                    sendMessage(ws, new TerminalOutput("OUTPUT", output));
                }

                // WebSocket disconnected — switch to background buffering
                if (ws == null || !ws.isOpen()) {
                    backgroundBufferPersistent(terminalSession);
                    return;
                }
            }
        } catch (IOException e) {
            log.debug("SSH stream ended for persistent session {}", terminalSession.getSessionId());
        }

        // SSH stream ended — notify attached client
        WebSocketSession ws = persistentWsSessions.get(terminalSession.getSessionId());
        if (ws != null && ws.isOpen()) {
            sendMessage(ws, new TerminalOutput("CLOSED", "Session ended"));
        }
        terminalSessionService.removeSession(terminalSession.getSessionId());
    }

    private void backgroundBufferPersistent(TerminalSession terminalSession) {
        try {
            byte[] buffer = new byte[4096];
            int bytesRead;
            InputStream in = terminalSession.getSshSession().getInputStream();
            while ((bytesRead = in.read(buffer)) != -1) {
                String output = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                terminalSession.appendToBuffer(output);
                terminalSession.touch(); // Keep session alive while output is flowing

                // If a WebSocket reconnected, hand off to streamPersistentOutput
                WebSocketSession ws = persistentWsSessions.get(terminalSession.getSessionId());
                if (ws != null && ws.isOpen()) {
                    return; // Reconnection handler starts a new streamPersistentOutput
                }
            }
        } catch (IOException e) {
            log.debug("SSH stream ended during background buffering for persistent session {}",
                    terminalSession.getSessionId());
        }

        // SSH died while disconnected — clean up
        terminalSessionService.removeSession(terminalSession.getSessionId());
    }

    private void sendMessage(WebSocketSession wsSession, TerminalOutput output) {
        try {
            synchronized (wsSession) {
                if (wsSession.isOpen()) {
                    wsSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(output)));
                }
            }
        } catch (IOException e) {
            log.debug("Failed to send WebSocket message: {}", e.getMessage());
        }
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new java.util.HashMap<>();
        if (query == null || query.isBlank()) {
            return params;
        }
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2) {
                params.put(kv[0], kv[1]);
            }
        }
        return params;
    }
}
