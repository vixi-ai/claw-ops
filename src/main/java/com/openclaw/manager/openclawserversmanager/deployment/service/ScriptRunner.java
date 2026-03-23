package com.openclaw.manager.openclawserversmanager.deployment.service;

import com.openclaw.manager.openclawserversmanager.deployment.entity.DeploymentJob;
import com.openclaw.manager.openclawserversmanager.deployment.entity.DeploymentStatus;
import com.openclaw.manager.openclawserversmanager.deployment.repository.DeploymentJobRepository;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.service.ServerService;
import com.openclaw.manager.openclawserversmanager.ssh.model.CommandResult;
import com.openclaw.manager.openclawserversmanager.ssh.service.SshService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Service
public class ScriptRunner {

    private static final Logger log = LoggerFactory.getLogger(ScriptRunner.class);

    private final DeploymentJobRepository jobRepository;
    private final ServerService serverService;
    private final SshService sshService;

    public ScriptRunner(DeploymentJobRepository jobRepository,
                        ServerService serverService,
                        SshService sshService) {
        this.jobRepository = jobRepository;
        this.serverService = serverService;
        this.sshService = sshService;
    }

    @Async("deploymentExecutor")
    public void run(UUID jobId, UUID serverId, String scriptContent) {
        DeploymentJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.error("Deployment job {} not found", jobId);
            return;
        }

        // If job was cancelled before we started, don't run
        if (job.getStatus() == DeploymentStatus.CANCELLED) {
            return;
        }

        job.setStatus(DeploymentStatus.RUNNING);
        job.setStartedAt(Instant.now());
        jobRepository.save(job);

        String remotePath = "/tmp/" + jobId + ".sh";
        try {
            Server server = serverService.getServerEntity(serverId);

            // Upload script
            sshService.uploadFile(server, scriptContent.getBytes(StandardCharsets.UTF_8), remotePath);

            // Execute script
            CommandResult result = sshService.executeCommand(server, "bash " + remotePath, 300);

            // Capture logs
            String output = result.stdout();
            if (output == null || output.isBlank()) {
                output = result.stderr();
            }
            job.setLogs(output);

            if (result.exitCode() == 0) {
                job.setStatus(DeploymentStatus.COMPLETED);
                log.info("Deployment job {} completed successfully", jobId);
            } else {
                job.setStatus(DeploymentStatus.FAILED);
                job.setErrorMessage("Exit code: " + result.exitCode() + "\n" + result.stderr());
                log.warn("Deployment job {} failed with exit code {}", jobId, result.exitCode());
            }
        } catch (Exception e) {
            job.setStatus(DeploymentStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            log.error("Deployment job {} failed with exception", jobId, e);
        } finally {
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);

            // Cleanup remote script
            try {
                Server server = serverService.getServerEntity(serverId);
                sshService.executeCommand(server, "rm -f " + remotePath, 10);
            } catch (Exception e) {
                log.warn("Failed to cleanup remote script for job {}: {}", jobId, e.getMessage());
            }
        }
    }
}
