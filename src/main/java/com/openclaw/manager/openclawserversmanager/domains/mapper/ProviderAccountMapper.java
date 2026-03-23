package com.openclaw.manager.openclawserversmanager.domains.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.manager.openclawserversmanager.domains.dto.CreateProviderAccountRequest;
import com.openclaw.manager.openclawserversmanager.domains.dto.ProviderAccountResponse;
import com.openclaw.manager.openclawserversmanager.domains.entity.ProviderAccount;

import java.util.Map;

public final class ProviderAccountMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private ProviderAccountMapper() {
    }

    public static ProviderAccountResponse toResponse(ProviderAccount account) {
        return new ProviderAccountResponse(
                account.getId(),
                account.getProviderType(),
                account.getDisplayName(),
                account.isEnabled(),
                account.getCredentialId(),
                deserializeSettings(account.getProviderSettings()),
                account.getHealthStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    public static ProviderAccount toEntity(CreateProviderAccountRequest request) {
        ProviderAccount account = new ProviderAccount();
        account.setProviderType(request.providerType());
        account.setDisplayName(request.displayName());
        account.setCredentialId(request.credentialId());
        account.setProviderSettings(serializeSettings(request.providerSettings()));
        return account;
    }

    public static String serializeSettings(Map<String, Object> settings) {
        if (settings == null || settings.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(settings);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid provider settings JSON", e);
        }
    }

    public static Map<String, Object> deserializeSettings(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
