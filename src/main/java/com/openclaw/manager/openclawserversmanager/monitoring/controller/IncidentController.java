package com.openclaw.manager.openclawserversmanager.monitoring.controller;

import com.openclaw.manager.openclawserversmanager.monitoring.dto.*;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.IncidentEvent;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.IncidentStatus;
import com.openclaw.manager.openclawserversmanager.monitoring.service.IncidentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/monitoring/incidents")
@SecurityRequirement(name = "bearerAuth")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    public ResponseEntity<Page<IncidentResponse>> listIncidents(
            @RequestParam(required = false) UUID serverId,
            @RequestParam(required = false) IncidentStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(
                incidentService.listIncidents(serverId, status, pageable)
                        .map(IncidentResponse::from));
    }

    @PostMapping
    public ResponseEntity<IncidentResponse> createIncident(
            @Valid @RequestBody CreateIncidentRequest req,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                IncidentResponse.from(incidentService.createIncident(
                        req.title(), req.description(), req.serverId(), req.severity(), userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentResponse> getIncident(@PathVariable UUID id) {
        return ResponseEntity.ok(IncidentResponse.from(incidentService.getIncident(id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<IncidentResponse> updateIncident(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateIncidentRequest req) {
        return ResponseEntity.ok(IncidentResponse.from(
                incidentService.update(id, req.title(), req.description(), req.severity())));
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<List<IncidentEventResponse>> getTimeline(@PathVariable UUID id) {
        return ResponseEntity.ok(
                incidentService.getTimeline(id).stream()
                        .map(IncidentEventResponse::from)
                        .toList());
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<IncidentResponse> acknowledge(@PathVariable UUID id, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(IncidentResponse.from(incidentService.acknowledge(id, userId)));
    }

    @PostMapping("/{id}/investigate")
    public ResponseEntity<IncidentResponse> investigate(@PathVariable UUID id, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(IncidentResponse.from(incidentService.investigate(id, userId)));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<IncidentResponse> resolve(
            @PathVariable UUID id,
            @RequestBody(required = false) ResolveIncidentRequest req,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        String rootCause = req != null ? req.rootCause() : null;
        return ResponseEntity.ok(IncidentResponse.from(incidentService.resolve(id, userId, rootCause)));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<IncidentResponse> close(@PathVariable UUID id, Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(IncidentResponse.from(incidentService.close(id, userId)));
    }

    @PostMapping("/{id}/events")
    public ResponseEntity<IncidentEventResponse> addNote(
            @PathVariable UUID id,
            @Valid @RequestBody AddNoteRequest req,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        IncidentEvent event = incidentService.addNote(id, userId, req.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(IncidentEventResponse.from(event));
    }

    @PostMapping("/{id}/link-alert/{alertEventId}")
    public ResponseEntity<IncidentResponse> linkAlert(
            @PathVariable UUID id,
            @PathVariable UUID alertEventId,
            Authentication auth) {
        UUID userId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(IncidentResponse.from(incidentService.linkAlert(id, alertEventId, userId)));
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> countOpen() {
        return ResponseEntity.ok(Map.of("open", incidentService.countOpen()));
    }
}
