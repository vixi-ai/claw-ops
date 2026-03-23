package com.openclaw.manager.openclawserversmanager.domains.exception;

public class DnsProviderException extends DomainException {

    private final String providerCorrelationId;

    public DnsProviderException(String message, String providerCorrelationId) {
        super(message);
        this.providerCorrelationId = providerCorrelationId;
    }

    public DnsProviderException(String message, String providerCorrelationId, Throwable cause) {
        super(message, cause);
        this.providerCorrelationId = providerCorrelationId;
    }

    public String getProviderCorrelationId() {
        return providerCorrelationId;
    }
}
