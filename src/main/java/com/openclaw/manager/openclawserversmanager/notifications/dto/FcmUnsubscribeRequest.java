package com.openclaw.manager.openclawserversmanager.notifications.dto;

import jakarta.validation.constraints.NotBlank;

public record FcmUnsubscribeRequest(
        @NotBlank String token
) {}
