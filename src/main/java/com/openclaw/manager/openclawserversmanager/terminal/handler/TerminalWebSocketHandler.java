package com.openclaw.manager.openclawserversmanager.terminal.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
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
import org.springframework.web.util.UriComponentsBuilder;

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
    private final SshService sshService;
    private final ServerService serverService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ConcurrentHashMap<String, TerminalSession> wsSessionMap = new ConcurrentHashMap<>();

    public TerminalWebSocketHandler(TerminalSessionService terminalSessionService,
                                     SshService sshService,
                                     ServerService serverService,
                                     AuditService auditService) {
        this.terminalSessionService = terminalSessionService;
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

        if (!terminalSessionService.canOpenSession(tokenInfo.userId())) {
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

        // Start output streaming thread
        Thread.ofVirtual().name("terminal-" + terminalSession.getSessionId()).start(() ->
                streamOutput(wsSession, terminalSession));
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
                // sshj doesn't natively support window-change after session start — known limitation
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
            terminalSessionService.removeSession(terminalSession.getSessionId());
            try {
                terminalSession.getSshSession().close();
            } catch (IOException ignored) {
            }
        }
    }

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
