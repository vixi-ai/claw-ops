package com.openclaw.manager.openclawserversmanager.deployment.controller;

import com.openclaw.manager.openclawserversmanager.deployment.dto.CreateScriptRequest;
import com.openclaw.manager.openclawserversmanager.deployment.dto.ScriptResponse;
import com.openclaw.manager.openclawserversmanager.deployment.dto.UpdateScriptRequest;
import com.openclaw.manager.openclawserversmanager.deployment.service.DeploymentScriptService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deployment-scripts")
@Tag(name = "Deployment Scripts", description = "Bash script library for deployments")
@SecurityRequirement(name = "bearerAuth")
public class DeploymentScriptController {

    private final DeploymentScriptService scriptService;

    public DeploymentScriptController(DeploymentScriptService scriptService) {
        this.scriptService = scriptService;
    }

    @PostMapping
    @Operation(summary = "Create a deployment script")
    public ResponseEntity<ScriptResponse> createScript(@Valid @RequestBody CreateScriptRequest request,
                                                       Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scriptService.createScript(request, userId));
    }

    @GetMapping
    @Operation(summary = "List all deployment scripts (paginated)")
    public ResponseEntity<Page<ScriptResponse>> getAllScripts(Pageable pageable) {
        return ResponseEntity.ok(scriptService.getAllScripts(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a deployment script by ID")
    public ResponseEntity<ScriptResponse> getScript(@PathVariable UUID id) {
        return ResponseEntity.ok(scriptService.getScript(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a deployment script")
    public ResponseEntity<ScriptResponse> updateScript(@PathVariable UUID id,
                                                       @Valid @RequestBody UpdateScriptRequest request,
                                                       Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(scriptService.updateScript(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a deployment script")
    public ResponseEntity<Void> deleteScript(@PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        scriptService.deleteScript(id, userId);
        return ResponseEntity.noContent().build();
    }
}
