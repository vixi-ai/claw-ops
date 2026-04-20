package com.openclaw.manager.openclawserversmanager.apps.dto;

/**
 * Result of running the chat-app installer on a server.
 *
 * @param exitCode    the install script's exit code (0 = success)
 * @param output      combined stdout + stderr from the install, truncated to a safe max
 * @param durationMs  end-to-end wall time
 */
public record ChatInstallResult(
        int exitCode,
        String output,
        long durationMs
) {
}
