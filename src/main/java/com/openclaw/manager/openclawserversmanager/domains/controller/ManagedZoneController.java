package com.openclaw.manager.openclawserversmanager.domains.controller;

import com.openclaw.manager.openclawserversmanager.domains.dto.CreateManagedZoneRequest;
import com.openclaw.manager.openclawserversmanager.domains.dto.DomainEventResponse;
import com.openclaw.manager.openclawserversmanager.domains.dto.ManagedZoneResponse;
import com.openclaw.manager.openclawserversmanager.domains.dto.UpdateManagedZoneRequest;
import com.openclaw.manager.openclawserversmanager.domains.service.DomainEventService;
import com.openclaw.manager.openclawserversmanager.domains.service.ManagedZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/zones")
@Tag(name = "zones", description = "DNS managed zone management")
@SecurityRequirement(name = "bearerAuth")
public class ManagedZoneController {

    private final ManagedZoneService managedZoneService;
    private final DomainEventService domainEventService;

    public ManagedZoneController(ManagedZoneService managedZoneService,
                                  DomainEventService domainEventService) {
        this.managedZoneService = managedZoneService;
        this.domainEventService = domainEventService;
    }

    @PostMapping
    @Operation(summary = "Create a managed zone")
    public ResponseEntity<ManagedZoneResponse> createZone(
            @Valid @RequestBody CreateManagedZoneRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(managedZoneService.createZone(request, userId));
    }

    @GetMapping
    @Operation(summary = "List all managed zones")
    public ResponseEntity<Page<ManagedZoneResponse>> getAllZones(Pageable pageable) {
        return ResponseEntity.ok(managedZoneService.getAllZones(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get zone by ID")
    public ResponseEntity<ManagedZoneResponse> getZoneById(@PathVariable UUID id) {
        return ResponseEntity.ok(managedZoneService.getZoneById(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a managed zone")
    public ResponseEntity<ManagedZoneResponse> updateZone(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateManagedZoneRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(managedZoneService.updateZone(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a managed zone (ADMIN only)")
    public ResponseEntity<Void> deleteZone(@PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        managedZoneService.deleteZone(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Run preflight checks and activate zone")
    public ResponseEntity<ManagedZoneResponse> activateZone(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(managedZoneService.activateZone(id, userId));
    }

    @PostMapping("/{id}/set-default")
    @Operation(summary = "Set zone as default for auto-assign on server creation")
    public ResponseEntity<ManagedZoneResponse> setDefaultForAutoAssign(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(managedZoneService.setDefaultForAutoAssign(id, userId));
    }

    @GetMapping("/{id}/events")
    @Operation(summary = "Get zone event history")
    public ResponseEntity<List<DomainEventResponse>> getZoneEvents(@PathVariable UUID id) {
        return ResponseEntity.ok(domainEventService.getEventsForZone(id));
    }
}
