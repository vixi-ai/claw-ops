package com.openclaw.manager.openclawserversmanager.monitoring.controller;

import com.openclaw.manager.openclawserversmanager.monitoring.dto.*;
import com.openclaw.manager.openclawserversmanager.monitoring.service.NotificationChannelService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/monitoring/channels")
@SecurityRequirement(name = "bearerAuth")
public class NotificationChannelController {

    private final NotificationChannelService channelService;

    public NotificationChannelController(NotificationChannelService channelService) {
        this.channelService = channelService;
    }

    @GetMapping
    public ResponseEntity<List<ChannelResponse>> listChannels() {
        return ResponseEntity.ok(channelService.listChannels().stream()
                .map(ChannelResponse::from)
                .toList());
    }

    @PostMapping
    public ResponseEntity<ChannelResponse> createChannel(@Valid @RequestBody CreateChannelRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ChannelResponse.from(channelService.createChannel(req.name(), req.channelType(), req.config())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChannelResponse> getChannel(@PathVariable UUID id) {
        return ResponseEntity.ok(ChannelResponse.from(channelService.getChannel(id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ChannelResponse> updateChannel(@PathVariable UUID id,
                                                          @Valid @RequestBody UpdateChannelRequest req) {
        return ResponseEntity.ok(ChannelResponse.from(
                channelService.updateChannel(id, req.name(), req.enabled(), req.config())));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChannel(@PathVariable UUID id) {
        channelService.deleteChannel(id);
    }

    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, String>> testChannel(@PathVariable UUID id) {
        channelService.testChannel(id);
        return ResponseEntity.ok(Map.of("message", "Test notification sent successfully"));
    }
}
