# Task 15: SSL Automation via Certbot + Nginx Reverse Proxy

**Status:** DONE
**Module(s):** domains, servers, audit
**Priority:** HIGH
**Created:** 2026-03-12
**Completed:** 2026-03-12

## Description

When a server gets a domain assignment, it should also automatically get an SSL certificate and Nginx reverse proxy config via SSH. The control plane SSHs into the target server, installs certbot + nginx, uploads an nginx config, and runs certbot to obtain a Let's Encrypt certificate. Follows the project's orchestration pattern: Java backend orchestrates → uploads script → executes via SSH → tracks status.

## Acceptance Criteria

- [x] `ssl_certificates` table created via Flyway migration V11
- [x] `SslCertificate` entity + `SslStatus` enum
- [x] `SslCertificateRepository` with finder methods
- [x] `SslCertificateResponse` + `ProvisionSslRequest` DTOs
- [x] `SslCertificateMapper` with static `toResponse()`
- [x] `SslService` — core orchestration: provision, renew, remove, check via SSH
- [x] `SslController` at `/api/v1/ssl-certificates` — CRUD + provision/renew/check/remove
- [x] `SslConfig` — admin email + target port from `application.properties`
- [x] Auto-provision SSL after successful domain assignment (best-effort, non-blocking)
- [x] Cleanup SSL on server deletion (best-effort)
- [x] `AuditAction` additions: SSL_PROVISIONED, SSL_RENEWED, SSL_REMOVED, SSL_CHECK
- [x] SecurityConfig updated for SSL endpoints
- [x] `servers.html` updated with SSL status column + Provision/Renew buttons
- [ ] Build succeeds

## Implementation Notes

### Existing infrastructure to reuse

| Component | File | What it provides |
|-----------|------|------------------|
| SSH command execution | `ssh/service/SshService.java` | `executeCommand(server, command, timeout)` |
| SSH file upload | `ssh/service/SshService.java` | `uploadFile(server, content, remotePath)` |
| Server entity | `servers/entity/Server.java` | `sslEnabled` boolean (exists, unused) |
| Domain assignment | `domains/service/DomainAssignmentService.java` | Server-to-domain mapping |
| Server service | `servers/service/ServerService.java` | `getServerEntity(id)` |

### New files (10)

| File | Type |
|------|------|
| `db/migration/V11__create_ssl_certificates_table.sql` | Migration |
| `domains/entity/SslCertificate.java` | Entity |
| `domains/entity/SslStatus.java` | Enum |
| `domains/repository/SslCertificateRepository.java` | Repository |
| `domains/dto/SslCertificateResponse.java` | DTO |
| `domains/dto/ProvisionSslRequest.java` | DTO |
| `domains/mapper/SslCertificateMapper.java` | Mapper |
| `domains/service/SslService.java` | Service |
| `domains/controller/SslController.java` | Controller |
| `domains/config/SslConfig.java` | Config |

### Modified files (6)

| File | Change |
|------|--------|
| `audit/entity/AuditAction.java` | Add SSL_PROVISIONED, SSL_RENEWED, SSL_REMOVED, SSL_CHECK |
| `auth/config/SecurityConfig.java` | Add SSL endpoint security rules |
| `domains/service/DomainAssignmentService.java` | Auto-provision SSL after domain assignment |
| `servers/service/ServerService.java` | Cleanup SSL on server deletion |
| `src/main/resources/static/dev/servers.html` | SSL status column + buttons |
| `src/main/resources/application.yml` | Add `ssl.admin-email`, `ssl.target-port` |

### SslService orchestration flow

**Provision:**
1. Load server + domain assignment → get hostname + IP
2. Create `SslCertificate` record (status: PROVISIONING)
3. SSH: `apt-get install -y nginx certbot python3-certbot-nginx`
4. Upload nginx config via SFTP to `/etc/nginx/sites-available/{hostname}`
5. Symlink to sites-enabled, reload nginx
6. SSH: `certbot --nginx -d {hostname} --non-interactive --agree-tos --email {admin_email}`
7. Success → status=ACTIVE, expiresAt=now+90d, server.sslEnabled=true
8. Failure → status=FAILED, lastError=stderr

**Nginx config template:**
```nginx
server {
    listen 80;
    server_name {hostname};
    location / {
        proxy_pass http://127.0.0.1:{targetPort};
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

**Renew:** SSH `certbot renew --cert-name {hostname}` → update expiresAt, lastRenewedAt

**Remove:** SSH `certbot delete --cert-name {hostname}` → remove nginx config → reload → delete record → server.sslEnabled=false

**Check:** SSH `certbot certificates --cert-name {hostname}` → parse expiry → update record

### API endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST /api/v1/ssl-certificates` | Provision SSL | Body: ProvisionSslRequest |
| `GET /api/v1/ssl-certificates` | List all | Pageable |
| `GET /api/v1/ssl-certificates/{id}` | Get by ID | |
| `GET /api/v1/ssl-certificates/server/{serverId}` | Get for server | |
| `POST /api/v1/ssl-certificates/{id}/renew` | Renew cert | |
| `POST /api/v1/ssl-certificates/{id}/check` | Check status | |
| `DELETE /api/v1/ssl-certificates/{id}` | Remove SSL | |

### Key design decisions

- SSL provisioning is **best-effort and non-blocking** — failure doesn't roll back domain assignment
- Uses certbot's `--nginx` flag which auto-modifies nginx config for HTTPS
- Let's Encrypt certs expire in 90 days — `expiresAt` tracked for future scheduled renewal
- `SslConfig` provides admin email + target port via `application.yml` (configurable per environment)
- Circular dependency: `DomainAssignmentService` → `SslService` → `ServerService` — use `@Lazy` on SslService injection if needed

## Files Modified

### New files (10)
- `src/main/resources/db/migration/V11__create_ssl_certificates_table.sql`
- `domains/entity/SslStatus.java`
- `domains/entity/SslCertificate.java`
- `domains/repository/SslCertificateRepository.java`
- `domains/dto/SslCertificateResponse.java`
- `domains/dto/ProvisionSslRequest.java`
- `domains/mapper/SslCertificateMapper.java`
- `domains/config/SslConfig.java`
- `domains/service/SslService.java`
- `domains/controller/SslController.java`

### Modified files (6)
- `audit/entity/AuditAction.java` — added SSL_PROVISIONED, SSL_RENEWED, SSL_REMOVED, SSL_CHECK
- `auth/config/SecurityConfig.java` — ADMIN-only DELETE for /api/v1/ssl-certificates/**
- `domains/service/DomainAssignmentService.java` — @Lazy SslService, auto-provision after DNS creation
- `servers/service/ServerService.java` — @Lazy SslService, SSL cleanup on delete, updateSslEnabled()
- `src/main/resources/application.properties` — ssl.admin-email, ssl.target-port
- `src/main/resources/static/dev/servers.html` — SSL column + Provision/Renew/Retry buttons
