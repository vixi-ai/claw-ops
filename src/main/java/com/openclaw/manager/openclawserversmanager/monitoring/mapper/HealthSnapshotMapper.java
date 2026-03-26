package com.openclaw.manager.openclawserversmanager.monitoring.mapper;

import com.openclaw.manager.openclawserversmanager.monitoring.dto.ServerHealthSummary;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.HealthSnapshot;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;

public class HealthSnapshotMapper {

    private HealthSnapshotMapper() {}

    public static ServerHealthSummary toSummary(HealthSnapshot snapshot, Server server) {
        return new ServerHealthSummary(
                server.getId(),
                server.getName(),
                server.getHostname(),
                server.getEnvironment(),
                snapshot.getOverallState(),
                snapshot.getCpuState(),
                snapshot.getMemoryState(),
                snapshot.getDiskState(),
                snapshot.getCpuUsage(),
                snapshot.getMemoryUsage(),
                snapshot.getDiskUsage(),
                snapshot.getLoad1m(),
                snapshot.getUptimeSeconds(),
                snapshot.getProcessCount(),
                snapshot.isSshReachable(),
                snapshot.getLastCheckAt(),
                snapshot.getStateChangedAt()
        );
    }

    public static ServerHealthSummary toSummaryNoSnapshot(Server server) {
        return new ServerHealthSummary(
                server.getId(),
                server.getName(),
                server.getHostname(),
                server.getEnvironment(),
                com.openclaw.manager.openclawserversmanager.monitoring.entity.HealthState.UNKNOWN,
                null, null, null,
                null, null, null, null,
                null, null,
                false, null, null
        );
    }
}
