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

import java.io.InputStream;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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

        // Update job status
        job.setStatus(DeploymentStatus.RUNNING);
        job.setStartedAt(Instant.now());
        job.setInteractive(true);
        job.setTerminalSessionId(terminalSession.getSessionId());
        jobRepository.save(job);

        // Pipe the script content directly into bash via heredoc in the interactive shell
        // This avoids needing SFTP upload
        Thread.ofVirtual().name("deploy-exec-" + jobId).start(() -> {
            try {
                // Small delay to let the shell initialize
                Thread.sleep(500);
                // Use a heredoc to pipe the script into bash, then capture exit code
                String heredocMarker = "DEPLOY_SCRIPT_EOF_" + jobId.toString().substring(0, 8);
                String command = "bash <<'" + heredocMarker + "'\n"
                        + scriptContent + "\n"
                        + heredocMarker + "\n"
                        + "__DEPLOY_EC=$?\n"
                        + "echo __EXIT_CODE:${__DEPLOY_EC}__\n"
                        + "exit ${__DEPLOY_EC}\n";
                sshSession.getOutputStream().write(command.getBytes(StandardCharsets.UTF_8));
                sshSession.getOutputStream().flush();
            } catch (Exception e) {
                log.error("Failed to send script command for job {}: {}", jobId, e.getMessage());
            }
        });

        // Start background monitor for script completion
        Thread.ofVirtual().name("deploy-monitor-" + jobId).start(() ->
                monitorCompletion(terminalSession, jobId, serverId));

        return terminalSession;
    }

    public String getActiveSessionId(UUID jobId) {
        return activeDeploymentSessions.get(jobId);
    }

    public void removeActiveSession(UUID jobId) {
        activeDeploymentSessions.remove(jobId);
    }

    public void stopDeployment(UUID jobId) {
        // Try to clean up the active session if it exists in memory
        String sessionId = activeDeploymentSessions.remove(jobId);
        TerminalSession terminalSession = null;

        if (sessionId != null) {
            terminalSession = terminalSessionService.removeSession(sessionId);
        } else {
            // App may have restarted — try to find session by job ID
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

        // Always update the DB — even if session is gone (e.g. after app restart)
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

    private void monitorCompletion(TerminalSession terminalSession, UUID jobId, UUID serverId) {
        try {
            byte[] buffer = new byte[4096];
            int bytesRead;
            InputStream in = terminalSession.getSshSession().getInputStream();
            while ((bytesRead = in.read(buffer)) != -1) {
                String output = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                terminalSession.appendToBuffer(output);
            }
        } catch (Exception e) {
            log.debug("Output stream ended for deployment job {}: {}", jobId, e.getMessage());
        }

        // Stream ended — script completed
        terminalSession.setScriptCompleted(true);
        activeDeploymentSessions.remove(jobId);

        // Parse exit code from buffer
        String buffered = terminalSession.getBufferedOutput();
        int exitCode = parseExitCode(buffered);

        // Update job in database
        DeploymentJob job = jobRepository.findById(jobId).orElse(null);
        if (job != null && job.getStatus() == DeploymentStatus.RUNNING) {
            job.setStatus(exitCode == 0 ? DeploymentStatus.COMPLETED : DeploymentStatus.FAILED);
            job.setLogs(buffered);
            if (exitCode != 0) {
                job.setErrorMessage("Exit code: " + exitCode);
            }
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);
            log.info("Interactive deployment job {} finished with exit code {}", jobId, exitCode);
        }
    }

    private int parseExitCode(String output) {
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
