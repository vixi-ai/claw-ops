package com.openclaw.manager.openclawserversmanager.monitoring.service;

import com.openclaw.manager.openclawserversmanager.common.exception.ResourceNotFoundException;
import com.openclaw.manager.openclawserversmanager.monitoring.entity.*;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.AlertEventRepository;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.IncidentEventRepository;
import com.openclaw.manager.openclawserversmanager.monitoring.repository.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentService.class);

    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final AlertEventRepository alertEventRepository;

    public IncidentService(IncidentRepository incidentRepository,
                           IncidentEventRepository incidentEventRepository,
                           AlertEventRepository alertEventRepository) {
        this.incidentRepository = incidentRepository;
        this.incidentEventRepository = incidentEventRepository;
        this.alertEventRepository = alertEventRepository;
    }

    // ── Queries ──

    @Transactional(readOnly = true)
    public Page<Incident> listIncidents(UUID serverId, IncidentStatus status, Pageable pageable) {
        if (serverId != null) {
            return incidentRepository.findByServerIdOrderByOpenedAtDesc(serverId, pageable);
        }
        return incidentRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Incident getIncident(UUID id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<IncidentEvent> getTimeline(UUID incidentId) {
        return incidentEventRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId);
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        return incidentRepository.countByStatusIn(
                List.of(IncidentStatus.OPEN, IncidentStatus.ACKNOWLEDGED, IncidentStatus.INVESTIGATING));
    }

    // ── Create ──

    @Transactional
    public Incident createIncident(String title, String description, UUID serverId,
                                    IncidentSeverity severity, UUID authorId) {
        Incident incident = new Incident();
        incident.setTitle(title);
        incident.setDescription(description);
        incident.setServerId(serverId);
        incident.setSeverity(severity);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setOpenedAt(Instant.now());
        incident = incidentRepository.save(incident);

        addTimelineEvent(incident.getId(), authorId, IncidentEventType.STATE_CHANGE,
                "Incident opened with severity " + severity);

        log.info("Incident created: id={}, title={}, server={}", incident.getId(), title, serverId);
        return incident;
    }

    /**
     * Auto-create an incident from an alert event (called by AlertEngine for HIGH/CRITICAL severity).
     */
    @Transactional
    public Incident createFromAlert(AlertEvent alertEvent) {
        Incident incident = new Incident();
        incident.setTitle("Alert: " + (alertEvent.getMessage() != null
                ? alertEvent.getMessage().substring(0, Math.min(alertEvent.getMessage().length(), 200))
                : "Monitoring alert"));
        incident.setDescription("Auto-created from alert event " + alertEvent.getId());
        incident.setServerId(alertEvent.getServerId());
        incident.setSeverity(alertEvent.getSeverity());
        incident.setStatus(IncidentStatus.OPEN);
        incident.setOpenedAt(Instant.now());
        incident = incidentRepository.save(incident);

        // Link the alert to this incident
        alertEvent.setIncidentId(incident.getId());
        alertEventRepository.save(alertEvent);

        addTimelineEvent(incident.getId(), null, IncidentEventType.ALERT_LINKED,
                "Alert linked: " + alertEvent.getId());

        log.info("Incident auto-created from alert: incident={}, alert={}", incident.getId(), alertEvent.getId());
        return incident;
    }

    // ── State transitions ──

    @Transactional
    public Incident acknowledge(UUID id, UUID userId) {
        Incident incident = getIncident(id);
        if (incident.getStatus() != IncidentStatus.OPEN) {
            throw new IllegalStateException("Can only acknowledge OPEN incidents");
        }
        incident.setStatus(IncidentStatus.ACKNOWLEDGED);
        incident.setAcknowledgedAt(Instant.now());
        incident = incidentRepository.save(incident);

        addTimelineEvent(id, userId, IncidentEventType.ACKNOWLEDGED, "Incident acknowledged");
        return incident;
    }

    @Transactional
    public Incident investigate(UUID id, UUID userId) {
        Incident incident = getIncident(id);
        if (incident.getStatus() != IncidentStatus.OPEN && incident.getStatus() != IncidentStatus.ACKNOWLEDGED) {
            throw new IllegalStateException("Can only investigate OPEN or ACKNOWLEDGED incidents");
        }
        incident.setStatus(IncidentStatus.INVESTIGATING);
        incident = incidentRepository.save(incident);

        addTimelineEvent(id, userId, IncidentEventType.STATE_CHANGE, "Investigation started");
        return incident;
    }

    @Transactional
    public Incident resolve(UUID id, UUID userId, String rootCause) {
        Incident incident = getIncident(id);
        if (incident.getStatus() == IncidentStatus.RESOLVED || incident.getStatus() == IncidentStatus.CLOSED) {
            throw new IllegalStateException("Incident is already resolved or closed");
        }
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(Instant.now());
        incident.setResolvedBy(userId);
        if (rootCause != null) incident.setRootCause(rootCause);
        incident = incidentRepository.save(incident);

        // Auto-resolve linked alerts
        List<AlertEvent> linkedAlerts = alertEventRepository.findByIncidentId(id);
        Instant now = Instant.now();
        for (AlertEvent alert : linkedAlerts) {
            if (alert.getStatus() == AlertStatus.ACTIVE || alert.getStatus() == AlertStatus.ACKNOWLEDGED) {
                alert.setStatus(AlertStatus.RESOLVED);
                alert.setResolvedAt(now);
                alertEventRepository.save(alert);
            }
        }

        addTimelineEvent(id, userId, IncidentEventType.STATE_CHANGE,
                "Incident resolved" + (rootCause != null ? ". Root cause: " + rootCause : ""));
        return incident;
    }

    @Transactional
    public Incident close(UUID id, UUID userId) {
        Incident incident = getIncident(id);
        if (incident.getStatus() != IncidentStatus.RESOLVED) {
            throw new IllegalStateException("Can only close RESOLVED incidents");
        }
        incident.setStatus(IncidentStatus.CLOSED);
        incident.setClosedAt(Instant.now());
        incident = incidentRepository.save(incident);

        addTimelineEvent(id, userId, IncidentEventType.STATE_CHANGE, "Incident closed");
        return incident;
    }

    // ── Update ──

    @Transactional
    public Incident update(UUID id, String title, String description, IncidentSeverity severity) {
        Incident incident = getIncident(id);
        if (title != null) incident.setTitle(title);
        if (description != null) incident.setDescription(description);
        if (severity != null) incident.setSeverity(severity);
        return incidentRepository.save(incident);
    }

    // ── Timeline events ──

    @Transactional
    public IncidentEvent addNote(UUID incidentId, UUID authorId, String content) {
        // Verify incident exists
        getIncident(incidentId);
        return addTimelineEvent(incidentId, authorId, IncidentEventType.NOTE, content);
    }

    @Transactional
    public Incident linkAlert(UUID incidentId, UUID alertEventId, UUID userId) {
        Incident incident = getIncident(incidentId);
        AlertEvent alertEvent = alertEventRepository.findById(alertEventId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert event not found: " + alertEventId));

        alertEvent.setIncidentId(incidentId);
        alertEventRepository.save(alertEvent);

        addTimelineEvent(incidentId, userId, IncidentEventType.ALERT_LINKED,
                "Alert " + alertEventId + " linked");
        return incident;
    }

    private IncidentEvent addTimelineEvent(UUID incidentId, UUID authorId,
                                            IncidentEventType type, String content) {
        IncidentEvent event = new IncidentEvent();
        event.setIncidentId(incidentId);
        event.setAuthorId(authorId);
        event.setEventType(type);
        event.setContent(content);
        event.setCreatedAt(Instant.now());
        return incidentEventRepository.save(event);
    }
}
