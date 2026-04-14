package com.openclaw.manager.openclawserversmanager.monitoring.dto;

import jakarta.validation.constraints.NotBlank;

public record AddNoteRequest(@NotBlank String content) {}
