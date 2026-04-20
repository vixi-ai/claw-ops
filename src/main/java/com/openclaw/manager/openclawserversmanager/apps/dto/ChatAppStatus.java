package com.openclaw.manager.openclawserversmanager.apps.dto;

/**
 * Snapshot of the chat app's state on a server. Populated by probing the remote
 * {@code docker ps} output.
 *
 * @param installed   the {@code claw-chat} container exists (running or stopped)
 * @param running     the container is currently up
 * @param healthState "healthy" / "starting" / "unhealthy" / "none" / free-form string from docker
 * @param rawOutput   raw {@code docker ps} output (for debugging)
 */
public record ChatAppStatus(
        boolean installed,
        boolean running,
        String healthState,
        String rawOutput
) {
    public static ChatAppStatus notInstalled() {
        return new ChatAppStatus(false, false, "none", "");
    }
}
