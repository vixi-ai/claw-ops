package com.openclaw.manager.openclawserversmanager.containerlogs.service;

import com.openclaw.manager.openclawserversmanager.containerlogs.config.ContainerLogsProperties;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerLog;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;
import com.openclaw.manager.openclawserversmanager.containerlogs.metrics.ContainerLogMetrics;
import com.openclaw.manager.openclawserversmanager.containerlogs.repository.ContainerLogRepository;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@Service
public class ContainerLogIngestService {

    private static final Logger log = LoggerFactory.getLogger(ContainerLogIngestService.class);

    private final ContainerLogRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final ContainerLogsProperties props;
    private final ContainerLogMetrics metrics;

    private final Map<ContainerService, Thread> drainers = new EnumMap<>(ContainerService.class);
    private volatile boolean running = false;

    public ContainerLogIngestService(ContainerLogRepository repository,
                                     TransactionTemplate transactionTemplate,
                                     ContainerLogsProperties props,
                                     ContainerLogMetrics metrics) {
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
        this.props = props;
        this.metrics = metrics;
    }

    public void start(Map<ContainerService, BlockingQueue<ContainerLogEvent>> queues) {
        running = true;
        for (Map.Entry<ContainerService, BlockingQueue<ContainerLogEvent>> entry : queues.entrySet()) {
            ContainerService service = entry.getKey();
            BlockingQueue<ContainerLogEvent> q = entry.getValue();
            Thread t = Thread.ofVirtual()
                    .name("container-log-ingest-" + service.name().toLowerCase())
                    .start(() -> drainLoop(service, q));
            drainers.put(service, t);
        }
        log.info("ContainerLogIngestService drainers started for {} services", drainers.size());
    }

    public void stop() {
        running = false;
        for (Thread t : drainers.values()) {
            t.interrupt();
        }
    }

    private void drainLoop(ContainerService service, BlockingQueue<ContainerLogEvent> q) {
        int batchSize = props.getBatch().getSize();
        long flushIntervalMs = props.getBatch().getFlushIntervalMs();
        List<ContainerLogEvent> buffer = new ArrayList<>(batchSize);

        while (running) {
            try {
                ContainerLogEvent first = q.take();
                buffer.add(first);
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(flushIntervalMs);

                while (buffer.size() < batchSize) {
                    long remainingNs = deadline - System.nanoTime();
                    if (remainingNs <= 0) break;
                    ContainerLogEvent next = q.poll(remainingNs, TimeUnit.NANOSECONDS);
                    if (next == null) break;
                    buffer.add(next);
                }

                flush(service, buffer);
                buffer.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (!running) break;
            } catch (RuntimeException e) {
                log.warn("Ingest drain error for service={}: {}", service, e.getMessage());
                buffer.clear();
            }
        }

        // Best-effort flush of anything left in buffer on shutdown.
        if (!buffer.isEmpty()) {
            try { flush(service, buffer); } catch (Exception ignored) {}
        }
    }

    private void flush(ContainerService service, List<ContainerLogEvent> events) {
        if (events.isEmpty()) return;
        Timer.Sample sample = Timer.start();
        try {
            List<ContainerLog> rows = new ArrayList<>(events.size());
            for (ContainerLogEvent e : events) {
                rows.add(new ContainerLog(
                        e.service(), e.containerId(), e.containerName(),
                        e.stream(), e.level(), e.message(), e.logTs()));
            }
            transactionTemplate.executeWithoutResult(status -> repository.saveAll(rows));
            metrics.recordIngested(service, rows.size());
        } finally {
            sample.stop(metrics.batchFlush());
        }
    }
}
