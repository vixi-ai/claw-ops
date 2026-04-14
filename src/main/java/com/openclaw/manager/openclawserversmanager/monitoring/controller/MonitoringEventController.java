package com.openclaw.manager.openclawserversmanager.monitoring.controller;

import com.openclaw.manager.openclawserversmanager.monitoring.engine.AlertEngine;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-Sent Events (SSE) endpoint for real-time monitoring events.
 * Clients connect to /api/v1/monitoring/events/stream and receive live updates
 * when alerts fire, resolve, or health state changes occur.
 */
@RestController
@RequestMapping("/api/v1/monitoring/events")
@SecurityRequirement(name = "bearerAuth")
public class MonitoringEventController {

    private static final Logger log = LoggerFactory.getLogger(MonitoringEventController.class);
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30 minutes

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("message", "Connected to monitoring event stream")));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    @Async
    @EventListener
    public void onAlertFired(AlertEngine.AlertFiredEvent event) {
        broadcast("alert_fired", Map.of(
                "alertId", event.alertEvent().getId().toString(),
                "ruleName", event.alertRule().getName(),
                "serverId", event.alertEvent().getServerId().toString(),
                "severity", event.alertEvent().getSeverity().name(),
                "message", event.alertEvent().getMessage() != null ? event.alertEvent().getMessage() : ""
        ));
    }

    @Async
    @EventListener
    public void onAlertResolved(AlertEngine.AlertResolvedEvent event) {
        broadcast("alert_resolved", Map.of(
                "alertId", event.alertEvent().getId().toString(),
                "ruleName", event.alertRule().getName(),
                "serverId", event.alertEvent().getServerId().toString()
        ));
    }

    private void broadcast(String eventName, Object data) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}
