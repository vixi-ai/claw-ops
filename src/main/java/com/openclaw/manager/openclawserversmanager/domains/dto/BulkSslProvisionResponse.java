package com.openclaw.manager.openclawserversmanager.domains.dto;

public record BulkSslProvisionResponse(int total, int provisioned, int skipped, int failed) {}
