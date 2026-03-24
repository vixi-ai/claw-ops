package com.openclaw.manager.openclawserversmanager.users.dto;

import com.openclaw.manager.openclawserversmanager.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank @StrongPassword
        String newPassword
) {
}
