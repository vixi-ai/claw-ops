package com.openclaw.manager.openclawserversmanager.auth.dto;

import com.openclaw.manager.openclawserversmanager.users.entity.Role;

import java.util.UUID;

public record UserInfoResponse(
        UUID id,
        String email,
        String username,
        Role role
) {
}
