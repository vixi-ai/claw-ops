package com.openclaw.manager.openclawserversmanager.containerlogs.metrics;

import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToDoubleFunction;

@Component
public class ContainerLogMetrics {

    private final MeterRegistry registry;
    private final Map<ContainerService, Counter> ingested = new EnumMap<>(ContainerService.class);
    private final Map<String, Counter> dropped = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<ContainerService, Counter> retentionDeleted = new EnumMap<>(ContainerService.class);
    private final Map<ContainerService, Counter> reconnects = new EnumMap<>(ContainerService.class);
    private final Timer batchFlush;

    public ContainerLogMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (ContainerService s : ContainerService.values()) {
            ingested.put(s, Counter.builder("container_logs.ingested")
                    .tag("service", s.name()).register(registry));
            retentionDeleted.put(s, Counter.builder("container_logs.retention_deleted")
                    .tag("service", s.name()).register(registry));
            reconnects.put(s, Counter.builder("container_logs.docker_reconnects")
                    .tag("service", s.name()).register(registry));
        }
        this.batchFlush = Timer.builder("container_logs.batch_flush")
                .register(registry);
    }

    public void recordIngested(ContainerService service, int count) {
        ingested.get(service).increment(count);
    }

    public void recordDropped(ContainerService service, String reason, int count) {
        String key = service.name() + ":" + reason;
        dropped.computeIfAbsent(key, k -> Counter.builder("container_logs.dropped")
                .tag("service", service.name())
                .tag("reason", reason)
                .register(registry)).increment(count);
    }

    public Timer batchFlush() {
        return batchFlush;
    }

    public void recordRetentionDelete(ContainerService service, long deleted) {
        retentionDeleted.get(service).increment(deleted);
    }

    public void recordReconnect(ContainerService service) {
        reconnects.get(service).increment();
    }

    public <T> void registerQueueGauge(ContainerService service, T owner, ToDoubleFunction<T> measure) {
        registry.gauge("container_logs.queue_size", java.util.List.of(io.micrometer.core.instrument.Tag.of("service", service.name())),
                owner, measure);
    }

    public void registerSubscriberGauge(ContainerService service, AtomicInteger value) {
        registry.gauge("container_logs.ws_subscribers",
                java.util.List.of(io.micrometer.core.instrument.Tag.of("service", service.name())),
                value, AtomicInteger::doubleValue);
    }
}
