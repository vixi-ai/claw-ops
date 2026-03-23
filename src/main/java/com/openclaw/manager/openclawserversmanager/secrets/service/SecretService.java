package com.openclaw.manager.openclawserversmanager.secrets.service;

import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.service.AuditService;
import com.openclaw.manager.openclawserversmanager.common.exception.DuplicateResourceException;
import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.secrets.dto.CreateSecretRequest;
import com.openclaw.manager.openclawserversmanager.secrets.dto.EncryptedPayload;
import com.openclaw.manager.openclawserversmanager.secrets.dto.SecretResponse;
import com.openclaw.manager.openclawserversmanager.secrets.dto.UpdateSecretRequest;
import com.openclaw.manager.openclawserversmanager.secrets.entity.Secret;
import com.openclaw.manager.openclawserversmanager.secrets.mapper.SecretMapper;
import com.openclaw.manager.openclawserversmanager.secrets.repository.SecretRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SecretService {

    private final SecretRepository secretRepository;
    private final EncryptionService encryptionService;
    private final AuditService auditService;

    public SecretService(SecretRepository secretRepository, EncryptionService encryptionService,
                         AuditService auditService) {
        this.secretRepository = secretRepository;
        this.encryptionService = encryptionService;
        this.auditService = auditService;
    }

    @Transactional
    public SecretResponse createSecret(CreateSecretRequest request, UUID userId) {
        if (secretRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("Secret with name '" + request.name() + "' already exists");
        }

        EncryptedPayload payload = encryptionService.encrypt(request.value());
        Secret secret = SecretMapper.toEntity(request, payload, userId);
        Secret saved = secretRepository.save(secret);

        try {
            auditService.log(AuditAction.SECRET_CREATED, "SECRET", saved.getId(), userId,
                    "Secret '%s' created (type: %s)".formatted(saved.getName(), saved.getType()));
        } catch (Exception ignored) {
        }

        return SecretMapper.toResponse(saved);
    }

    public Page<SecretResponse> getAllSecrets(Pageable pageable) {
        return secretRepository.findAll(pageable).map(SecretMapper::toResponse);
    }

    public SecretResponse getSecretById(UUID id) {
        Secret secret = findSecretOrThrow(id);
        return SecretMapper.toResponse(secret);
    }

    @Transactional
    public SecretResponse updateSecret(UUID id, UpdateSecretRequest request, UUID userId) {
        Secret secret = findSecretOrThrow(id);

        if (request.name() != null && !request.name().equals(secret.getName())) {
            if (secretRepository.existsByName(request.name())) {
                throw new DuplicateResourceException("Secret with name '" + request.name() + "' already exists");
            }
            secret.setName(request.name());
        }

        if (request.value() != null && !request.value().isBlank()) {
            EncryptedPayload payload = encryptionService.encrypt(request.value());
            secret.setEncryptedValue(payload.ciphertext());
            secret.setIv(payload.iv());
        }

        if (request.description() != null) {
            secret.setDescription(request.description());
        }

        Secret saved = secretRepository.save(secret);

        try {
            auditService.log(AuditAction.SECRET_UPDATED, "SECRET", saved.getId(), userId,
                    "Secret '%s' updated".formatted(saved.getName()));
        } catch (Exception ignored) {
        }

        return SecretMapper.toResponse(saved);
    }

    @Transactional
    public void deleteSecret(UUID id, UUID userId) {
        Secret secret = findSecretOrThrow(id);
        String name = secret.getName();
        secretRepository.delete(secret);

        try {
            auditService.log(AuditAction.SECRET_DELETED, "SECRET", id, userId,
                    "Secret '%s' deleted".formatted(name));
        } catch (Exception ignored) {
        }
    }

    public String decryptSecret(UUID id) {
        Secret secret = findSecretOrThrow(id);
        return encryptionService.decrypt(secret.getEncryptedValue(), secret.getIv());
    }

    private Secret findSecretOrThrow(UUID id) {
        return secretRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Secret with id " + id + " not found"));
    }
}
