# Task 35: Monitoring Dev Admin Page (HTML)

**Status:** DONE
**Module(s):** monitoring
**Priority:** HIGH
**Created:** 2026-03-25
**Completed:** 2026-03-25

## Description

Create the monitoring dev admin page at `/dev/monitoring.html` following the existing dev admin page pattern (dark theme, vanilla HTML/CSS/JS, JWT auth via `common.js`). This is the primary UI for viewing server health, metrics, alerts, and incidents.

## Acceptance Criteria

### Fleet Overview Section
- [x] Table showing all servers with: name, host, environment, overall health state, CPU %, memory %, disk %, load, uptime, last checked
- [x] Color-coded health badges: green=HEALTHY, yellow=WARNING, red=CRITICAL, gray=UNREACHABLE/UNKNOWN, blue=MAINTENANCE
- [x] Sort by health state (worst first by default)
- [x] Filter by environment, health state
- [x] Auto-refresh every 30 seconds
- [x] Click server row → expands to show detailed metrics

### Server Detail (expanded row or modal)
- [x] All current metric values with health badges
- [x] Per-disk usage breakdown (/, /var, /home, etc.) — via latest metrics endpoint
- [ ] Running services list with status (Phase 2)
- [ ] Endpoint check results (Phase 2)
- [x] "Check Now" button to trigger immediate check
- [ ] Link to SSH terminal for this server (Phase 2)

### Metrics History Charts
- [x] Simple line charts for CPU, memory, disk over time (last 1h / 6h / 24h / 7d)
- [x] Use `<canvas>` with lightweight charting
- [x] Time range selector

### Active Alerts Section (Phase 2 placeholder)
- [ ] Table of active alerts: severity, server, metric, value, triggered at
- [ ] Acknowledge button
- [ ] Silence button (with duration picker)

### Monitoring Configuration
- [x] Per-server monitoring profile editor (thresholds, interval, enable/disable)
- [ ] Service check manager (add/remove/enable/disable services) (Phase 2)
- [ ] Endpoint check manager (add/remove HTTP/TCP/SSL checks) (Phase 2)

### Navigation
- [x] Add "Monitoring" link to the dev admin sidebar/header navigation (update `common.js` or `index.html`)
- [x] Update dashboard (`index.html`) with monitoring summary card

## Implementation Notes

### Page Structure
```html
<!-- Fleet Overview -->
<div class="section">
    <div class="section-header">
        <h2>Server Health</h2>
        <div>
            <select id="filterEnv">...</select>
            <select id="filterState">...</select>
            <button onclick="refreshAll()">Refresh</button>
        </div>
    </div>
    <table>
        <thead><tr><th>Server</th><th>Host</th><th>Health</th><th>CPU</th><th>Memory</th><th>Disk</th><th>Load</th><th>Uptime</th><th>Last Check</th><th>Actions</th></tr></thead>
        <tbody id="healthTable">...</tbody>
    </table>
</div>
```

### Health Badge Colors (match existing badge CSS)
```javascript
const healthColors = {
    HEALTHY: 'badge-active',     // green
    WARNING: 'badge-warning',    // yellow
    CRITICAL: 'badge-error',     // red
    UNREACHABLE: 'badge-soon',   // gray
    UNKNOWN: 'badge-soon',       // gray
    MAINTENANCE: 'badge-info'    // blue (add new CSS class)
};
```

### Auto-Refresh
```javascript
setInterval(() => loadHealthOverview(), 30000);
```

### API Integration
- `GET /api/v1/monitoring/health` — fleet overview data
- `GET /api/v1/monitoring/health/{serverId}` — server detail
- `GET /api/v1/monitoring/metrics/{serverId}?type=CPU_USAGE_PERCENT&from=...&to=...` — chart data
- `POST /api/v1/monitoring/check/{serverId}` — trigger check
- All calls via `apiFetch()` from `common.js`

### Uptime Formatting
```javascript
function formatUptime(seconds) {
    if (!seconds) return '—';
    const d = Math.floor(seconds / 86400);
    const h = Math.floor((seconds % 86400) / 3600);
    if (d > 0) return d + 'd ' + h + 'h';
    const m = Math.floor((seconds % 3600) / 60);
    return h + 'h ' + m + 'm';
}
```

### Metric Formatting
```javascript
function formatPercent(v) { return v != null ? v.toFixed(1) + '%' : '—'; }
function formatBytes(b) {
    if (b == null) return '—';
    const units = ['B', 'KB', 'MB', 'GB', 'TB'];
    let i = 0;
    while (b >= 1024 && i < units.length - 1) { b /= 1024; i++; }
    return b.toFixed(1) + ' ' + units[i];
}
```

## Files Modified
- `src/main/resources/static/dev/monitoring.html` — Full monitoring dashboard page
- `src/main/resources/static/dev/index.html` — Added Monitoring card to dashboard grid
