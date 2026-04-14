package com.openclaw.manager.openclawserversmanager.monitoring.controller;

import com.openclaw.manager.openclawserversmanager.monitoring.dto.*;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.EndpointCheck;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.EndpointCheckResult;
import com.openclaw.manager.openclawserversmanager.monitoring.scheduler.EndpointCheckScheduler;
import com.openclaw.manager.openclawserversmanager.monitoring.service.EndpointCheckService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/monitoring/endpoints")
@SecurityRequirement(name = "bearerAuth")
public class EndpointCheckController {

    private final EndpointCheckService checkService;
    private final EndpointCheckScheduler checkScheduler;

    public EndpointCheckController(EndpointCheckService checkService,
                                    EndpointCheckScheduler checkScheduler) {
        this.checkService = checkService;
        this.checkScheduler = checkScheduler;
    }

    @GetMapping
    public ResponseEntity<?> listChecks(
            @RequestParam(required = false) UUID serverId,
            @RequestParam(required = false) Boolean enabled) {
        return ResponseEntity.ok(checkService.listChecks(serverId, enabled).stream()
                .map(c -> EndpointCheckResponse.from(c, checkService.getLatestResult(c.getId())))
                .toList());
    }

    @PostMapping
    public ResponseEntity<EndpointCheckResponse> createCheck(@Valid @RequestBody CreateEndpointCheckRequest req) {
        EndpointCheck check = checkService.createCheck(
                req.name(), req.url(), req.checkType(),
                req.serverId(), req.expectedStatusCode(), req.intervalSeconds());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                EndpointCheckResponse.from(check, null));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EndpointCheckResponse> getCheck(@PathVariable UUID id) {
        EndpointCheck check = checkService.getCheck(id);
        return ResponseEntity.ok(EndpointCheckResponse.from(check, checkService.getLatestResult(id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<EndpointCheckResponse> updateCheck(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> updates) {
        EndpointCheck check = checkService.updateCheck(id,
                (String) updates.get("name"),
                (String) updates.get("url"),
                updates.containsKey("enabled") ? (Boolean) updates.get("enabled") : null,
                updates.containsKey("expectedStatusCode") ? (Integer) updates.get("expectedStatusCode") : null,
                updates.containsKey("intervalSeconds") ? (Integer) updates.get("intervalSeconds") : null);
        return ResponseEntity.ok(EndpointCheckResponse.from(check, checkService.getLatestResult(id)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCheck(@PathVariable UUID id) {
        checkService.deleteCheck(id);
    }

    @GetMapping("/{id}/results")
    public ResponseEntity<Page<EndpointCheckResponse.LatestResult>> getResults(
            @PathVariable UUID id,
            Pageable pageable) {
        Page<EndpointCheckResult> results = checkService.getResults(id, pageable);
        return ResponseEntity.ok(results.map(EndpointCheckResponse.LatestResult::from));
    }

    @PostMapping("/{id}/check")
    public ResponseEntity<EndpointCheckResponse.LatestResult> triggerCheck(@PathVariable UUID id) {
        EndpointCheck check = checkService.getCheck(id);
        EndpointCheckResult result = checkScheduler.executeCheck(check, Instant.now());
        checkService.saveResult(result);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(EndpointCheckResponse.LatestResult.from(result));
    }
}
