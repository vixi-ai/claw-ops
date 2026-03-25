package com.openclaw.manager.openclawserversmanager.common.exception;

public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
