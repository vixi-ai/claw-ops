package com.openclaw.manager.openclawserversmanager.containerlogs.controller;

import com.openclaw.manager.openclawserversmanager.containerlogs.entity.ContainerService;
import com.openclaw.manager.openclawserversmanager.containerlogs.service.ContainerLogsTicketService;
import com.openclaw.manager.openclawserversmanager.containerlogs.service.ContainerLogsTicketService.Ticket;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/container-logs/ws-ticket")
@Tag(name = "container-logs", description = "Live tail WebSocket ticket issuance (ADMIN only)")
@SecurityRequirement(name = "bearerAuth")
public class ContainerLogsTokenController {

    private final ContainerLogsTicketService ticketService;

    public ContainerLogsTokenController(ContainerLogsTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    @Operation(summary = "Issue a single-use WebSocket ticket for live tail")
    public ResponseEntity<TicketResponse> issue(
            @RequestParam ContainerService service,
            Authentication authentication
    ) {
        UUID userId = (UUID) authentication.getPrincipal();
        Ticket ticket = ticketService.issue(userId, service);
        return ResponseEntity.ok(new TicketResponse(ticket.value(), ticket.expiresAt()));
    }

    public record TicketResponse(String ticket, Instant expiresAt) {
    }
}
