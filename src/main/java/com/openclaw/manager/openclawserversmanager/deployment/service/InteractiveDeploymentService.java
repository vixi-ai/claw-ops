package com.openclaw.manager.openclawserversmanager.deployment.service;

import com.openclaw.manager.openclawserversmanager.deployment.entity.DeploymentJob;
import com.openclaw.manager.openclawserversmanager.deployment.entity.DeploymentStatus;
import com.openclaw.manager.openclawserversmanager.deployment.repository.DeploymentJobRepository;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.service.ServerService;
import com.openclaw.manager.openclawserversmanager.ssh.model.SshSession;
import com.openclaw.manager.openclawserversmanager.ssh.service.SshService;
import com.openclaw.manager.openclawserversmanager.terminal.model.TerminalSession;
import com.openclaw.manager.openclawserversmanager.terminal.service.TerminalSessionService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InteractiveDeploymentService {

    private static final Logger log = LoggerFactory.getLogger(InteractiveDeploymentService.class);

    private final DeploymentJobRepository jobRepository;
    private final ServerService serverService;
    private final SshService sshService;
    private final TerminalSessionService terminalSessionService;

    // jobId -> terminal sessionId
    private final ConcurrentHashMap<UUID, String> activeDeploymentSessions = new ConcurrentHashMap<>();
    // jobId -> script content (held until WebSocket connects and triggers execution)
    private final ConcurrentHashMap<UUID, String> pendingScripts = new ConcurrentHashMap<>();

    public InteractiveDeploymentService(DeploymentJobRepository jobRepository,
                                         ServerService serverService,
                                         SshService sshService,
                                         TerminalSessionService terminalSessionService) {
        this.jobRepository = jobRepository;
        this.serverService = serverService;
        this.sshService = sshService;
        this.terminalSessionService = terminalSessionService;
    }

    @PostConstruct
    void cleanupStaleInteractiveJobs() {
        List<DeploymentJob> staleJobs = jobRepository.findByInteractiveAndStatus(true, DeploymentStatus.RUNNING);
        for (DeploymentJob job : staleJobs) {
            job.setStatus(DeploymentStatus.FAILED);
            job.setErrorMessage("Server restarted while job was running");
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
            log.info("Cleaned up stale interactive job {} (server restart)", job.getId());
        }
        if (!staleJobs.isEmpty()) {
            log.info("Cleaned up {} stale interactive deployment jobs from previous run", staleJobs.size());
        }
    }

    public TerminalSession startInteractiveDeployment(UUID jobId, UUID serverId, UUID userId, String scriptContent) {
        DeploymentJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Deployment job {} not found", jobId);
            return null;
        }

        if (job.getStatus() == DeploymentStatus.CANCELLED) {
            return null;
        }

        Server server = serverService.getServerEntity(serverId);

        // Open interactive SSH session
        SshSession sshSession = sshService.openInteractiveSession(server, 120, 40);

        // Create terminal session with deployment context
        TerminalSession terminalSession = new TerminalSession(
                sshSession.getSessionId(), serverId, userId, sshSession, jobId);
        terminalSessionService.registerSession(terminalSession.getSessionId(), terminalSession);
        activeDeploymentSessions.put(jobId, terminalSession.getSessionId());

        // Store script content — it will be sent when the WebSocket connects
        pendingScripts.put(jobId, scriptContent);

        // Update job status
        job.setStatus(DeploymentStatus.RUNNING);
        job.setStartedAt(Instant.now());
        job.setInteractive(true);
        job.setTerminalSessionId(terminalSession.getSessionId());
        jobRepository.save(job);

        log.info("Interactive deployment session created for job {} on server {}", jobId, server.getName());
        return terminalSession;
    }

    /**
     * Called by the WebSocket handler once the client is connected and streaming.
     * Sends the script into the shell via heredoc.
     */
    public void executeScript(UUID jobId, SshSession sshSession) {
        String scriptContent = pendingScripts.remove(jobId);
        if (scriptContent == null) {
            log.debug("No pending script for job {} (already sent or reconnection)", jobId);
            return;
        }

        Thread.ofVirtual().name("deploy-exec-" + jobId).start(() -> {
            try {
                Thread.sleep(800);
                String id = jobId.toString().substring(0, 8);
                String scriptPath = "/tmp/_deploy_" + id + ".sh";
                String wrapperPath = "/tmp/_deploy_wrap_" + id + ".sh";
                String heredocMarker = "DEPLOY_SCRIPT_EOF_" + id;

                // Phase 1: Write the user's script to a temp file
                String writeScript = "cat > " + scriptPath + " <<'" + heredocMarker + "'\n"
                        + scriptContent + "\n"
                        + heredocMarker + "\n";
                sshSession.getOutputStream().write(writeScript.getBytes(StandardCharsets.UTF_8));
                sshSession.getOutputStream().flush();
                Thread.sleep(300);

                // Phase 2: Write a wrapper that runs the script, cleans up, reports exit code
                String writeWrapper = "cat > " + wrapperPath + " <<'WRAPPER_EOF'\n"
                        + "#!/bin/bash\n"
                        + "bash " + scriptPath + "\n"
                        + "__ec=$?\n"
                        + "rm -f " + scriptPath + " " + wrapperPath + "\n"
                        + "echo __EXIT_CODE:${__ec}__\n"
                        + "exit ${__ec}\n"
                        + "WRAPPER_EOF\n";
                sshSession.getOutputStream().write(writeWrapper.getBytes(StandardCharsets.UTF_8));
                sshSession.getOutputStream().flush();
                Thread.sleep(200);

                // Phase 3: Execute the wrapper — stdin stays free for interactive input
                String execCmd = "bash " + wrapperPath + "\n";
                sshSession.getOutputStream().write(execCmd.getBytes(StandardCharsets.UTF_8));
                sshSession.getOutputStream().flush();

                log.info("Script sent to shell for job {}", jobId);
            } catch (Exception e) {
                log.error("Failed to send script command for job {}: {}", jobId, e.getMessage());
            }
        });
    }

    public void completeJob(UUID jobId, String logs) {
        activeDeploymentSessions.remove(jobId);
        pendingScripts.remove(jobId);

        int exitCode = parseExitCode(logs);

        DeploymentJob job = jobRepository.findById(jobId).orElse(null);
        if (job != null && job.getStatus() == DeploymentStatus.RUNNING) {
            job.setStatus(exitCode == 0 ? DeploymentStatus.COMPLETED : DeploymentStatus.FAILED);
            job.setLogs(logs);
            if (exitCode != 0) {
                job.setErrorMessage("Exit code: " + exitCode);
            }
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
            log.info("Interactive deployment job {} finished with exit code {}", jobId, exitCode);
        }
    }

    public String getActiveSessionId(UUID jobId) {
        return activeDeploymentSessions.get(jobId);
    }

    public void removeActiveSession(UUID jobId) {
        activeDeploymentSessions.remove(jobId);
    }

    public void stopDeployment(UUID jobId) {
        String sessionId = activeDeploymentSessions.remove(jobId);
        pendingScripts.remove(jobId);
        TerminalSession terminalSession = null;

        if (sessionId != null) {
            terminalSession = terminalSessionService.removeSession(sessionId);
        } else {
            terminalSession = terminalSessionService.findSessionByJobId(jobId);
            if (terminalSession != null) {
                terminalSessionService.removeSession(terminalSession.getSessionId());
            }
        }

        if (terminalSession != null) {
            terminalSession.setScriptCompleted(true);
            try {
                terminalSession.getSshSession().close();
            } catch (Exception e) {
                log.debug("Error closing SSH session for stopped job {}: {}", jobId, e.getMessage());
            }
        }

        DeploymentJob job = jobRepository.findById(jobId).orElse(null);
        if (job != null && job.getStatus() == DeploymentStatus.RUNNING) {
            job.setStatus(DeploymentStatus.CANCELLED);
            job.setLogs(terminalSession != null ? terminalSession.getBufferedOutput() : null);
            job.setErrorMessage("Stopped by user");
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
            log.info("Interactive deployment job {} stopped by user", jobId);
        }
    }

    private int parseExitCode(String output) {
        if (output == null) return -1;
        int idx = output.lastIndexOf("__EXIT_CODE:");
        if (idx >= 0) {
            int end = output.indexOf("__", idx + 12);
            if (end > idx) {
                try {
                    return Integer.parseInt(output.substring(idx + 12, end).trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        return -1;
    }
}
