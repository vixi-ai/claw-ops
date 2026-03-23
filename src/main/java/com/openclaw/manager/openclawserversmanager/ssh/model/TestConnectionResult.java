package com.openclaw.manager.openclawserversmanager.ssh.model;

public record TestConnectionResult(boolean success, String message, long latencyMs) {
}
