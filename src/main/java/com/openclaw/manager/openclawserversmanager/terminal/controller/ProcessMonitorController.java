package com.openclaw.manager.openclawserversmanager.terminal.controller;

import com.openclaw.manager.openclawserversmanager.deployment.dto.DeploymentJobResponse;
import com.openclaw.manager.openclawserversmanager.deployment.entity.DeploymentStatus;
import com.openclaw.manager.openclawserversmanager.deployment.mapper.DeploymentMapper;
import com.openclaw.manager.openclawserversmanager.deployment.repository.DeploymentJobRepository;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.repository.ServerRepository;
import com.openclaw.manager.openclawserversmanager.terminal.dto.ActiveSessionInfo;
import com.openclaw.manager.openclawserversmanager.terminal.dto.ProcessMonitorResponse;
import com.openclaw.manager.openclawserversmanager.terminal.handler.TerminalWebSocketHandler;
import com.openclaw.manager.openclawserversmanager.terminal.model.TerminalSession;
import com.openclaw.manager.openclawserversmanager.terminal.service.TerminalSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/processes")
@SecurityRequirement(name = "bearerAuth")
public class ProcessMonitorController {

    private static final Logger log = LoggerFactory.getLogger(ProcessMonitorController.class);

    private final TerminalSessionService terminalSessionService;
    private final TerminalWebSocketHandler webSocketHandler;
    private final ServerRepository serverRepository;
    private final DeploymentJobRepository deploymentJobRepository;

    public ProcessMonitorController(TerminalSessionService terminalSessionService,
                                     TerminalWebSocketHandler webSocketHandler,
                                     ServerRepository serverRepository,
                                     DeploymentJobRepository deploymentJobRepository) {
        this.terminalSessionService = terminalSessionService;
        this.webSocketHandler = webSocketHandler;
        this.serverRepository = serverRepository;
        this.deploymentJobRepository = deploymentJobRepository;
    }

    @GetMapping
    @Operation(summary = "List all active background processes and running jobs")
    public ResponseEntity<ProcessMonitorResponse> listProcesses() {
        // Build server name lookup
        Map<UUID, String> serverNames = new java.util.HashMap<>();
        serverRepository.findAll().forEach(s -> serverNames.put(s.getId(), s.getName()));

        // Map active sessions
        Instant now = Instant.now();
        List<ActiveSessionInfo> sessions = new ArrayList<>();
        for (TerminalSession ts : terminalSessionService.getAllActiveSessions()) {
            String type;
            if (ts.isDeploymentSession()) type = "DEPLOYMENT";
            else if (ts.isPersistentSession()) type = "PERSISTENT";
            else type = "TERMINAL";

            boolean sshConnected = ts.getSshSession() != null && ts.getSshSession().isConnected();
            boolean hasWs = webSocketHandler.hasActiveWebSocket(ts.getSessionId());

            String status;
            if (hasWs && sshConnected) status = "CONNECTED";
            else if (sshConnected) status = "BUFFERING";
            else status = "DISCONNECTED";

            long duration = now.getEpochSecond() - ts.getCreatedAt().getEpochSecond();
            int bufferSize = ts.getBufferedOutput() != null ? ts.getBufferedOutput().length() : 0;

            sessions.add(new ActiveSessionInfo(
                    ts.getSessionId(),
                    ts.getServerId(),
                    serverNames.getOrDefault(ts.getServerId(), ts.getServerId().toString()),
                    ts.getUserId(),
                    type, status,
                    ts.getCreatedAt(), ts.getLastActivityAt(),
                    duration, sshConnected, hasWs,
                    ts.getDeploymentJobId(), bufferSize
            ));
        }

        // Get running/pending deployment jobs
        List<DeploymentJobResponse> runningJobs = new ArrayList<>();
        deploymentJobRepository.findByStatus(DeploymentStatus.RUNNING,
                PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt")))
                .forEach(j -> runningJobs.add(DeploymentMapper.toJobResponse(j)));
        deploymentJobRepository.findByStatus(DeploymentStatus.PENDING,
                PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt")))
                .forEach(j -> runningJobs.add(DeploymentMapper.toJobResponse(j)));

        return ResponseEntity.ok(new ProcessMonitorResponse(
                sessions, runningJobs, sessions.size(), runningJobs.size()));
    }

    @PostMapping("/{sessionId}/kill")
    @Operation(summary = "Kill an active terminal session")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void killSession(@PathVariable String sessionId) {
        TerminalSession session = terminalSessionService.removeSession(sessionId);
        if (session == null) {
            log.warn("Session not found for kill: {}", sessionId);
            return;
        }
        try {
            session.getSshSession().close();
        } catch (Exception e) {
            log.warn("Error closing SSH for session {}: {}", sessionId, e.getMessage());
        }
        log.info("Session killed via process monitor: {}", sessionId);
    }
}
