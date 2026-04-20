package com.openclaw.manager.openclawserversmanager.apps.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/v1/servers/{id}/apps/chat/install}.
 *
 * @param allowedEmail  single authorized user email (maps to {@code ALLOWED_EMAIL} env var)
 * @param apiOrigin     optional override for the Spring backend URL the chat app talks to;
 *                      defaults to {@code https://<server.assignedDomain>} when null/blank
 */
public record ChatInstallRequest(
        @NotBlank @Email String allowedEmail,
        String apiOrigin
) {
}
