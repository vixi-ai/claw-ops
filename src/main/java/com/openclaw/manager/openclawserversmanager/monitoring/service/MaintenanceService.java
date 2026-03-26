package com.openclaw.manager.openclawserversmanager.monitoring.service;

import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.monitoring.dto.CreateMaintenanceWindowRequest;
import com.openclaw.manager.openclawserversmanager.monitoring.dto.MaintenanceWindowResponse;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.MaintenanceWindow;
import com.openclaw.manager.openclawserversmanager.monitoring.mapper.MaintenanceWindowMapper;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.MaintenanceWindowRepository;
import com.openclaw.manager.openclawserversmanager.servers.entity.Server;
import com.openclaw.manager.openclawserversmanager.servers.repository.ServerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MaintenanceService {

    private final MaintenanceWindowRepository maintenanceWindowRepository;
    private final ServerRepository serverRepository;

    public MaintenanceService(MaintenanceWindowRepository maintenanceWindowRepository,
                              ServerRepository serverRepository) {
        this.maintenanceWindowRepository = maintenanceWindowRepository;
        this.serverRepository = serverRepository;
    }

    @Transactional
    public MaintenanceWindowResponse createWindow(CreateMaintenanceWindowRequest request, UUID createdBy) {
        if (!request.endAt().isAfter(request.startAt())) {
            throw new IllegalArgumentException("End time must be after start time");
        }

        Server server = serverRepository.findById(request.serverId())
                .orElseThrow(() -> new ResourceNotFoundException("Server with id " + request.serverId() + " not found"));

        MaintenanceWindow window = new MaintenanceWindow();
        window.setServerId(request.serverId());
        window.setReason(request.reason());
        window.setStartAt(request.startAt());
        window.setEndAt(request.endAt());
        window.setCreatedBy(createdBy);

        window = maintenanceWindowRepository.save(window);
        return MaintenanceWindowMapper.toResponse(window, server.getName());
    }

    @Transactional(readOnly = true)
    public List<MaintenanceWindowResponse> getActiveWindows() {
        Instant now = Instant.now();
        List<MaintenanceWindow> windows = maintenanceWindowRepository.findByStartAtBeforeAndEndAtAfter(now, now);
        return mapWindows(windows);
    }

    @Transactional(readOnly = true)
    public List<MaintenanceWindowResponse> getAllWindows() {
        List<MaintenanceWindow> windows = maintenanceWindowRepository.findAll();
        return mapWindows(windows);
    }

    @Transactional
    public void deleteWindow(UUID windowId) {
        if (!maintenanceWindowRepository.existsById(windowId)) {
            throw new ResourceNotFoundException("Maintenance window with id " + windowId + " not found");
        }
        maintenanceWindowRepository.deleteById(windowId);
    }

    private List<MaintenanceWindowResponse> mapWindows(List<MaintenanceWindow> windows) {
        List<UUID> serverIds = windows.stream().map(MaintenanceWindow::getServerId).distinct().toList();
        Map<UUID, Server> serverMap = serverRepository.findAllById(serverIds).stream()
                .collect(Collectors.toMap(Server::getId, Function.identity()));

        return windows.stream()
                .map(w -> {
                    Server server = serverMap.get(w.getServerId());
                    String name = server != null ? server.getName() : "Unknown";
                    return MaintenanceWindowMapper.toResponse(w, name);
                })
                .toList();
    }
}
