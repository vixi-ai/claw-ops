package com.openclaw.manager.openclawserversmanager.ssh.model;

public record CommandResult(int exitCode, String stdout, String stderr, long durationMs) {
}
