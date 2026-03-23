package com.openclaw.manager.openclawserversmanager.audit.specification;

import com.openclaw.manager.openclawserversmanager.audit.dto.AuditLogFilter;
import com.openclaw.manager.openclawserversmanager.audit.entity.AuditAction;
import com.openclaw.manager.openclawserversmanager.audit.entity.AuditLog;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class AuditLogSpecification {

    private AuditLogSpecification() {
    }

    public static Specification<AuditLog> withFilter(AuditLogFilter filter) {
        Specification<AuditLog> spec = (root, query, cb) -> cb.conjunction();
        if (filter.userId() != null) spec = spec.and(hasUserId(filter.userId()));
        if (filter.action() != null) spec = spec.and(hasAction(filter.action()));
        if (filter.entityType() != null) spec = spec.and(hasEntityType(filter.entityType()));
        if (filter.entityId() != null) spec = spec.and(hasEntityId(filter.entityId()));
        if (filter.from() != null) spec = spec.and(createdAfter(filter.from()));
        if (filter.to() != null) spec = spec.and(createdBefore(filter.to()));
        return spec;
    }

    private static Specification<AuditLog> hasUserId(UUID userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    private static Specification<AuditLog> hasAction(AuditAction action) {
        return (root, query, cb) -> cb.equal(root.get("action"), action);
    }

    private static Specification<AuditLog> hasEntityType(String entityType) {
        return (root, query, cb) -> cb.equal(root.get("entityType"), entityType);
    }

    private static Specification<AuditLog> hasEntityId(UUID entityId) {
        return (root, query, cb) -> cb.equal(root.get("entityId"), entityId);
    }

    private static Specification<AuditLog> createdAfter(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    private static Specification<AuditLog> createdBefore(Instant to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
