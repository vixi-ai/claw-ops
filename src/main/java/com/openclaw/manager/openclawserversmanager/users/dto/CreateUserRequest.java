package com.openclaw.manager.openclawserversmanager.users.dto;

import com.openclaw.manager.openclawserversmanager.users.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Email
        String email,

        @NotBlank @Size(min = 3, max = 50)
        String username,

        @NotBlank @Size(min = 8, max = 128)
        String password,

        @NotNull
        Role role
) {
}
