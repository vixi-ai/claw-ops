package com.openclaw.manager.openclawserversmanager.apps.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/servers/{id}/apps/chat/install}.
 *
 * @param allowedEmail  single authorized user email (maps to {@code ALLOWED_EMAIL} env var)
 * @param apiOrigin     the Spring backend URL the chat app calls for /api/v1/auth/...;
 *                      required because silently defaulting to the chat's own hostname
 *                      produces a stack that 404s on every login.
 */
public record ChatInstallRequest(
        @NotBlank @Email String allowedEmail,
        @NotBlank String apiOrigin
) {
}
