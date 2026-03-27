# Task 33: Incident Management — Lifecycle, Timeline, Notes

**Status:** TODO
**Module(s):** monitoring
**Priority:** MEDIUM
**Created:** 2026-03-25
**Completed:** —

## Description

Implement a lightweight but production-grade incident management system. Incidents are auto-created when CRITICAL alerts fire and track the full lifecycle from detection to resolution. Includes timeline of events, status transitions, assignment, acknowledgement, notes, and root cause tracking.

## Acceptance Criteria

- [ ] `Incident` entity: tracks incident lifecycle (OPEN → ACKNOWLEDGED → INVESTIGATING → RESOLVED → CLOSED)
- [ ] `IncidentEvent` entity: timeline entries (state changes, notes, alerts linked)
- [ ] Auto-create incident when CRITICAL alert fires (if no open incident for same server)
- [ ] Link multiple alerts to one incident (e.g., CPU + memory alerts on same server = 1 incident)
- [ ] Incident severity: LOW, MEDIUM, HIGH, CRITICAL (derived from worst alert)
- [ ] Status transitions: OPEN → ACKNOWLEDGED → INVESTIGATING → RESOLVED → CLOSED
- [ ] Assignment: assign incident to a user
- [ ] Acknowledgement: user acknowledges they're investigating
- [ ] Notes: add free-text notes to incident timeline (investigation findings, actions taken)
- [ ] Root cause field: optional text field for root cause analysis
- [ ] Auto-resolve: when all linked alerts resolve, auto-resolve incident (with confirmation delay)
- [ ] Duration tracking: time from OPEN to RESOLVED
- [ ] CRUD endpoints for incidents, notes, status transitions
- [ ] Incidents page shows active incidents sorted by severity

## Implementation Notes

### Incident Lifecycle
```
Alert CRITICAL → Check: open incident for this server?
  No → Create incident (OPEN, severity=CRITICAL, linked to alert)
  Yes → Link alert to existing incident, escalate severity if needed

User acknowledges → Status: ACKNOWLEDGED, add timeline event
User starts investigating → Status: INVESTIGATING
User adds note → IncidentEvent with type=NOTE, text=user's note
All alerts resolve → Status: RESOLVED (auto), add timeline event
User confirms closed → Status: CLOSED, add resolution note + root cause
```

### Incident Entity
```java
public class Incident {
    UUID id;
    UUID serverId;
    IncidentSeverity severity;
    IncidentStatus status;          // OPEN, ACKNOWLEDGED, INVESTIGATING, RESOLVED, CLOSED
    String title;                    // auto-generated: "CRITICAL: CPU/Memory on Production-1"
    UUID assignedTo;                // nullable user ID
    UUID acknowledgedBy;
    Instant acknowledgedAt;
    Instant resolvedAt;
    Instant closedAt;
    String rootCause;               // optional
    String resolutionNotes;         // optional
    Instant createdAt;
    Instant updatedAt;
}

public class IncidentEvent {
    UUID id;
    UUID incidentId;
    String eventType;               // STATE_CHANGE, NOTE, ALERT_LINKED, ALERT_RESOLVED, ASSIGNED
    String description;
    UUID userId;                    // who performed the action (null for system events)
    Instant createdAt;
}
```

## Files Modified
<!-- Filled in after completion -->
