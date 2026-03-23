package com.openclaw.manager.openclawserversmanager.templates.controller;

import com.openclaw.manager.openclawserversmanager.templates.dto.CreateTemplateRequest;
import com.openclaw.manager.openclawserversmanager.templates.dto.DeployTemplateRequest;
import com.openclaw.manager.openclawserversmanager.templates.dto.DeployTemplateResponse;
import com.openclaw.manager.openclawserversmanager.templates.dto.TemplateResponse;
import com.openclaw.manager.openclawserversmanager.templates.dto.UpdateTemplateRequest;
import com.openclaw.manager.openclawserversmanager.templates.service.TemplateService;
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
@RequestMapping("/api/v1/agent-templates")
@Tag(name = "Agent Templates", description = "Agent template provisioning")
@SecurityRequirement(name = "bearerAuth")
public class AgentTemplateController {

    private final TemplateService templateService;

    public AgentTemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping
    @Operation(summary = "Create an agent template")
    public ResponseEntity<TemplateResponse> createTemplate(@Valid @RequestBody CreateTemplateRequest request,
                                                           Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateService.createTemplate(request, userId));
    }

    @GetMapping
    @Operation(summary = "List all agent templates")
    public ResponseEntity<Page<TemplateResponse>> getAllTemplates(Pageable pageable) {
        return ResponseEntity.ok(templateService.getAllTemplates(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an agent template by ID")
    public ResponseEntity<TemplateResponse> getTemplate(@PathVariable UUID id) {
        return ResponseEntity.ok(templateService.getTemplate(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update an agent template")
    public ResponseEntity<TemplateResponse> updateTemplate(@PathVariable UUID id,
                                                           @Valid @RequestBody UpdateTemplateRequest request,
                                                           Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(templateService.updateTemplate(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an agent template")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        templateService.deleteTemplate(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deploy")
    @Operation(summary = "Deploy an agent template to a server")
    public ResponseEntity<DeployTemplateResponse> deployTemplate(@PathVariable UUID id,
                                                                  @Valid @RequestBody DeployTemplateRequest request,
                                                                  Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateService.deployTemplate(id, request.serverId(), userId));
    }
}
