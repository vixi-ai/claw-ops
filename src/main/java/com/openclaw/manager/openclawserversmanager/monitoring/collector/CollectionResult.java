package com.openclaw.manager.openclawserversmanager.monitoring.collector;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record CollectionResult(
    UUID serverId,
    Map<String, Double> metrics,
    Instant collectedAt,
    long durationMs,
    boolean sshReachable,
    List<String> errors
) {}
