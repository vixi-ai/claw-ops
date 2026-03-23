package com.openclaw.manager.openclawserversmanager.terminal.model;

public record TerminalMessage(
        String type,
        String data,
        Integer cols,
        Integer rows
) {
}
