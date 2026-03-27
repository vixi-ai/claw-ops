# Task 29: Service & Process Monitoring — systemd, Docker, nginx

**Status:** TODO
**Module(s):** monitoring
**Priority:** MEDIUM
**Created:** 2026-03-25
**Completed:** —

## Description

Extend the metrics collector to monitor running services on remote servers. Detect systemd service status, Docker container health, nginx/apache status, database processes (PostgreSQL, MySQL, Redis), and user-defined custom services. Service status feeds into health evaluation and alerting.

## Acceptance Criteria

- [ ] `service_checks` table stores per-server service definitions (name, type, expected state)
- [ ] Auto-discover running systemd services on first check (store in service_checks with `auto_discovered=true`)
- [ ] Auto-discover running Docker containers on first check
- [ ] Track service state: RUNNING, STOPPED, DEGRADED, NOT_FOUND
- [ ] Collect as part of batched SSH command (same SSH session as infrastructure metrics)
- [ ] Service health integrated into overall server health evaluation
- [ ] Users can add custom service checks (service name, check command, expected output)
- [ ] Service restart detection: if service uptime resets, log event
- [ ] Docker container health: parse `docker ps` status column (healthy, unhealthy, starting)
- [ ] Service definitions are templates: "PostgreSQL", "nginx", "Redis" with pre-configured check commands
- [ ] Endpoint for CRUD on service checks per server

### Service Types & Check Commands
```
SYSTEMD:     systemctl is-active {service_name}
DOCKER:      docker inspect --format='{{.State.Status}}' {container_name}
PROCESS:     pgrep -c {process_name}
CUSTOM:      user-defined command, expected exit code 0
PORT:        ss -tlnp 'sport = :{port}' | grep LISTEN
```

## Implementation Notes

### SSH Command Extension (added to batched command)
```bash
echo '---SERVICES---'
systemctl list-units --type=service --state=running,failed --no-pager --plain 2>/dev/null | grep -E '\.service' | awk '{print $1, $3, $4}'
echo '---DOCKER---'
docker ps --format '{{.Names}}|{{.Status}}|{{.Image}}|{{.State}}' 2>/dev/null || echo 'DOCKER_NA'
echo '---PORTS---'
ss -tlnp 2>/dev/null | grep LISTEN
```

### Service Check Entity
```java
public class ServiceCheck {
    UUID id;
    UUID serverId;
    String serviceName;        // "nginx", "postgresql", "my-app"
    ServiceType serviceType;   // SYSTEMD, DOCKER, PROCESS, PORT, CUSTOM
    String checkTarget;        // service name, container name, port number, or custom command
    String currentState;       // RUNNING, STOPPED, DEGRADED, NOT_FOUND
    boolean enabled;
    boolean autoDiscovered;    // true if found by auto-discovery
    boolean critical;          // if true, STOPPED → overall server CRITICAL
    Instant lastCheckedAt;
    Instant lastStateChangeAt;
}
```

## Files Modified
<!-- Filled in after completion -->
