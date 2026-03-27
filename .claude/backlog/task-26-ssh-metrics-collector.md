# Task 26: SSH Metrics Collector — CPU, RAM, Disk, Load, Uptime

**Status:** DONE
**Module(s):** monitoring
**Priority:** HIGH
**Created:** 2026-03-25
**Completed:** 2026-03-25

## Description

Implement the core SSH-based metrics collector that connects to remote servers via SSH and collects infrastructure metrics (CPU, RAM, disk, load, uptime, process count, network I/O) in a single batched command. This is the heart of the monitoring system.

Design the `MetricCollector` interface so that a future agent-based implementation can replace SSH without changing the rest of the system.

## Acceptance Criteria

- [x] `MetricCollector` interface with `CollectionResult collect(Server server)` method
- [x] `SshMetricCollector` implementation that uses `SshService.executeCommand()`
- [x] Single batched SSH command collects ALL metrics in one connection (~1-2s per server)
- [x] Robust parsing of command output with section delimiters (`---CPU---`, `---MEM---`, etc.)
- [x] Graceful handling of partial failures (e.g., disk command fails but CPU works)
- [x] `CollectionResult` contains: Map of MetricType→value, collection timestamp, duration, errors
- [x] Metrics parsed: CPU usage %, memory usage %, disk usage % (per mount), load averages, uptime, process count, swap, network RX/TX bytes
- [x] `MetricsService` persists collected metrics to `metric_samples` table
- [x] `MetricsService` updates `health_snapshots` table with latest values
- [x] Cross-distro compatibility: works on Ubuntu, Debian, CentOS, RHEL, Amazon Linux
- [x] All commands use standard Linux tools: `top`, `free`, `df`, `cat /proc/*`, `ps`
- [x] No sudo required for metric collection
- [x] Timeout protection: 30-second SSH timeout per server
- [x] Handles SSH connection failures gracefully (marks server UNREACHABLE)

## Implementation Notes

### Batched SSH Command
```bash
echo '---CPU---'
top -bn1 | head -5
echo '---MEM---'
free -b
echo '---DISK---'
df -B1 --output=target,size,used,avail,pcent 2>/dev/null || df -k
echo '---LOAD---'
cat /proc/loadavg
echo '---UPTIME---'
cat /proc/uptime
echo '---PROCS---'
ps aux --no-headers 2>/dev/null | wc -l
echo '---SWAP---'
free -b | grep -i swap
echo '---NET---'
cat /proc/net/dev | tail -n +3
echo '---END---'
```

### MetricCollector Interface (Provider-Agnostic)
```java
public interface MetricCollector {
    CollectionResult collect(Server server);
    String getCollectorType(); // "SSH" or "AGENT"
}

public record CollectionResult(
    UUID serverId,
    Map<String, Double> metrics,      // MetricType.name() + optional label → value
    Instant collectedAt,
    long durationMs,
    boolean sshReachable,
    List<String> errors               // partial failure details
) {}
```

### Parsing Strategy
- Split output by `---SECTION---` markers
- Each section parsed independently — one section failing doesn't block others
- CPU: parse `%Cpu(s):` line from top output → extract idle → compute usage
- Memory: parse `free -b` Mem row → used/total → percentage
- Disk: parse each `df` row → per-mount metrics
- Load: parse `/proc/loadavg` → 3 values
- Network: parse `/proc/net/dev` → per-interface RX/TX bytes

### Error Handling
- SSH connection timeout → mark UNREACHABLE, log error, skip this cycle
- SSH auth failure → mark UNREACHABLE, log error, don't retry until next cycle
- Command partial failure → collect what's available, log warnings for failed sections
- Parsing failure → log warning with raw output, store null for that metric

## Files Modified
- `src/main/java/.../monitoring/collector/MetricCollector.java` — Provider-agnostic interface
- `src/main/java/.../monitoring/collector/CollectionResult.java` — Immutable result record
- `src/main/java/.../monitoring/collector/SshMetricCollector.java` — SSH-based implementation with batched command + section parsing
- `src/main/java/.../monitoring/service/MetricsService.java` — Persists metrics, upserts health snapshots, evaluates health state
