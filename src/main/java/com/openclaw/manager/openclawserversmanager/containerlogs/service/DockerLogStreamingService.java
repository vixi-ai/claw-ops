package com.openclaw.manager.openclawserversmanager.containerlogs.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.openclaw.manager.openclawserversmanager.containerlogs.config.ContainerLogsProperties;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerLogStream;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;
import com.openclaw.manager.openclawserversmanager.containerlogs.metrics.ContainerLogMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DockerLogStreamingService {

    private static final Logger log = LoggerFactory.getLogger(DockerLogStreamingService.class);

    private final DockerClient dockerClient;
    private final ContainerLogsProperties props;
    private final LogParserService parser;
    private final ContainerLogFanout fanout;
    private final ContainerLogMetrics metrics;
    private final ContainerLogIngestService ingest;

    private final Map<ContainerService, BlockingQueue<ContainerLogEvent>> queues = new EnumMap<>(ContainerService.class);
    private final Map<ContainerService, AtomicReference<Closeable>> activeCallbacks = new EnumMap<>(ContainerService.class);
    private final Map<ContainerService, Thread> followers = new EnumMap<>(ContainerService.class);

    private volatile boolean shuttingDown = false;

    public DockerLogStreamingService(DockerClient dockerClient,
                                     ContainerLogsProperties props,
                                     LogParserService parser,
                                     ContainerLogFanout fanout,
                                     ContainerLogMetrics metrics,
                                     ContainerLogIngestService ingest) {
        this.dockerClient = dockerClient;
        this.props = props;
        this.parser = parser;
        this.fanout = fanout;
        this.metrics = metrics;
        this.ingest = ingest;
        for (ContainerService s : ContainerService.values()) {
            BlockingQueue<ContainerLogEvent> q = new ArrayBlockingQueue<>(props.getQueueCapacity());
            queues.put(s, q);
            activeCallbacks.put(s, new AtomicReference<>());
            metrics.registerQueueGauge(s, q, BlockingQueue::size);
        }
    }

    public BlockingQueue<ContainerLogEvent> queueFor(ContainerService service) {
        return queues.get(service);
    }

    @PostConstruct
    public void start() {
        ingest.start(queues);
        for (ContainerService service : ContainerService.values()) {
            Thread t = Thread.ofVirtual()
                    .name("docker-log-follower-" + service.name().toLowerCase())
                    .start(() -> followLoop(service));
            followers.put(service, t);
        }
        log.info("DockerLogStreamingService started for services={}", queues.keySet());
    }

    @PreDestroy
    public void stop() {
        shuttingDown = true;
        for (AtomicReference<Closeable> ref : activeCallbacks.values()) {
            Closeable c = ref.getAndSet(null);
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ignored) {
                    // best-effort cleanup
                }
            }
        }
        for (Thread t : followers.values()) {
            t.interrupt();
        }
        ingest.stop();
    }

    private void followLoop(ContainerService service) {
        long backoffMs = 1000;
        while (!shuttingDown) {
            String name = props.containerNameFor(service);
            try {
                InspectContainerResponse info = dockerClient.inspectContainerCmd(name).exec();
                String containerId = shortId(info.getId());
                log.info("Following logs for service={} container={} id={}", service, name, containerId);

                LogCallback cb = new LogCallback(service, containerId, name);
                activeCallbacks.get(service).set(cb);

                dockerClient.logContainerCmd(name)
                        .withFollowStream(true)
                        .withStdOut(true)
                        .withStdErr(true)
                        .withTimestamps(true)
                        .withTail(0)
                        .exec(cb);

                cb.awaitCompletion();
                backoffMs = 1000;
                if (shuttingDown) break;
                log.info("Log stream completed for service={} — reconnecting", service);
            } catch (NotFoundException e) {
                log.warn("Container not found for service={} name={} — retry in {}ms", service, name, backoffMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (shuttingDown) break;
                log.warn("Log follower error for service={}: {} — reconnecting in {}ms", service, e.getMessage(), backoffMs);
            } finally {
                Closeable cb = activeCallbacks.get(service).getAndSet(null);
                if (cb != null) {
                    try { cb.close(); } catch (Exception ignored) {}
                }
            }

            metrics.recordReconnect(service);
            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            backoffMs = Math.min(backoffMs * 2, 30_000);
        }
    }

    private void handleFrame(ContainerService service, String containerId, String containerName, Frame frame) {
        ContainerLogStream stream = frame.getStreamType() == StreamType.STDERR
                ? ContainerLogStream.STDERR
                : ContainerLogStream.STDOUT;
        String payload = new String(frame.getPayload(), StandardCharsets.UTF_8);
        // A single frame may contain multiple lines.
        for (String rawLine : payload.split("\\r?\\n", -1)) {
            if (rawLine.isEmpty()) continue;
            LogParserService.ParsedLine parsed = parser.parse(service, rawLine);
            ContainerLogEvent evt = new ContainerLogEvent(
                    service, containerId, containerName, stream, parsed.level(), parsed.message(), parsed.ts());

            // Fan out to live subscribers BEFORE attempting persistence so live tail is unaffected by ingest pressure.
            fanout.broadcast(service, evt, null);

            BlockingQueue<ContainerLogEvent> q = queues.get(service);
            if (!q.offer(evt)) {
                metrics.recordDropped(service, "queue_full", 1);
            }
        }
    }

    private static String shortId(String fullId) {
        if (fullId == null) return "";
        return fullId.length() > 12 ? fullId.substring(0, 12) : fullId;
    }

    private final class LogCallback extends ResultCallback.Adapter<Frame> {
        private final ContainerService service;
        private final String containerId;
        private final String containerName;

        LogCallback(ContainerService service, String containerId, String containerName) {
            this.service = service;
            this.containerId = containerId;
            this.containerName = containerName;
        }

        @Override
        public void onNext(Frame frame) {
            try {
                handleFrame(service, containerId, containerName, frame);
            } catch (RuntimeException e) {
                log.warn("Frame handling error for service={}: {}", service, e.getMessage());
            }
        }
    }
}
