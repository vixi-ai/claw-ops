package com.openclaw.manager.openclawserversmanager.audit.controller;

import com.openclaw.manager.openclawserversmanager.audit.dto.AuditLogFilter;
import com.openclaw.manager.openclawserversmanager.audit.dto.AuditLogResponse;
import com.openclaw.manager.openclawserversmanager.audit.dto.DeleteAuditLogsResponse;
import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "audit", description = "Audit log endpoints (ADMIN only)")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/logs")
    @Operation(summary = "List audit logs (filtered, paginated)")
    public ResponseEntity<Page<AuditLogResponse>> getLogs(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            Pageable pageable
    ) {
        AuditLogFilter filter = new AuditLogFilter(userId, action, entityType, entityId, from, to);
        return ResponseEntity.ok(auditService.getLogs(filter, pageable));
    }

    @DeleteMapping("/logs")
    @Operation(summary = "Delete all audit logs older than the given cutoff (ADMIN only)")
    public ResponseEntity<DeleteAuditLogsResponse> deleteOldLogs(@RequestParam("before") Instant before) {
        if (before == null) {
            throw new IllegalArgumentException("'before' query parameter is required");
        }
        if (!before.isBefore(Instant.now())) {
            throw new IllegalArgumentException("'before' must be a past timestamp");
        }
        long deleted = auditService.deleteOldAuditLogs(before);
        return ResponseEntity.ok(new DeleteAuditLogsResponse(deleted, before));
    }
}
