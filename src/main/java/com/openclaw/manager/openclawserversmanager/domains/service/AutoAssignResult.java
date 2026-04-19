package com.openclaw.manager.openclawserversmanager.domains.service;

import com.openclaw.manager.openclawserversmanager.domains.dto.DomainAssignmentResponse;
import com.openclaw.manager.openclawserversmanager.domains.dto.DomainJobResponse;

/**
 * Result of an auto/manual domain assignment trigger. Includes the persisted assignment
 * and the async job that will populate DNS state. Callers (e.g. ServerService) surface
 * the job id to the client so the frontend can poll without a round-trip to find it.
 */
public record AutoAssignResult(
        DomainAssignmentResponse assignment,
        DomainJobResponse job
) {
}
