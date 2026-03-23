package com.openclaw.manager.openclawserversmanager.domains.dto;

import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateManagedZoneRequest(
        Boolean active,
        Integer defaultTtl,
        @Size(max = 50) String environmentTag,
        Map<String, Object> metadata
) {
}
