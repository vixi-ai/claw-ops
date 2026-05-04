package com.openclaw.manager.openclawserversmanager.containerlogs.controller;

import com.openclaw.manager.openclawserversmanager.containerlogs.dto.ContainerLogFilter;
import com.openclaw.manager.openclawserversmanager.containerlogs.dto.ContainerLogResponse;
import com.openclaw.manager.openclawserversmanager.containerlogs.dto.DeleteContainerLogsResponse;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerLogLevel;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerLogStream;
import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;
import com.openclaw.manager.openclawserversmanager.containerlogs.scheduler.ContainerLogRetentionScheduler;
import com.openclaw.manager.openclawserversmanager.containerlogs.service.ContainerLogQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/container-logs")
@Tag(name = "container-logs", description = "Docker container log history (ADMIN only)")
@SecurityRequirement(name = "bearerAuth")
public class ContainerLogsController {

    private static final int MAX_PAGE_SIZE = 200;

    private final ContainerLogQueryService queryService;
    private final ContainerLogRetentionScheduler retentionScheduler;

    public ContainerLogsController(ContainerLogQueryService queryService,
                                   ContainerLogRetentionScheduler retentionScheduler) {
        this.queryService = queryService;
        this.retentionScheduler = retentionScheduler;
    }

    @GetMapping("/history")
    @Operation(summary = "List container logs (filtered, paginated)")
    public ResponseEntity<Page<ContainerLogResponse>> history(
            @RequestParam ContainerService service,
            @RequestParam(required = false) ContainerLogLevel level,
            @RequestParam(required = false) ContainerLogStream stream,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String search,
            Pageable pageable
    ) {
        Pageable safe = capPageSize(pageable);
        ContainerLogFilter filter = new ContainerLogFilter(service, level, stream, from, to, search);
        return ResponseEntity.ok(queryService.query(filter, safe));
    }

    @DeleteMapping("/history")
    @Operation(summary = "Delete container logs older than the given cutoff")
    public ResponseEntity<DeleteContainerLogsResponse> deleteOld(
            @RequestParam("before") Instant before,
            @RequestParam(required = false) ContainerService service,
            Authentication authentication
    ) {
        if (before == null) {
            throw new IllegalArgumentException("'before' query parameter is required");
        }
        if (!before.isBefore(Instant.now())) {
            throw new IllegalArgumentException("'before' must be a past timestamp");
        }
        UUID userId = (UUID) authentication.getPrincipal();
        long deleted = queryService.deleteOldLogs(before, service, userId);
        return ResponseEntity.ok(new DeleteContainerLogsResponse(deleted, before, service));
    }

    @PostMapping("/admin/purge-now")
    @Operation(summary = "Trigger retention purge immediately (verification helper)")
    public ResponseEntity<Map<ContainerService, Long>> purgeNow() {
        return ResponseEntity.ok(retentionScheduler.purge());
    }

    private Pageable capPageSize(Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);
        Sort sort = pageable.getSort().isUnsorted()
                ? Sort.by(Sort.Direction.DESC, "logTs")
                : pageable.getSort();
        return PageRequest.of(pageable.getPageNumber(), size, sort);
    }
}
