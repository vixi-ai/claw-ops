# Audit Module

The audit module logs every significant operation in ClawOps. All mutations are tracked with the user, timestamp, affected resource, and a human-readable description.

## How It Works

- Every service that modifies data calls `AuditService.log()` after the operation
- Audit logging is **best-effort** — it never fails the main operation
- If audit logging throws an exception, it's caught and logged to the application log
- Client IP address is automatically captured via `AuditContext`

## Viewing Audit Logs

**ADMIN only.**

```bash
curl "http://localhost:8080/api/v1/audit/logs?size=50&sort=createdAt,desc" \
  -H "Authorization: Bearer $TOKEN"
```

### Filters

| Parameter | Type | Description |
|-----------|------|-------------|
| `userId` | UUID | Filter by user who performed the action |
| `action` | String | Filter by action type (e.g., `SERVER_CREATED`) |
| `entityType` | String | Filter by entity type (e.g., `SERVER`, `SECRET`) |
| `entityId` | UUID | Filter by specific entity |
| `from` | ISO DateTime | Start of time range |
| `to` | ISO DateTime | End of time range |

### Example: all actions by a specific user

```bash
curl "http://localhost:8080/api/v1/audit/logs?userId=$USER_ID&size=100" \
  -H "Authorization: Bearer $TOKEN"
```

### Example: all server-related events

```bash
curl "http://localhost:8080/api/v1/audit/logs?entityType=SERVER" \
  -H "Authorization: Bearer $TOKEN"
```

## Audit Actions

### User Lifecycle
| Action | Description |
|--------|-------------|
| `USER_LOGIN` | User logged in |
| `USER_LOGIN_FAILED` | Failed login attempt |
| `USER_LOGOUT` | User logged out |
| `USER_CREATED` | New user created |
| `USER_UPDATED` | User profile updated |
| `USER_DISABLED` | User account disabled |
| `USER_DELETED` | User deleted |
| `USER_PASSWORD_CHANGED` | Password changed |

### Servers
| Action | Description |
|--------|-------------|
| `SERVER_CREATED` | Server registered |
| `SERVER_UPDATED` | Server details updated |
| `SERVER_DELETED` | Server removed |
| `SERVER_CONNECTION_TESTED` | SSH connection tested |

### Secrets
| Action | Description |
|--------|-------------|
| `SECRET_CREATED` | Secret created |
| `SECRET_UPDATED` | Secret updated |
| `SECRET_DELETED` | Secret deleted |

### SSH
| Action | Description |
|--------|-------------|
| `SSH_COMMAND_EXECUTED` | Command executed on a server |

### Terminal
| Action | Description |
|--------|-------------|
| `TERMINAL_SESSION_REQUESTED` | Session token generated |
| `TERMINAL_SESSION_OPENED` | WebSocket session opened |
| `TERMINAL_SESSION_CLOSED` | Session ended |

### Deployment Scripts
| Action | Description |
|--------|-------------|
| `SCRIPT_CREATED` | Deployment script created |
| `SCRIPT_UPDATED` | Deployment script updated |
| `SCRIPT_DELETED` | Deployment script deleted |

### Deployment Jobs
| Action | Description |
|--------|-------------|
| `JOB_TRIGGERED` | Deployment job triggered |
| `JOB_CANCELLED` | Deployment job cancelled |

### Templates
| Action | Description |
|--------|-------------|
| `TEMPLATE_CREATED` | Agent template created |
| `TEMPLATE_UPDATED` | Agent template updated |
| `TEMPLATE_DELETED` | Agent template deleted |
| `TEMPLATE_DEPLOYED` | Template deployed to server |

### Domains
| Action | Description |
|--------|-------------|
| `PROVIDER_ACCOUNT_CREATED` | DNS provider account added |
| `PROVIDER_ACCOUNT_UPDATED` | Provider account updated |
| `PROVIDER_ACCOUNT_DELETED` | Provider account removed |
| `ZONE_CREATED` | Managed zone created |
| `ZONE_ACTIVATED` | Zone activated |
| `ZONE_DELETED` | Zone deleted |
| `DOMAIN_ASSIGNED` | Subdomain assigned |
| `DOMAIN_AUTO_ASSIGNED` | Subdomain auto-assigned on server create |
| `DOMAIN_RELEASED` | Domain assignment released |
| `DOMAIN_VERIFIED` | DNS propagation verified |

### SSL
| Action | Description |
|--------|-------------|
| `SSL_PROVISIONED` | SSL certificate provisioned |
| `SSL_RENEWED` | Certificate renewed |
| `SSL_REMOVED` | Certificate removed |
| `SSL_CHECK` | Certificate status checked |

## Log Entry Format

```json
{
  "id": "uuid",
  "userId": "uuid",
  "action": "SERVER_CREATED",
  "entityType": "SERVER",
  "entityId": "uuid",
  "details": "Server 'prod-web-1' created (env: production)",
  "ipAddress": "192.168.1.100",
  "createdAt": "2026-03-23T10:00:00Z"
}
```

## UI

The audit page (`/dev/audit.html`) provides:

- Paginated audit log table
- Filters for user, action, entity type, date range
- Human-readable details for each entry
