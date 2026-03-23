package com.openclaw.manager.openclawserversmanager.secrets.mapper;

import com.openclaw.manager.openclawserversmanager.secrets.dto.EncryptedPayload;
import com.openclaw.manager.openclawserversmanager.secrets.dto.CreateSecretRequest;
import com.openclaw.manager.openclawserversmanager.secrets.dto.SecretResponse;
import com.openclaw.manager.openclawserversmanager.secrets.entity.Secret;

import java.util.UUID;

public final class SecretMapper {

    private SecretMapper() {
    }

    public static SecretResponse toResponse(Secret secret) {
        return new SecretResponse(
                secret.getId(),
                secret.getName(),
                secret.getType(),
                secret.getDescription(),
                secret.getCreatedBy(),
                secret.getCreatedAt(),
                secret.getUpdatedAt()
        );
    }

    public static Secret toEntity(CreateSecretRequest request, EncryptedPayload payload, UUID createdBy) {
        Secret secret = new Secret();
        secret.setName(request.name());
        secret.setType(request.type());
        secret.setEncryptedValue(payload.ciphertext());
        secret.setIv(payload.iv());
        secret.setDescription(request.description());
        secret.setCreatedBy(createdBy);
        return secret;
    }
}
