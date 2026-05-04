package com.openclaw.manager.openclawserversmanager.containerlogs.controller;

import com.openclaw.manager.openclawserversmanager.containerlogs.dto.RetentionSettingDto;
import com.openclaw.manager.openclawserversmanager.containerlogs.dto.UpdateRetentionRequest;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;
import com.openclaw.manager.openclawserversmanager.containerlogs.service.RetentionSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/container-logs/retention")
@Tag(name = "container-logs", description = "Per-service retention settings (ADMIN only)")
@SecurityRequirement(name = "bearerAuth")
public class ContainerLogsRetentionController {

    private final RetentionSettingsService retentionService;

    public ContainerLogsRetentionController(RetentionSettingsService retentionService) {
        this.retentionService = retentionService;
    }

    @GetMapping
    @Operation(summary = "List retention settings for all services")
    public ResponseEntity<List<RetentionSettingDto>> getAll() {
        return ResponseEntity.ok(retentionService.getAll());
    }

    @PatchMapping("/{service}")
    @Operation(summary = "Update retention days for a service")
    public ResponseEntity<RetentionSettingDto> update(
            @PathVariable ContainerService service,
            @Valid @RequestBody UpdateRetentionRequest request,
            Authentication authentication
    ) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(retentionService.update(service, request.retentionDays(), userId));
    }
}
