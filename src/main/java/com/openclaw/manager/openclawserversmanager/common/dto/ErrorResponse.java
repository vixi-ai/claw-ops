package com.openclaw.manager.openclawserversmanager.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String error,
        String message,
        List<FieldError> messages,
        Instant timestamp
) {

    public record FieldError(String field, String message) {
    }

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, null, Instant.now());
    }

    public static ErrorResponse ofValidation(int status, String error, List<FieldError> messages) {
        return new ErrorResponse(status, error, null, messages, Instant.now());
    }
}
