package com.openclaw.manager.openclawserversmanager.domains.dto;

import com.openclaw.manager.openclawserversmanager.domains.entity.AssignmentStatus;
import com.openclaw.manager.openclawserversmanager.domains.entity.AssignmentType;
import com.openclaw.manager.openclawserversmanager.domains.entity.DnsRecordType;

import java.time.Instant;
import java.util.UUID;

public record DomainAssignmentResponse(
        UUID id,
        UUID zoneId,
        String zoneName,
        String hostname,
        DnsRecordType recordType,
        String targetValue,
        AssignmentType assignmentType,
        UUID resourceId,
        AssignmentStatus status,
        String providerRecordId,
        Instant createdAt,
        Instant updatedAt
) {
}
