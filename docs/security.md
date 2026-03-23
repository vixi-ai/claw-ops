# Security

## Authentication

ClawOps uses JWT (JSON Web Tokens) for stateless authentication with a two-token system:

### Token Flow

1. **Login**: `POST /api/v1/auth/login` with email + password
2. **Receive tokens**: access token (short-lived) + refresh token (long-lived)
3. **Use access token**: include as `Authorization: Bearer <token>` header on all API requests
4. **Refresh when expired**: `POST /api/v1/auth/refresh` with the refresh token to get a new access token
5. **Logout**: `POST /api/v1/auth/logout` revokes the refresh token

### Token Lifetimes

| Token | Default Lifetime | Environment Variable |
|-------|-----------------|---------------------|
| Access token | 15 minutes | `JWT_ACCESS_TOKEN_EXPIRATION` |
| Refresh token | 7 days | `JWT_REFRESH_TOKEN_EXPIRATION` |

### Token Storage Advice

- **Browser**: store tokens in `localStorage` (the dev admin panel does this)
- **Server-to-server**: store in a secure configuration store
- **Never** include tokens in URLs or logs

## Roles

ClawOps has two roles:

### ADMIN

Full access to all operations:

- User management (create, update, delete users)
- Audit log viewing
- Create/update/delete deployment scripts and agent templates
- Delete servers, secrets, DNS resources, SSL certificates
- All operations available to DEVOPS

### DEVOPS

Read and execute access:

- View and manage servers (create, update — but not delete)
- View and create secrets (but not delete)
- Execute SSH commands and open terminal sessions
- Trigger deployment jobs and template deployments
- View deployment logs and audit logs for own operations
- Manage DNS assignments (create — but not delete)
- Provision SSL certificates

### Permission Matrix

| Operation | ADMIN | DEVOPS |
|-----------|-------|--------|
| Login / refresh / logout | Yes | Yes |
| View servers | Yes | Yes |
| Create/update servers | Yes | Yes |
| Delete servers | Yes | No |
| View secrets | Yes | Yes |
| Create/update secrets | Yes | Yes |
| Delete secrets | Yes | No |
| Execute SSH commands | Yes | Yes |
| Open terminal sessions | Yes | Yes |
| Create deployment scripts | Yes | No |
| Run deployment scripts | Yes | Yes |
| Create agent templates | Yes | No |
| Deploy templates | Yes | Yes |
| Manage users | Yes | No |
| View audit logs | Yes | No |
| Manage DNS providers | Yes | Yes |
| Delete DNS resources | Yes | No |
| Provision SSL | Yes | Yes |
| Delete SSL certificates | Yes | No |

## Secret Encryption

All sensitive data stored in the database is encrypted using AES-256-GCM:

### What's Encrypted

| Data Type | Storage |
|-----------|---------|
| SSH passwords | AES-GCM encrypted in `secrets` table |
| SSH private keys | AES-GCM encrypted in `secrets` table |
| API keys | AES-GCM encrypted in `secrets` table |
| DNS provider tokens | AES-GCM encrypted in `provider_accounts` table |

### Encryption Details

- **Algorithm**: AES-256-GCM (authenticated encryption)
- **Key**: derived from `MASTER_ENCRYPTION_KEY` environment variable
- **IV**: random 12-byte IV generated per encryption operation, stored alongside the ciphertext
- **Key format**: Base64-encoded 32-byte key

### Key Generation

```bash
openssl rand -base64 32
```

**Important**: the `MASTER_ENCRYPTION_KEY` must never be committed to source control. If lost, all encrypted secrets become unrecoverable.

## Password Hashing

User passwords are hashed using BCrypt with Spring Security's default strength (10 rounds). The original password is never stored.

## No Public Signup

ClawOps does not allow public user registration. All users must be created by:

1. **Admin bootstrap**: the first admin user is created automatically on first startup from environment variables
2. **Admin API**: `POST /api/v1/users` (ADMIN only)
3. **Admin UI**: the Users page in the dev admin panel

## API Security

### Public Endpoints (no authentication)

- `/api/v1/auth/**` — login, refresh, logout
- `/swagger-ui/**`, `/v3/api-docs/**` — API documentation
- `/dev/**` — static dev admin panel files
- `/ws/**` — WebSocket endpoint (authentication handled by handshake interceptor)

### CSRF

CSRF protection is disabled since the API is stateless (JWT-based) and does not use cookies for authentication.

### Session Management

Sessions are stateless (`SessionCreationPolicy.STATELESS`). No server-side session state is maintained.

## Audit Trail

Every mutation is logged to the `audit_logs` table with:

- **User ID**: who performed the action
- **Action**: what was done (e.g., `SERVER_CREATED`, `JOB_TRIGGERED`)
- **Entity type + ID**: what resource was affected
- **Details**: human-readable description
- **IP address**: client IP (captured via `AuditContext`)
- **Timestamp**: when it happened

Audit logging is best-effort — it never fails the main operation. If audit logging throws an exception, it is caught and logged to the application log.
