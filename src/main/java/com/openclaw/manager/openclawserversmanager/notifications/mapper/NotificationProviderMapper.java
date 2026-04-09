package com.openclaw.manager.openclawserversmanager.notifications.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.manager.openclawserversmanager.notifications.dto.CreateNotificationProviderRequest;
import com.openclaw.manager.openclawserversmanager.notifications.dto.NotificationProviderResponse;
import com.openclaw.manager.openclawserversmanager.notifications.entity.NotificationProvider;

import java.util.Map;

public final class NotificationProviderMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private NotificationProviderMapper() {}

    public static NotificationProviderResponse toResponse(NotificationProvider entity) {
        return new NotificationProviderResponse(
                entity.getId(),
                entity.getProviderType(),
                entity.getDisplayName(),
                entity.isEnabled(),
                entity.isDefault(),
                entity.getCredentialId(),
                deserializeSettings(entity.getProviderSettings()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public static NotificationProvider toEntity(CreateNotificationProviderRequest request) {
        NotificationProvider entity = new NotificationProvider();
        entity.setProviderType(request.providerType());
        entity.setDisplayName(request.displayName());
        entity.setCredentialId(request.credentialId());
        entity.setProviderSettings(serializeSettings(request.providerSettings()));
        return entity;
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
