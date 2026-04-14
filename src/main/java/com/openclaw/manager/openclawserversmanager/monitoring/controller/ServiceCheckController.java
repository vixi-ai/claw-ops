package com.openclaw.manager.openclawserversmanager.monitoring.controller;

import com.openclaw.manager.openclawserversmanager.monitoring.entity.ServiceCheck;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.ServiceCheckRepository;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/monitoring/services")
@SecurityRequirement(name = "bearerAuth")
public class ServiceCheckController {

    private final ServiceCheckRepository serviceCheckRepository;

    public ServiceCheckController(ServiceCheckRepository serviceCheckRepository) {
        this.serviceCheckRepository = serviceCheckRepository;
    }

    @GetMapping("/{serverId}")
    public ResponseEntity<List<Map<String, Object>>> listServices(@PathVariable UUID serverId) {
        List<ServiceCheck> checks = serviceCheckRepository.findByServerIdOrderByCheckedAtDesc(serverId);
        // Group by service name, return latest per service
        var latest = checks.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ServiceCheck::getServiceName,
                        sc -> sc,
                        (a, b) -> a.getCheckedAt().isAfter(b.getCheckedAt()) ? a : b
                ));
        return ResponseEntity.ok(latest.values().stream()
                .map(sc -> Map.<String, Object>of(
                        "serviceName", sc.getServiceName(),
                        "serviceType", sc.getServiceType().name(),
                        "isRunning", sc.isRunning(),
                        "pid", sc.getPid() != null ? sc.getPid() : 0,
                        "checkedAt", sc.getCheckedAt().toString()
                ))
                .toList());
    }
}
