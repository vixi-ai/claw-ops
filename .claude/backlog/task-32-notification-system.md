# Task 32: Notification System — Email, Slack, Webhook, Discord

**Status:** TODO
**Module(s):** monitoring
**Priority:** MEDIUM
**Created:** 2026-03-25
**Completed:** —

## Description

Implement the notification dispatch system. When the alerting engine fires or resolves an alert, notifications are sent to configured channels. Supports multiple channel types, severity-based routing, quiet hours, and retry on failure.

## Acceptance Criteria

- [ ] `NotificationChannel` entity: stores channel config (type, webhook URL, email, API token)
- [ ] Channel types: EMAIL, SLACK, DISCORD, TELEGRAM, WEBHOOK (generic)
- [ ] `NotificationDispatcher` service: dispatches notifications to channels based on routing rules
- [ ] `NotificationRoute` entity: maps alert severity + server environment → notification channel(s)
- [ ] Each channel type has a dedicated sender: `SlackNotificationSender`, `WebhookNotificationSender`, etc.
- [ ] Common notification payload: alert summary, server name, hostname, metric, value, threshold, severity, timestamp
- [ ] Slack: sends formatted message with color-coded severity (green/yellow/red)
- [ ] Webhook: POST JSON payload to configured URL with HMAC signature
- [ ] Email: sends via SMTP (Spring Mail) with HTML template
- [ ] Discord: sends embed via Discord webhook URL
- [ ] Telegram: sends message via Telegram Bot API
- [ ] Retry on send failure: 3 retries with exponential backoff (2s, 4s, 8s)
- [ ] Quiet hours: suppress non-CRITICAL notifications during configured hours
- [ ] CRUD endpoints for notification channels and routes
- [ ] Test endpoint: send a test notification to verify channel configuration

## Implementation Notes

### Slack Message Format
```json
{
  "text": "🔴 CRITICAL: CPU usage at 97% on server 'Production-1'",
  "attachments": [{
    "color": "#ff0000",
    "fields": [
      {"title": "Server", "value": "Production-1 (65.109.232.172)", "short": true},
      {"title": "Metric", "value": "CPU Usage: 97%", "short": true},
      {"title": "Threshold", "value": "> 95% (critical)", "short": true},
      {"title": "Time", "value": "2026-03-25 15:30:00 UTC", "short": true}
    ]
  }]
}
```

### Webhook Payload
```json
{
  "event": "alert.triggered",
  "severity": "CRITICAL",
  "server": {"id": "...", "name": "Production-1", "hostname": "65.109.232.172"},
  "alert": {"id": "...", "metric": "CPU_USAGE_PERCENT", "value": 97.0, "threshold": 95.0},
  "timestamp": "2026-03-25T15:30:00Z",
  "signature": "HMAC-SHA256 of payload"
}
```

## Files Modified
<!-- Filled in after completion -->
