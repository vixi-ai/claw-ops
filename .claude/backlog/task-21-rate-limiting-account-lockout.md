# Task 21: Rate Limiting & Account Lockout

**Status:** DONE
**Module(s):** auth, users, common
**Priority:** HIGH
**Created:** 2026-03-24
**Completed:** 2026-03-24

## Description

The login endpoint (`POST /api/v1/auth/login`) has no brute-force protection. An attacker can attempt unlimited password guesses. This task adds:

1. **IP-based rate limiting** on the login endpoint — max N attempts per window per IP
2. **Account lockout** — after N consecutive failed login attempts, the account is locked for a configurable duration

Both mechanisms are configurable via environment variables.

## Acceptance Criteria

### Rate Limiting (IP-based)
- [ ] In-memory rate limiter using `ConcurrentHashMap` (no external dependency)
- [ ] Configurable via env vars: `LOGIN_RATE_LIMIT_MAX_ATTEMPTS` (default: 10), `LOGIN_RATE_LIMIT_WINDOW_SECONDS` (default: 60)
- [ ] Rate limit applied per IP address on `/api/v1/auth/login` only
- [ ] When limit exceeded, return HTTP 429 Too Many Requests with body: `{"status": 429, "error": "Too Many Requests", "message": "Too many login attempts. Try again later.", "timestamp": "..."}`
- [ ] Scheduled cleanup of expired rate limit entries every 5 minutes

### Account Lockout
- [ ] New columns on `users` table: `failed_login_attempts` (INT, default 0), `locked_until` (TIMESTAMPTZ, nullable)
- [ ] Flyway migration `V14__add_account_lockout_columns.sql`
- [ ] Configurable via env vars: `ACCOUNT_LOCKOUT_THRESHOLD` (default: 5), `ACCOUNT_LOCKOUT_DURATION_MINUTES` (default: 15)
- [ ] On failed login: increment `failedLoginAttempts`. If threshold reached, set `lockedUntil` to now + duration
- [ ] On successful login: reset `failedLoginAttempts` to 0 and `lockedUntil` to null
- [ ] If account is locked and lockout period hasn't expired, reject login with "Account is temporarily locked. Try again later."
- [ ] If account is locked but lockout period has expired, allow login attempt (auto-unlock)
- [ ] Audit log `USER_ACCOUNT_LOCKED` when account gets locked

### Configuration Properties
- [ ] New `LoginSecurityProperties` class in `auth/config/` with `@ConfigurationProperties(prefix = "login.security")`
- [ ] Properties: `rateLimitMaxAttempts`, `rateLimitWindowSeconds`, `lockoutThreshold`, `lockoutDurationMinutes`
- [ ] Added to `application.properties` with env var bindings

### Documentation
- [ ] Architecture log entries in `.claude/architecture/auth.md` and `.claude/architecture/users.md`

## Implementation Notes

### New Files
- `src/main/java/com/openclaw/manager/openclawserversmanager/auth/config/LoginSecurityProperties.java`
- `src/main/java/com/openclaw/manager/openclawserversmanager/auth/service/LoginRateLimiterService.java`
- `src/main/resources/db/migration/V14__add_account_lockout_columns.sql`

### Files to Modify
- `src/main/java/com/openclaw/manager/openclawserversmanager/users/entity/User.java` — add fields
- `src/main/java/com/openclaw/manager/openclawserversmanager/auth/service/AuthService.java` — integrate lockout + rate limit checks
- `src/main/java/com/openclaw/manager/openclawserversmanager/audit/entity/AuditAction.java` — add `USER_ACCOUNT_LOCKED`
- `src/main/resources/application.properties` — add login security properties
- `.env.example` — add login security env vars

### AuthService.login() Flow (Updated)
1. Check IP rate limit → 429 if exceeded
2. Find user by email → generic error if not found (increment rate limit counter)
3. Check if account locked → error if locked and not expired, auto-unlock if expired
4. Check if account disabled → error
5. Verify password → on failure: increment failed attempts, check lockout threshold, lock if reached
6. On success: reset failed attempts, issue tokens

## Files Modified
- `src/main/java/com/openclaw/manager/openclawserversmanager/auth/config/LoginSecurityProperties.java` — **created** — `@ConfigurationProperties` for rate limit + lockout config
- `src/main/java/com/openclaw/manager/openclawserversmanager/auth/service/LoginRateLimiterService.java` — **created** — in-memory IP-based rate limiter with scheduled cleanup
- `src/main/java/com/openclaw/manager/openclawserversmanager/common/exception/RateLimitExceededException.java` — **created** — 429 exception
- `src/main/resources/db/migration/V14__add_account_lockout_columns.sql` — **created** — adds failed_login_attempts and locked_until to users table
- `src/main/java/com/openclaw/manager/openclawserversmanager/users/entity/User.java` — **modified** — added failedLoginAttempts, lockedUntil fields and isAccountLocked() method
- `src/main/java/com/openclaw/manager/openclawserversmanager/auth/service/AuthService.java` — **modified** — added rate limit + lockout logic to login flow
- `src/main/java/com/openclaw/manager/openclawserversmanager/auth/controller/AuthController.java` — **modified** — extracts client IP and passes to login
- `src/main/java/com/openclaw/manager/openclawserversmanager/audit/entity/AuditAction.java` — **modified** — added USER_ACCOUNT_LOCKED
- `src/main/java/com/openclaw/manager/openclawserversmanager/common/exception/GlobalExceptionHandler.java` — **modified** — added 429 handler for RateLimitExceededException
- `src/main/resources/application.properties` — **modified** — added login.security properties
- `.env.example` — **modified** — added login security env vars
