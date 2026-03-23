package com.openclaw.manager.openclawserversmanager.users.dto;

import com.openclaw.manager.openclawserversmanager.users.entity.Role;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String username,
        Role role,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
