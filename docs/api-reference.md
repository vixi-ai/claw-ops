# API Reference

Base URL: `http://localhost:8080/api/v1`

All endpoints return JSON. Authentication uses Bearer JWT tokens unless marked as public. Interactive documentation is available at `/swagger-ui.html`.

## Table of Contents

- [Authentication](#authentication)
- [Users](#users)
- [Servers](#servers)
- [Secrets](#secrets)
- [Deployment Scripts](#deployment-scripts)
- [Deployment Jobs](#deployment-jobs)
- [Agent Templates](#agent-templates)
- [DNS Provider Accounts](#dns-provider-accounts)
- [Managed Zones](#managed-zones)
- [Domain Assignments](#domain-assignments)
- [SSL Certificates](#ssl-certificates)
- [Audit Logs](#audit-logs)

---

## Authentication

### POST /auth/login

Authenticate and obtain JWT tokens. **Public endpoint.**

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "admin@openclaw.dev", "password": "changeme"}'
```

**Request:**
```json
{
  "email": "admin@openclaw.dev",
  "password": "changeme"
}
```

**Response (200):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

### POST /auth/refresh

Refresh an expired access token. **Public endpoint.**

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"}'
```

### POST /auth/logout

Revoke a refresh token. **Public endpoint.**

```bash
curl -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Content-Type: application/json" \
  -d '{"refreshToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"}'
```

### GET /auth/me

Get the currently authenticated user's info.

```bash
curl http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

---

## Users

All user endpoints require **ADMIN** role.

### POST /users

Create a new user.

```bash
curl -X POST http://localhost:8080/api/v1/users \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "devops@example.com",
    "username": "devops1",
    "password": "securepass123",
    "role": "DEVOPS"
  }'
```

**Response (201):**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "devops@example.com",
  "username": "devops1",
  "role": "DEVOPS",
  "enabled": true,
  "createdAt": "2026-03-23T10:00:00Z",
  "updatedAt": "2026-03-23T10:00:00Z"
}
```

### GET /users

List users (paginated).

```bash
curl "http://localhost:8080/api/v1/users?page=0&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

### GET /users/{id}

Get user by ID.

### PATCH /users/{id}

Update user fields (email, username, role, enabled).

### POST /users/{id}/change-password

Change a user's password.

```bash
curl -X POST http://localhost:8080/api/v1/users/$USER_ID/change-password \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"newPassword": "newsecurepass"}'
```

### DELETE /users/{id}

Delete a user.

---

## Servers

### POST /servers

Register a new server.

```bash
curl -X POST http://localhost:8080/api/v1/servers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "prod-web-1",
    "hostname": "ec2-1-2-3-4.compute.amazonaws.com",
    "ipAddress": "1.2.3.4",
    "sshPort": 22,
    "sshUsername": "ubuntu",
    "authType": "KEY",
    "credentialId": "secret-uuid-here",
    "environment": "production"
  }'
```

**Response (201):**
```json
{
  "id": "...",
  "name": "prod-web-1",
  "hostname": "ec2-1-2-3-4.compute.amazonaws.com",
  "ipAddress": "1.2.3.4",
  "sshPort": 22,
  "sshUsername": "ubuntu",
  "authType": "KEY",
  "credentialId": "...",
  "environment": "production",
  "assignedDomain": "prod-web-1.yourdomain.com",
  "sslEnabled": false,
  "status": "UNKNOWN",
  "createdAt": "2026-03-23T10:00:00Z",
  "updatedAt": "2026-03-23T10:00:00Z"
}
```

### GET /servers

List servers (paginated). Supports `page`, `size`, `sort` query params.

### GET /servers/{id}

Get server by ID.

### PATCH /servers/{id}

Update server fields.

### DELETE /servers/{id}

Delete server. **ADMIN only.**

### POST /servers/{id}/test-connection

Test SSH connection to a server.

```bash
curl -X POST http://localhost:8080/api/v1/servers/$SERVER_ID/test-connection \
  -H "Authorization: Bearer $TOKEN"
```

**Response:**
```json
{
  "success": true,
  "message": "Connection successful",
  "latencyMs": 245
}
```

### POST /servers/{id}/ssh/command

Execute a command on a server.

```bash
curl -X POST http://localhost:8080/api/v1/servers/$SERVER_ID/ssh/command \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"command": "uptime", "timeoutSeconds": 30}'
```

**Response:**
```json
{
  "exitCode": 0,
  "stdout": " 10:00:00 up 30 days, 5:00, 1 user, load average: 0.00, 0.00, 0.00",
  "stderr": "",
  "durationMs": 312
}
```

### GET /servers/{id}/ssh/session-token

Generate a WebSocket terminal session token.

---

## Secrets

### POST /secrets

Create an encrypted secret.

```bash
curl -X POST http://localhost:8080/api/v1/secrets \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "prod-ssh-key",
    "type": "SSH_KEY",
    "value": "-----BEGIN OPENSSH PRIVATE KEY-----\n...",
    "description": "Production SSH private key"
  }'
```

### GET /secrets

List secrets (paginated). **Note:** the `value` field is never returned in list responses.

### GET /secrets/{id}

Get secret by ID.

### PATCH /secrets/{id}

Update a secret's name, description, or value.

### DELETE /secrets/{id}

Delete a secret. **ADMIN only.**

---

## Deployment Scripts

### POST /deployment-scripts

Create a deployment script. **ADMIN only.**

```bash
curl -X POST http://localhost:8080/api/v1/deployment-scripts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "install-docker",
    "description": "Install Docker CE on Ubuntu",
    "scriptType": "INSTALL",
    "scriptContent": "#!/bin/bash\nset -e\ncurl -fsSL https://get.docker.com | sh"
  }'
```

**Script types:** `GENERAL`, `INSTALL`, `REMOVE`, `UPDATE`, `MAINTENANCE`

### GET /deployment-scripts

List scripts (paginated).

### GET /deployment-scripts/{id}

Get script by ID (includes `scriptContent`).

### PATCH /deployment-scripts/{id}

Update a script. **ADMIN only.**

### DELETE /deployment-scripts/{id}

Delete a script. **ADMIN only.**

---

## Deployment Jobs

### POST /deployment-jobs

Trigger a deployment job (runs script on server asynchronously).

```bash
curl -X POST http://localhost:8080/api/v1/deployment-jobs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "serverId": "server-uuid",
    "scriptId": "script-uuid"
  }'
```

**Response (201):**
```json
{
  "id": "job-uuid",
  "serverId": "server-uuid",
  "scriptId": "script-uuid",
  "scriptName": "install-docker",
  "status": "PENDING",
  "triggeredBy": "user-uuid",
  "startedAt": null,
  "finishedAt": null,
  "logs": null,
  "errorMessage": null,
  "createdAt": "2026-03-23T10:00:00Z"
}
```

**Job statuses:** `PENDING` -> `RUNNING` -> `COMPLETED` or `FAILED`. Also `CANCELLED`.

**Error (409):** if the server already has a RUNNING job.

### GET /deployment-jobs

List jobs (paginated). Optional filters:

| Parameter | Type | Description |
|-----------|------|-------------|
| `serverId` | UUID | Filter by server |
| `status` | String | Filter by status (PENDING, RUNNING, COMPLETED, FAILED, CANCELLED) |

```bash
curl "http://localhost:8080/api/v1/deployment-jobs?serverId=$SID&status=COMPLETED&size=20" \
  -H "Authorization: Bearer $TOKEN"
```

### GET /deployment-jobs/{id}

Get job by ID (includes `logs` and `errorMessage`).

### POST /deployment-jobs/{id}/cancel

Cancel a PENDING job. Returns 409 if job is RUNNING or already terminal.

---

## Agent Templates

### POST /agent-templates

Create a template. **ADMIN only.**

```bash
curl -X POST http://localhost:8080/api/v1/agent-templates \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Web Scraper Agent",
    "agentType": "web-scraper",
    "description": "Installs web scraper agent with default skills",
    "installScript": "#!/bin/bash\nset -e\nAGENT_TYPE=\"web-scraper\"\nBASE_DIR=\"$HOME/openclaw/agents/$AGENT_TYPE\"\nmkdir -p \"$BASE_DIR/skills\"\necho \"Agent installed at $BASE_DIR\""
  }'
```

**agentType** must match pattern `[a-z0-9-]+` (lowercase alphanumeric with hyphens).

### GET /agent-templates

List templates (paginated).

### GET /agent-templates/{id}

Get template by ID (includes `installScript`).

### PATCH /agent-templates/{id}

Update a template. **ADMIN only.**

### DELETE /agent-templates/{id}

Delete a template. **ADMIN only.**

### POST /agent-templates/{id}/deploy

Deploy a template to a server. Creates a deployment job.

```bash
curl -X POST http://localhost:8080/api/v1/agent-templates/$TEMPLATE_ID/deploy \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"serverId": "server-uuid"}'
```

**Response (201):**
```json
{
  "jobId": "deployment-job-uuid"
}
```

---

## DNS Provider Accounts

### POST /provider-accounts

Create a DNS provider account.

```bash
curl -X POST http://localhost:8080/api/v1/provider-accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Cloudflare",
    "providerType": "CLOUDFLARE",
    "apiToken": "your-cloudflare-api-token"
  }'
```

**Provider types:** `CLOUDFLARE`, `NAMECHEAP`

### GET /provider-accounts

List provider accounts (paginated).

### GET /provider-accounts/{id}

Get account by ID.

### PATCH /provider-accounts/{id}

Update account.

### DELETE /provider-accounts/{id}

Delete account. **ADMIN only.**

### POST /provider-accounts/{id}/validate

Validate provider credentials.

### POST /provider-accounts/{id}/sync-domains

Sync (import) all domains from the provider into managed zones.

### GET /provider-accounts/{id}/capabilities

Get provider capabilities.

---

## Managed Zones

### POST /zones

Create a managed zone (a domain used for subdomain assignment).

```bash
curl -X POST http://localhost:8080/api/v1/zones \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "domain": "example.com",
    "providerAccountId": "provider-uuid"
  }'
```

### GET /zones

List zones (paginated).

### GET /zones/{id}

Get zone by ID.

### PATCH /zones/{id}

Update zone.

### DELETE /zones/{id}

Delete zone. **ADMIN only.**

### POST /zones/{id}/activate

Activate a zone for DNS operations.

### POST /zones/{id}/set-default

Set as the default zone for auto-assigning subdomains to new servers.

---

## Domain Assignments

### POST /domain-assignments/server

Auto-assign a subdomain to a server.

```bash
curl -X POST http://localhost:8080/api/v1/domain-assignments/server \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"serverId": "server-uuid"}'
```

### POST /domain-assignments/custom

Create a custom DNS record.

```bash
curl -X POST http://localhost:8080/api/v1/domain-assignments/custom \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "zoneId": "zone-uuid",
    "subdomain": "api",
    "recordType": "A",
    "target": "1.2.3.4"
  }'
```

### GET /domain-assignments

List assignments. Optional filters: `zoneId`, `resourceId`.

### GET /domain-assignments/{id}

Get assignment by ID.

### POST /domain-assignments/{id}/verify

Verify DNS propagation for an assignment.

### DELETE /domain-assignments/{id}

Release (delete) an assignment. **ADMIN only.**

### DELETE /domain-assignments/resource/{resourceId}

Release all assignments for a resource. **ADMIN only.**

### GET /domain-assignments/{id}/events

Get event history for an assignment.

---

## SSL Certificates

### POST /ssl-certificates

Provision an SSL certificate on a server.

```bash
curl -X POST http://localhost:8080/api/v1/ssl-certificates \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "serverId": "server-uuid",
    "domain": "myserver.example.com"
  }'
```

### GET /ssl-certificates

List certificates (paginated).

### GET /ssl-certificates/{id}

Get certificate by ID.

### GET /ssl-certificates/server/{serverId}

Get the active certificate for a specific server.

### POST /ssl-certificates/{id}/renew

Renew a certificate.

### POST /ssl-certificates/{id}/check

Check certificate status on the remote server.

### POST /ssl-certificates/provision-all

Bulk provision SSL for all servers that don't have an active certificate.

### DELETE /ssl-certificates/{id}

Remove a certificate. **ADMIN only.**

---

## Audit Logs

All audit endpoints require **ADMIN** role.

### GET /audit/logs

List audit logs (paginated, filterable).

```bash
curl "http://localhost:8080/api/v1/audit/logs?action=SERVER_CREATED&size=50" \
  -H "Authorization: Bearer $TOKEN"
```

**Filter parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `userId` | UUID | Filter by user |
| `action` | String | Filter by audit action |
| `entityType` | String | Filter by entity type (SERVER, SECRET, etc.) |
| `entityId` | UUID | Filter by entity ID |
| `from` | ISO DateTime | Start of time range |
| `to` | ISO DateTime | End of time range |

**Response:**
```json
{
  "content": [
    {
      "id": "...",
      "userId": "...",
      "action": "SERVER_CREATED",
      "entityType": "SERVER",
      "entityId": "...",
      "details": "Server 'prod-web-1' created (env: production)",
      "ipAddress": "192.168.1.100",
      "createdAt": "2026-03-23T10:00:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1
}
```

---

## Error Responses

All errors follow a consistent format:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Server with id 550e8400-... not found",
  "timestamp": "2026-03-23T10:00:00Z"
}
```

### Validation Errors (400)

```json
{
  "status": 400,
  "error": "Validation Failed",
  "messages": [
    { "field": "email", "message": "must be a valid email address" },
    { "field": "hostname", "message": "must not be blank" }
  ],
  "timestamp": "2026-03-23T10:00:00Z"
}
```

### HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | Success |
| 201 | Created |
| 204 | No Content (successful delete) |
| 400 | Bad Request / Validation Failed |
| 401 | Unauthorized (missing or invalid token) |
| 403 | Forbidden (insufficient role) |
| 404 | Not Found |
| 409 | Conflict (duplicate resource or deployment conflict) |
| 422 | Unprocessable Entity (domain/SSL error) |
| 500 | Internal Server Error |
| 502 | Bad Gateway (SSH or DNS provider error) |

## Pagination

All list endpoints support pagination via query parameters:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `page` | `0` | Page number (zero-based) |
| `size` | `20` | Page size |
| `sort` | varies | Sort field and direction (e.g., `createdAt,desc`) |

**Paginated response wrapper:**
```json
{
  "content": [...],
  "totalElements": 100,
  "totalPages": 5,
  "size": 20,
  "number": 0,
  "first": true,
  "last": false
}
```
