package com.openclaw.manager.openclawserversmanager.domains.dto;

import com.openclaw.manager.openclawserversmanager.domains.entity.DnsRecordType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AssignCustomDomainRequest(
        @NotNull UUID zoneId,
        @NotBlank @Size(max = 255) String hostname,
        @NotNull DnsRecordType recordType,
        @NotBlank @Size(max = 255) String targetValue,
        UUID resourceId
) {
}
