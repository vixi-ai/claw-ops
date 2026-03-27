# Task 31: Alerting Engine — Rules, Thresholds, Deduplication

**Status:** TODO
**Module(s):** monitoring
**Priority:** HIGH
**Created:** 2026-03-25
**Completed:** —

## Description

Implement the alerting engine that evaluates health state changes and metric thresholds against user-defined alert rules. Handles alert creation, deduplication, cooldown, acknowledgement, auto-resolution, silence/mute, and maintenance window suppression. Phase 2 feature but HIGH priority due to production importance.

## Acceptance Criteria

- [ ] `AlertRule` entity: defines when to fire an alert (metric thresholds, state changes, deadman)
- [ ] `AlertEvent` entity: tracks individual alert lifecycle (ACTIVE → ACKNOWLEDGED → RESOLVED)
- [ ] Three rule types: THRESHOLD (metric exceeds value), CONSECUTIVE_FAILURE (N checks fail), DEADMAN (no data for N minutes)
- [ ] Alert deduplication: same rule + same server → update existing alert, don't create duplicate
- [ ] Cooldown: after alert fires, don't re-fire for configurable period (default 5 min)
- [ ] Auto-resolution: when condition clears, auto-resolve the alert
- [ ] Acknowledge: user can acknowledge an alert (stops notifications but alert stays active)
- [ ] Silence/Mute: suppress alerts for a server for a configurable period
- [ ] Maintenance window: alerts suppressed during scheduled maintenance
- [ ] Alert severity: LOW, MEDIUM, HIGH, CRITICAL (derived from rule + metric state)
- [ ] `AlertEngine.evaluate(server, healthSnapshot)` — called after every check cycle
- [ ] Default alert rules auto-created for each server (can be customized)

## Implementation Notes

### Alert Rule Model
```java
public class AlertRule {
    UUID id;
    UUID serverId;           // nullable = global rule
    AlertRuleType ruleType;  // THRESHOLD, CONSECUTIVE_FAILURE, DEADMAN
    MetricType metricType;   // which metric to check
    String condition;        // ">", "<", "="
    Double threshold;        // value to compare
    int consecutiveCount;    // for CONSECUTIVE_FAILURE type
    int deadmanMinutes;      // for DEADMAN type (no data timeout)
    int cooldownSeconds;     // default 300 (5 min)
    IncidentSeverity severity;
    boolean enabled;
    boolean autoCreated;     // system-generated default rules
}
```

### Alert Lifecycle
```
Health check → threshold exceeded → check existing alerts
  → No existing alert: create new ACTIVE alert → notify
  → Existing ACTIVE alert: update lastTriggeredAt, skip notification (dedup)
  → Existing ACKNOWLEDGED alert: update, skip notification
  → Cooldown active: skip

Health check → threshold cleared → find matching ACTIVE alert
  → Auto-resolve alert → notify resolution
```

### Noise Reduction Strategy
1. **Deduplication**: one active alert per rule+server combination
2. **Cooldown**: don't re-notify within cooldown period
3. **Flapping protection**: inherited from HealthEvaluator (task-27)
4. **Maintenance suppression**: don't fire during maintenance windows
5. **Severity filtering**: notification channels can filter by severity

## Files Modified
<!-- Filled in after completion -->
