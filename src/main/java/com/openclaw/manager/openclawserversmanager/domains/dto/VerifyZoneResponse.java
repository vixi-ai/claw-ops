package com.openclaw.manager.openclawserversmanager.domains.dto;

import java.util.List;

public record VerifyZoneResponse(
        boolean manageable,
        String message,
        List<String> warnings
) {
}
