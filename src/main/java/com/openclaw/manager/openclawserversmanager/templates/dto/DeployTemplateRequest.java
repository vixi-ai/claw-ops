package com.openclaw.manager.openclawserversmanager.templates.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DeployTemplateRequest(
        @NotNull UUID serverId
) {}
