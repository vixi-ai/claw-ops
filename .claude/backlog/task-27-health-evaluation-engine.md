# Task 27: Health Evaluation Engine — Thresholds & State Machine

**Status:** DONE
**Module(s):** monitoring
**Priority:** HIGH
**Created:** 2026-03-25
**Completed:** 2026-03-25

## Description

Implement the health evaluation engine that takes raw metrics and computes per-metric health states, per-server overall health state, and manages health state transitions. Includes threshold comparison, state machine logic, and flapping protection.

## Acceptance Criteria

- [x] `HealthEvaluator` service that takes `CollectionResult` and `MonitoringProfile` → produces `HealthSnapshot`
- [x] Per-metric health evaluation: CPU, memory, disk, load each independently evaluated
- [x] Configurable thresholds per server via `MonitoringProfile` (warning/critical levels)
- [x] Default thresholds: CPU warn=80%/crit=95%, memory warn=80%/crit=95%, disk warn=85%/crit=95%
- [x] Overall server health = worst state among all metrics (pessimistic aggregation)
- [x] UNREACHABLE state when SSH fails (overrides all metric states)
- [x] MAINTENANCE state manual override (ignores metric states during maintenance window)
- [x] Flapping protection: require N consecutive checks in new state before transitioning (default: 2)
- [x] Health state changes logged as events (for timeline/history)
- [x] `HealthSnapshot` entity updated on every check cycle
- [x] State transitions trigger alert evaluation (feeds into AlertEngine in task-31) — HealthEvaluator returns HealthSnapshot which can be consumed by AlertEngine

## Implementation Notes

### Threshold Evaluation Examples
```
CPU: 45% → HEALTHY (below 80% warning)
CPU: 82% → WARNING (above 80%, below 95%)
CPU: 97% → CRITICAL (above 95%)

Memory: 91% → CRITICAL (above 95%? No, above 80% warning. Check thresholds)
Memory: profile.memoryWarningThreshold = 80, profile.memoryCriticalThreshold = 95
Memory: 85% → WARNING

Disk /: 96% → CRITICAL
Disk /var: 50% → HEALTHY
Overall disk state: CRITICAL (worst of all mounts)

Overall server: max(CPU=WARNING, MEM=HEALTHY, DISK=CRITICAL) → CRITICAL
```

### Flapping Protection
```java
// Don't transition to WARNING from HEALTHY on a single spike
if (newState != currentState) {
    int consecutiveInNewState = snapshot.getConsecutiveInState();
    if (consecutiveInNewState < FLAP_THRESHOLD) {
        // Stay in current state, increment counter
        snapshot.setConsecutiveInState(consecutiveInNewState + 1);
        return currentState; // No transition yet
    }
    // Threshold met → transition
    snapshot.setConsecutiveInState(0);
    logStateChange(server, currentState, newState);
    return newState;
}
```

### State Transition Rules
- UNKNOWN → any state: always allowed (initial state)
- HEALTHY → WARNING: requires 2 consecutive WARNING evaluations
- HEALTHY → CRITICAL: immediate (critical is always immediate)
- WARNING → CRITICAL: immediate
- WARNING → HEALTHY: requires 2 consecutive HEALTHY evaluations (avoid flapping)
- CRITICAL → WARNING: requires 2 consecutive WARNING evaluations
- CRITICAL → HEALTHY: requires 3 consecutive HEALTHY evaluations
- Any → UNREACHABLE: immediate (SSH failure)
- UNREACHABLE → previous state: requires 1 successful check
- MAINTENANCE: manual override, ignores evaluations

## Files Modified
- `src/main/resources/db/migration/V18__add_health_snapshot_flapping_columns.sql` — Adds proposed_state, consecutive_in_proposed, previous_state, state_changed_at
- `src/main/java/.../monitoring/entity/HealthSnapshot.java` — Added 4 flapping protection fields + getters/setters
- `src/main/java/.../monitoring/engine/HealthEvaluator.java` — New: health evaluation engine with state machine + flapping protection
- `src/main/java/.../monitoring/service/MetricsService.java` — Refactored: removed health evaluation logic, delegates to HealthEvaluator
