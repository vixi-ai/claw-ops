package com.openclaw.manager.openclawserversmanager.deployment.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TriggerDeploymentRequest(
        @NotNull UUID serverId,
        @NotNull UUID scriptId,
        boolean interactive
) {}
