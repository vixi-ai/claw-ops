package com.openclaw.manager.openclawserversmanager.domains.controller;

import com.openclaw.manager.openclawserversmanager.domains.dto.CreateProviderAccountRequest;
import com.openclaw.manager.openclawserversmanager.domains.dto.ProviderAccountResponse;
import com.openclaw.manager.openclawserversmanager.domains.dto.UpdateProviderAccountRequest;
import com.openclaw.manager.openclawserversmanager.domains.dto.SyncDomainsResponse;
import com.openclaw.manager.openclawserversmanager.domains.dto.ValidateCredentialsResponse;
import com.openclaw.manager.openclawserversmanager.domains.provider.ProviderCapabilities;
import com.openclaw.manager.openclawserversmanager.domains.service.ProviderAccountService;
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
@RequestMapping("/api/v1/provider-accounts")
@Tag(name = "provider-accounts", description = "DNS provider account management")
@SecurityRequirement(name = "bearerAuth")
public class ProviderAccountController {

    private final ProviderAccountService providerAccountService;

    public ProviderAccountController(ProviderAccountService providerAccountService) {
        this.providerAccountService = providerAccountService;
    }

    @PostMapping
    @Operation(summary = "Create a provider account")
    public ResponseEntity<ProviderAccountResponse> createAccount(
            @Valid @RequestBody CreateProviderAccountRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(providerAccountService.createAccount(request, userId));
    }

    @GetMapping
    @Operation(summary = "List all provider accounts")
    public ResponseEntity<Page<ProviderAccountResponse>> getAllAccounts(Pageable pageable) {
        return ResponseEntity.ok(providerAccountService.getAllAccounts(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get provider account by ID")
    public ResponseEntity<ProviderAccountResponse> getAccountById(@PathVariable UUID id) {
        return ResponseEntity.ok(providerAccountService.getAccountById(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a provider account")
    public ResponseEntity<ProviderAccountResponse> updateAccount(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProviderAccountRequest request,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(providerAccountService.updateAccount(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a provider account (ADMIN only)")
    public ResponseEntity<Void> deleteAccount(@PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        providerAccountService.deleteAccount(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/validate")
    @Operation(summary = "Validate provider credentials")
    public ResponseEntity<ValidateCredentialsResponse> validateCredentials(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(providerAccountService.validateCredentials(id, userId));
    }

    @PostMapping("/{id}/sync-domains")
    @Operation(summary = "Sync domains from provider account")
    public ResponseEntity<SyncDomainsResponse> syncDomains(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(providerAccountService.syncDomainsForAccount(id, userId));
    }

    @GetMapping("/{id}/capabilities")
    @Operation(summary = "Get provider capabilities")
    public ResponseEntity<ProviderCapabilities> getCapabilities(@PathVariable UUID id) {
        return ResponseEntity.ok(providerAccountService.getCapabilities(id));
    }
}
