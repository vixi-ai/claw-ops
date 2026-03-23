package com.openclaw.manager.openclawserversmanager.users.mapper;

import com.openclaw.manager.openclawserversmanager.users.dto.UserResponse;
import com.openclaw.manager.openclawserversmanager.users.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getRole(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
