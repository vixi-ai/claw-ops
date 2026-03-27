package com.openclaw.manager.openclawserversmanager.deployment.mapper;

import com.openclaw.manager.openclawserversmanager.deployment.dto.CreateScriptRequest;
import com.openclaw.manager.openclawserversmanager.deployment.dto.DeploymentJobResponse;
import com.openclaw.manager.openclawserversmanager.deployment.dto.ScriptResponse;
import com.openclaw.manager.openclawserversmanager.deployment.entity.DeploymentJob;
import com.openclaw.manager.openclawserversmanager.deployment.entity.DeploymentScript;

public final class DeploymentMapper {

    private DeploymentMapper() {}

    public static ScriptResponse toScriptResponse(DeploymentScript script) {
        return new ScriptResponse(
                script.getId(),
                script.getName(),
                script.getDescription(),
                script.getScriptContent(),
                script.getScriptType(),
                script.getCreatedAt(),
                script.getUpdatedAt()
        );
    }

    public static DeploymentScript toEntity(CreateScriptRequest request) {
        DeploymentScript script = new DeploymentScript();
        script.setName(request.name());
        script.setDescription(request.description());
        script.setScriptContent(request.scriptContent());
        script.setScriptType(request.scriptType());
        return script;
    }

    public static DeploymentJobResponse toJobResponse(DeploymentJob job) {
        return new DeploymentJobResponse(
                job.getId(),
                job.getServerId(),
                job.getScriptId(),
                job.getScriptName(),
                job.getStatus(),
                job.getTriggeredBy(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getLogs(),
                job.getErrorMessage(),
                job.isInteractive(),
                job.getTerminalSessionId(),
                job.getCreatedAt()
        );
    }
}
