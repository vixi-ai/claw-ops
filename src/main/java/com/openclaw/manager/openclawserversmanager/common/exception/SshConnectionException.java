package com.openclaw.manager.openclawserversmanager.common.exception;

public class SshConnectionException extends RuntimeException {

    public SshConnectionException(String message) {
        super(message);
    }

    public SshConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
