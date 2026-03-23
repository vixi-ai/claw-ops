package com.openclaw.manager.openclawserversmanager.domains.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclaw.manager.openclawserversmanager.domains.dto.CreateManagedZoneRequest;
import com.openclaw.manager.openclawserversmanager.domains.dto.ManagedZoneResponse;
import com.openclaw.manager.openclawserversmanager.domains.entity.ManagedZone;

import java.util.Map;

public final class ManagedZoneMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private ManagedZoneMapper() {
    }

    public static ManagedZoneResponse toResponse(ManagedZone zone) {
        return new ManagedZoneResponse(
                zone.getId(),
                zone.getZoneName(),
                zone.getProviderAccountId(),
                zone.isActive(),
                zone.isDefaultForAutoAssign(),
                zone.getDefaultTtl(),
                zone.getProviderZoneId(),
                zone.getEnvironmentTag(),
                deserializeMetadata(zone.getMetadata()),
                zone.getCreatedAt(),
                zone.getUpdatedAt()
        );
    }

    public static ManagedZone toEntity(CreateManagedZoneRequest request) {
        ManagedZone zone = new ManagedZone();
        zone.setZoneName(request.zoneName().toLowerCase());
        zone.setProviderAccountId(request.providerAccountId());
        zone.setDefaultTtl(request.defaultTtl());
        zone.setEnvironmentTag(request.environmentTag());
        zone.setMetadata(serializeMetadata(request.metadata()));
        return zone;
    }

    public static String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid metadata JSON", e);
        }
    }

    public static Map<String, Object> deserializeMetadata(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
