package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import jakarta.validation.constraints.Size;

public record UpdateChannelRequest(
        @Size(max = 100) String name,
        Boolean enabled,
        String config
) {}
