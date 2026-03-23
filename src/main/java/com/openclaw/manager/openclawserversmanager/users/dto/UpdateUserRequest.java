package com.openclaw.manager.openclawserversmanager.users.dto;

import com.openclaw.manager.openclawserversmanager.users.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Email
        String email,

        @Size(min = 3, max = 50)
        String username,

        Role role,

        Boolean enabled
) {
}
