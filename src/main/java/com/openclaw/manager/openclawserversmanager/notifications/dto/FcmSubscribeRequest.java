package com.openclaw.manager.openclawserversmanager.notifications.dto;

import jakarta.validation.constraints.NotBlank;

public record FcmSubscribeRequest(
        @NotBlank String token,
        @NotBlank String platform
) {}
