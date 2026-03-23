package com.openclaw.manager.openclawserversmanager.secrets.controller;

import com.openclaw.manager.openclawserversmanager.secrets.dto.CreateSecretRequest;
import com.openclaw.manager.openclawserversmanager.secrets.dto.SecretResponse;
import com.openclaw.manager.openclawserversmanager.secrets.dto.UpdateSecretRequest;
import com.openclaw.manager.openclawserversmanager.secrets.service.SecretService;
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
@RequestMapping("/api/v1/secrets")
@Tag(name = "secrets", description = "Encrypted credential storage")
@SecurityRequirement(name = "bearerAuth")
public class SecretController {

    private final SecretService secretService;

    public SecretController(SecretService secretService) {
        this.secretService = secretService;
    }

    @PostMapping
    @Operation(summary = "Create a new secret")
    public ResponseEntity<SecretResponse> createSecret(@Valid @RequestBody CreateSecretRequest request,
                                                       Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED).body(secretService.createSecret(request, userId));
    }

    @GetMapping
    @Operation(summary = "List all secrets (metadata only)")
    public ResponseEntity<Page<SecretResponse>> getAllSecrets(Pageable pageable) {
        return ResponseEntity.ok(secretService.getAllSecrets(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get secret metadata by ID")
    public ResponseEntity<SecretResponse> getSecretById(@PathVariable UUID id) {
        return ResponseEntity.ok(secretService.getSecretById(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a secret")
    public ResponseEntity<SecretResponse> updateSecret(@PathVariable UUID id,
                                                       @Valid @RequestBody UpdateSecretRequest request,
                                                       Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(secretService.updateSecret(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a secret (ADMIN only)")
    public ResponseEntity<Void> deleteSecret(@PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        secretService.deleteSecret(id, userId);
        return ResponseEntity.noContent().build();
    }
}
