package com.openclaw.manager.openclawserversmanager.containerlogs.specification;

import com.openclaw.manager.openclawserversmanager.containerlogs.dto.ContainerLogFilter;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerLog;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerLogLevel;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerLogStream;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public final class ContainerLogSpecification {

    private ContainerLogSpecification() {
    }

    public static Specification<ContainerLog> withFilter(ContainerLogFilter filter) {
        Specification<ContainerLog> spec = (root, query, cb) -> cb.conjunction();
        if (filter.service() != null) spec = spec.and(hasService(filter.service()));
        if (filter.level() != null) spec = spec.and(hasLevel(filter.level()));
        if (filter.stream() != null) spec = spec.and(hasStream(filter.stream()));
        if (filter.from() != null) spec = spec.and(after(filter.from()));
        if (filter.to() != null) spec = spec.and(before(filter.to()));
        if (filter.search() != null && !filter.search().isBlank()) spec = spec.and(messageLike(filter.search()));
        return spec;
    }

    private static Specification<ContainerLog> hasService(ContainerService service) {
        return (root, query, cb) -> cb.equal(root.get("service"), service);
    }

    private static Specification<ContainerLog> hasLevel(ContainerLogLevel level) {
        return (root, query, cb) -> cb.equal(root.get("level"), level);
    }

    private static Specification<ContainerLog> hasStream(ContainerLogStream stream) {
        return (root, query, cb) -> cb.equal(root.get("stream"), stream);
    }

    private static Specification<ContainerLog> after(Instant from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("logTs"), from);
    }

    private static Specification<ContainerLog> before(Instant to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("logTs"), to);
    }

    private static Specification<ContainerLog> messageLike(String search) {
        String pattern = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("message")), pattern);
    }
}
