# Task 28: Monitoring Scheduler — Periodic Check Execution

**Status:** DONE
**Module(s):** monitoring
**Priority:** HIGH
**Created:** 2026-03-25
**Completed:** 2026-03-25

## Description

Implement the monitoring scheduler that periodically collects metrics from all enabled servers. Uses async worker pool to run checks concurrently without blocking the main application. Handles server prioritization, concurrency limits, stale check detection, and graceful error recovery.

## Acceptance Criteria

- [x] `MonitoringSchedulerConfig` — adds `monitoringExecutor` ThreadPoolTaskExecutor (core=4, max=20, queue=100, prefix="monitor-")
- [x] `MonitoringScheduler` service with `@Scheduled(fixedDelayString = "${monitoring.scheduler.interval:30000}")` trigger
- [x] Each scheduler tick: queries all servers with `monitoring_profiles.enabled=true` and `nextCheckDue <= now()`
- [x] Submits check tasks to `monitoringExecutor` — one task per server
- [x] Each task: collect metrics → evaluate health → update snapshot → persist metrics → trigger alert evaluation
- [x] Concurrency control: max 1 active check per server (skip if previous still running)
- [x] Server prioritization: CRITICAL/WARNING servers checked first, then HEALTHY, then UNKNOWN
- [x] Per-server configurable check interval (default 60s, stored in `monitoring_profiles`)
- [x] Stale check detection: if `last_check_at` > 3× interval → mark UNKNOWN
- [x] Startup behavior: on app start, schedule immediate check for all enabled servers
- [x] Cleanup job: `@Scheduled(cron = "0 0 3 * * *")` — delete metric_samples older than retention period
- [x] Monitoring of the monitor: log scheduler tick duration, checks completed, checks failed
- [x] Graceful shutdown: `@PreDestroy` waits for in-flight checks to complete (max 30s) — configured via ThreadPoolTaskExecutor.setWaitForTasksToCompleteOnShutdown + awaitTerminationSeconds

## Implementation Notes

### Scheduler Flow
```
@Scheduled(fixedDelay=30s)
schedulerTick() {
    List<Server> servers = getServersDueForCheck()
    sort by priority (CRITICAL first, HEALTHY last)
    for each server:
        if (!isCheckInFlight(server.id)):
            monitoringExecutor.submit(() -> runCheck(server))
}

runCheck(server) {
    markCheckInFlight(server.id)
    try {
        CollectionResult metrics = metricCollector.collect(server)
        HealthSnapshot health = healthEvaluator.evaluate(metrics, profile)
        metricsService.persistMetrics(metrics)
        healthService.updateSnapshot(health)
        alertEngine.evaluateAlerts(server, health)  // Phase 2, no-op stub for MVP
    } catch (Exception e) {
        healthService.markUnreachable(server)
    } finally {
        clearCheckInFlight(server.id)
    }
}
```

### Concurrency Tracking
Use `ConcurrentHashMap<UUID, Boolean>` for in-flight check tracking. Avoid database-based locking for performance.

### Auto-Create Monitoring Profile
When scheduler encounters a server with no `monitoring_profile`, auto-create one with defaults. This means monitoring is opt-out, not opt-in.

### Environment Variables
```properties
monitoring.scheduler.interval=30000           # scheduler tick interval (ms)
monitoring.check.default-interval=60          # default per-server check interval (seconds)
monitoring.check.timeout=30                   # SSH command timeout (seconds)
monitoring.metrics.retention-days=7           # raw metric retention
monitoring.executor.core-pool-size=4
monitoring.executor.max-pool-size=20
```

## Files Modified
- `src/main/java/.../monitoring/config/MonitoringSchedulerConfig.java` — ThreadPoolTaskExecutor bean with graceful shutdown
- `src/main/java/.../monitoring/scheduler/MonitoringScheduler.java` — Scheduled tick, concurrency control, priority sorting, stale detection, retention cleanup
- `src/main/resources/application.properties` — Added monitoring.* configuration properties
