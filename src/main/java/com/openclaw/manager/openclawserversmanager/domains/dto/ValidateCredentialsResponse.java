package com.openclaw.manager.openclawserversmanager.domains.dto;

public record ValidateCredentialsResponse(
        boolean valid,
        String message
) {
}
