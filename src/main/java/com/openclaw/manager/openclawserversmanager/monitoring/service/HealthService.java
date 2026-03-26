package com.openclaw.manager.openclawserversmanager.monitoring.service;

import com.openclaw.manager.openclawserversmanager.monitoring.dto.FleetHealthResponse;
import com.openclaw.manager.openclawserversmanager.monitoring.dto.ServerHealthSummary;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.HealthSnapshot;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.HealthState;
import com.openclaw.manager.openclawserversmanager.monitoring.mapper.HealthSnapshotMapper;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.HealthSnapshotRepository;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.repository.ServerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HealthService {

    private final HealthSnapshotRepository healthSnapshotRepository;
    private final ServerRepository serverRepository;

    public HealthService(HealthSnapshotRepository healthSnapshotRepository,
                         ServerRepository serverRepository) {
        this.healthSnapshotRepository = healthSnapshotRepository;
        this.serverRepository = serverRepository;
    }

    @Transactional(readOnly = true)
    public FleetHealthResponse getFleetHealth(String environment, HealthState stateFilter) {
        List<Server> servers = serverRepository.findAll();
        List<HealthSnapshot> snapshots = healthSnapshotRepository.findAll();

        Map<UUID, HealthSnapshot> snapshotMap = snapshots.stream()
                .collect(Collectors.toMap(HealthSnapshot::getServerId, Function.identity()));

        List<ServerHealthSummary> summaries = new ArrayList<>();
        int healthy = 0, warning = 0, critical = 0, unreachable = 0, unknown = 0, maintenance = 0;

        for (Server server : servers) {
            if (environment != null && !environment.isBlank() && !environment.equals(server.getEnvironment())) {
                continue;
            }

            HealthSnapshot snapshot = snapshotMap.get(server.getId());
            ServerHealthSummary summary;

            if (snapshot != null) {
                summary = HealthSnapshotMapper.toSummary(snapshot, server);
            } else {
                summary = HealthSnapshotMapper.toSummaryNoSnapshot(server);
            }

            if (stateFilter != null && summary.overallState() != stateFilter) {
                continue;
            }

            summaries.add(summary);

            switch (summary.overallState()) {
                case HEALTHY -> healthy++;
                case WARNING -> warning++;
                case CRITICAL -> critical++;
                case UNREACHABLE -> unreachable++;
                case UNKNOWN -> unknown++;
                case MAINTENANCE -> maintenance++;
            }
        }

        // Sort: worst state first
        summaries.sort(Comparator.comparingInt(s -> statePriority(s.overallState())));

        return new FleetHealthResponse(
                summaries.size(), healthy, warning, critical, unreachable, unknown, maintenance, summaries
        );
    }

    @Transactional(readOnly = true)
    public ServerHealthSummary getServerHealth(UUID serverId) {
        Server server = serverRepository.findById(serverId).orElse(null);
        if (server == null) return null;

        Optional<HealthSnapshot> snapshotOpt = healthSnapshotRepository.findByServerId(serverId);
        if (snapshotOpt.isPresent()) {
            return HealthSnapshotMapper.toSummary(snapshotOpt.get(), server);
        }
        return HealthSnapshotMapper.toSummaryNoSnapshot(server);
    }

    private int statePriority(HealthState state) {
        return switch (state) {
            case CRITICAL -> 0;
            case UNREACHABLE -> 1;
            case WARNING -> 2;
            case UNKNOWN -> 3;
            case MAINTENANCE -> 4;
            case HEALTHY -> 5;
        };
    }
}
