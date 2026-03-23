package com.openclaw.manager.openclawserversmanager.ssh.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExecuteCommandRequest(
        @NotBlank @Size(max = 4096) String command,
        @Min(1) @Max(300) Integer timeoutSeconds
) {
}
