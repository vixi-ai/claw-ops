# CLAUDE.md

This file provides guidance to Claude Code when working with the ClawOps backend.

## Project

ClawOps is a server management platform backend for deploying and managing self-hosted AI agents and general server infrastructure. It provides REST APIs and WebSocket terminals for SSH command execution, SFTP file management, deployment automation, DNS/SSL provisioning, health monitoring, and push notifications.

Built with Spring Boot 4.0.3, Java 21, PostgreSQL 17, Flyway migrations.

## Commands

```bash
mvn clean install           # Full build with tests
mvn clean install -DskipTests  # Build without tests
mvn spring-boot:run         # Run dev server (port 8080)
mvn compile                 # Compile only
mvn flyway:migrate          # Run pending migrations
```

Swagger UI available at `/swagger-ui.html` (controlled by `SWAGGER_ENABLED` env var).

## Environment Setup

Key environment variables:
- `DATABASE_URL` — PostgreSQL connection string (default: `jdbc:postgresql://localhost:5432/openclaw`)
- `DATABASE_USERNAME` / `DATABASE_PASSWORD` — DB credentials
- `JWT_SECRET` — Secret key for JWT token signing
- `ENCRYPTION_KEY` — AES-256 key for secrets encryption (hex-encoded)
- `CORS_ALLOWED_ORIGINS` — Comma-separated frontend origins
- `SWAGGER_ENABLED` — Enable/disable Swagger UI (default: true)

## Architecture

### Module Structure

14 packages under `com.openclaw.manager.openclawserversmanager`:

| Module | Purpose | Key Entities |
|--------|---------|-------------|
| `auth` | JWT authentication, login, refresh | RefreshToken |
| `users` | User management, roles (ADMIN/DEVOPS) | User, UserServerAccess |
| `servers` | Server inventory, connection settings | Server |
| `secrets` | AES-256-GCM encrypted credentials | Secret |
| `ssh` | SSH command execution, SFTP operations | CommandResult |
| `terminal` | WebSocket browser terminal, persistent sessions | TerminalSession, PersistentSession |
| `deployment` | Script library, async job execution | DeploymentScript, DeploymentJob |
| `templates` | Agent template provisioning | AgentTemplate |
| `domains` | DNS automation (Cloudflare, Namecheap) | ProviderAccount, ManagedZone, DomainAssignment |
| `audit` | Operation audit logging | AuditLog |
| `monitoring` | Health checks, metrics, alerts, incidents | HealthSnapshot, MetricSample, AlertRule, Incident |
| `notifications` | Push notifications (FCM + Web Push) | NotificationProvider, DeviceToken, UserDevice, PushSubscription |
| `common` | Shared exceptions, utilities | ApiError, ResourceNotFoundException |

### Pattern

Controllers → Services → Repositories (within module). Cross-module: Services call other module's Services directly. No message bus.

### Database

PostgreSQL 17 with Flyway migrations (V1-V23). Schema managed entirely through migration files in `src/main/resources/db/migration/`.

Key tables: `users`, `servers`, `secrets`, `deployment_scripts`, `deployment_jobs`, `notification_providers`, `device_tokens`, `user_devices`, `push_subscriptions`, `health_snapshots`, `metric_samples`, `alert_rules`, `incidents`, `audit_logs`.

### Authentication

JWT Bearer tokens:
- Access token: short-lived, signed with `JWT_SECRET`
- Refresh token: stored in `refresh_tokens` table, used to obtain new access tokens
- Roles: `ADMIN` (full access) and `DEVOPS` (server management)
- Rate limiting: 10 login attempts per 60s window
- Account lockout: 5 failures → 15min lockout

### SSH & Terminal

Uses sshj library for all remote server operations:
- `SshService` — Execute commands, SFTP operations (ls, upload, download)
- `TerminalWebSocketHandler` — Interactive SSH via WebSocket at `/ws/terminal?token=TOKEN`
- `PersistentSessionService` — Long-lived SSH sessions that survive browser disconnects
- Connection pool: 10s timeout, 60s command timeout, strict host key checking

### Notification System

Multi-provider architecture supporting FCM and Web Push:
- `FirebaseService` — FCM sending with token management, batch sending (500/batch), stale token cleanup
- `WebPushService` — VAPID Web Push with BouncyCastle crypto
- `NotificationDispatchService` — Routes to appropriate provider by type
- `UserDeviceService` — Device registration, notification toggle per device
- Provider credentials stored encrypted via secrets module
- FCM tokens cached in `device_tokens` table, linked to `user_devices`

### Deployment

Async job execution via `@Async` executor (core=4, max=20):
- Scripts stored in `deployment_scripts` table (types: GENERAL, INSTALL, REMOVE, UPDATE, MAINTENANCE)
- Jobs tracked in `deployment_jobs` table with status: PENDING → RUNNING → COMPLETED/FAILED/CANCELLED
- Interactive deployment via WebSocket terminal
- Template-based deployment for OpenClaw agents

### Monitoring

Scheduled health checks every 30s:
- Health snapshots stored per server
- Metric samples: CPU, memory, disk, swap, load
- Alert rules with thresholds and notification channel routing
- Incident tracking with severity levels
- Maintenance windows to suppress alerts
- 7-day metric retention

## API Endpoints Reference

### Auth — `/api/v1/auth`
- `POST /login` — Login → TokenResponse
- `POST /refresh` — Refresh access token
- `POST /logout` — Invalidate refresh token
- `GET /me` — Current user profile

### Servers — `/api/v1/servers`
- `GET /` — List (paginated)
- `POST /` — Create
- `GET /{id}` — Get by ID
- `PATCH /{id}` — Update
- `DELETE /{id}` — Delete
- `POST /{id}/test-connection` — Test SSH
- `GET /{id}/ssh/session-token` — WebSocket ticket
- `POST /{id}/ssh/command` — Execute command `{ command, timeoutSeconds? }`
- `GET /{id}/sftp/ls?path=` — List directory
- `GET /{id}/sftp/download?path=` — Download file
- `POST /{id}/sftp/upload?path=` — Upload file (multipart)

### Notifications — `/api/v1/notifications`
- `GET /vapid-key` — VAPID public key
- `GET /fcm-config` — Firebase web config
- `POST /push/subscribe` — Register Web Push subscription
- `POST /fcm/subscribe` — Register FCM token
- `POST /devices` — Register device
- `GET /devices` — List user's devices
- `PATCH /devices/{id}/toggle` — Enable/disable notifications
- `POST /send` — Send via default provider
- `POST /send/all` — Broadcast to all providers
- `POST /send/user/{id}` — Send to specific user

### Notification Providers — `/api/v1/notification-providers`
- CRUD + `POST /{id}/validate` + `POST /{id}/set-default`

### Terminal — `/api/v1/servers/{id}/persistent-sessions`
- `POST /` — Create persistent session
- `GET /` — List sessions
- `POST /{sessionId}/token` — Get WebSocket token
- `POST /{sessionId}/kill` — Kill session

## Key Conventions

- All API responses wrapped in standard format; errors throw `ApiError` or `ResourceNotFoundException`
- Secrets encrypted with AES-256-GCM, per-secret random IV stored alongside
- Server auth types: `PASSWORD` or `PRIVATE_KEY`, credentials referenced by secret ID
- Deployment script types: `GENERAL`, `INSTALL`, `REMOVE`, `UPDATE`, `MAINTENANCE`
- Audit actions logged automatically for CRUD operations
- CORS configured via environment variables, not hardcoded
- WebSocket messages: `{ type: "INPUT"|"OUTPUT"|"ERROR"|"CLOSED"|"RESIZE", data: "..." }`
