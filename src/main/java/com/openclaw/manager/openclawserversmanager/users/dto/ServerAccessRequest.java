package com.openclaw.manager.openclawserversmanager.users.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ServerAccessRequest(
        @NotNull List<UUID> serverIds
) {}
