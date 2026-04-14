package com.openclaw.manager.openclawserversmanager.terminal.dto;

import com.openclaw.manager.openclawserversmanager.deployment.dto.DeploymentJobResponse;

import java.util.List;

public record ProcessMonitorResponse(
        List<ActiveSessionInfo> activeSessions,
        List<DeploymentJobResponse> runningJobs,
        int totalSessions,
        int totalRunningJobs
) {}
