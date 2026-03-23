package com.openclaw.manager.openclawserversmanager.domains.mapper;

import com.openclaw.manager.openclawserversmanager.domains.dto.SslCertificateResponse;
import com.openclaw.manager.openclawserversmanager.domains.entity.SslCertificate;

public final class SslCertificateMapper {

    private SslCertificateMapper() {
    }

    public static SslCertificateResponse toResponse(SslCertificate cert) {
        return new SslCertificateResponse(
                cert.getId(),
                cert.getServerId(),
                cert.getAssignmentId(),
                cert.getHostname(),
                cert.getStatus(),
                cert.getAdminEmail(),
                cert.getTargetPort(),
                cert.getExpiresAt(),
                cert.getLastRenewedAt(),
                cert.getLastError(),
                cert.getCreatedAt(),
                cert.getUpdatedAt()
        );
    }
}
