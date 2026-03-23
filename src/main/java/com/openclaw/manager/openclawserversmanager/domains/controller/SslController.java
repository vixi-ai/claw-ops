package com.openclaw.manager.openclawserversmanager.domains.controller;

import com.openclaw.manager.openclawserversmanager.domains.dto.BulkSslProvisionResponse;
import com.openclaw.manager.openclawserversmanager.domains.dto.ProvisionSslRequest;
import com.openclaw.manager.openclawserversmanager.domains.dto.SslCertificateResponse;
import com.openclaw.manager.openclawserversmanager.domains.entity.AssignmentStatus;
import com.openclaw.manager.openclawserversmanager.domains.entity.DomainAssignment;
import com.openclaw.manager.openclawserversmanager.domains.exception.DomainException;
import com.openclaw.manager.openclawserversmanager.domains.repository.DomainAssignmentRepository;
import com.openclaw.manager.openclawserversmanager.domains.service.SslService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ssl-certificates")
@Tag(name = "SSL Certificates", description = "SSL certificate provisioning and management")
public class SslController {

    private final SslService sslService;
    private final DomainAssignmentRepository domainAssignmentRepository;

    public SslController(SslService sslService,
                         DomainAssignmentRepository domainAssignmentRepository) {
        this.sslService = sslService;
        this.domainAssignmentRepository = domainAssignmentRepository;
    }

    @PostMapping
    @Operation(summary = "Provision SSL certificate for a server")
    public ResponseEntity<SslCertificateResponse> provision(
            @Valid @RequestBody ProvisionSslRequest request, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();

        // Find active domain assignment for the server
        List<DomainAssignment> assignments = domainAssignmentRepository
                .findByResourceIdAndStatusNot(request.serverId(), AssignmentStatus.RELEASED);

        if (assignments.isEmpty()) {
            throw new DomainException("No active domain assignment found for server. Assign a domain first.");
        }

        DomainAssignment assignment = assignments.getFirst();
        return ResponseEntity.ok(sslService.provision(
                request.serverId(), assignment.getId(), assignment.getHostname(),
                request.targetPort(), userId));
    }

    @GetMapping
    @Operation(summary = "List all SSL certificates")
    public ResponseEntity<Page<SslCertificateResponse>> getAll(Pageable pageable) {
        return ResponseEntity.ok(sslService.getAllCertificates(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get SSL certificate by ID")
    public ResponseEntity<SslCertificateResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(sslService.getCertificate(id));
    }

    @GetMapping("/server/{serverId}")
    @Operation(summary = "Get SSL certificate for a server")
    public ResponseEntity<SslCertificateResponse> getForServer(@PathVariable UUID serverId) {
        return sslService.getCertificateForServer(serverId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/renew")
    @Operation(summary = "Renew SSL certificate")
    public ResponseEntity<SslCertificateResponse> renew(@PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(sslService.renew(id, userId));
    }

    @PostMapping("/{id}/check")
    @Operation(summary = "Check SSL certificate status")
    public ResponseEntity<SslCertificateResponse> check(@PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(sslService.check(id, userId));
    }

    @PostMapping("/provision-all")
    @Operation(summary = "Provision SSL for all servers that have a domain but no active certificate")
    public ResponseEntity<BulkSslProvisionResponse> provisionAll(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(sslService.provisionMissingForAll(userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove SSL certificate")
    public ResponseEntity<Void> remove(@PathVariable UUID id, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        sslService.remove(id, userId);
        return ResponseEntity.noContent().build();
    }
}
