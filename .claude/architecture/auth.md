# Auth — Architecture Log

Living documentation of implemented code. Agents MUST append here after writing code for this module.

## Implemented Components

### RefreshToken Entity
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/auth/entity/RefreshToken.java`
- **Type:** entity
- **Description:** JPA entity for `refresh_tokens` table. UUID PK, ManyToOne to User (lazy), opaque token string (unique), expiresAt, revoked flag, createdAt. Helper methods: `isExpired()`, `isUsable()`.
- **Dependencies:** User entity (users module)
- **Date:** 2026-03-11

### V3 Migration
- **File(s):** `src/main/resources/db/migration/V3__create_refresh_tokens_table.sql`
- **Type:** migration
- **Description:** Creates `refresh_tokens` table with UUID PK, user_id FK (CASCADE delete), unique token, expires_at, revoked, created_at. Indexes on token and user_id.
- **Dependencies:** V2 (users table)
- **Date:** 2026-03-11

### RefreshTokenRepository
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/auth/repository/RefreshTokenRepository.java`
- **Type:** repository
- **Description:** Spring Data JPA repository. Methods: `findByToken`, `deleteByUserId`.
- **Dependencies:** RefreshToken entity
- **Date:** 2026-03-11

### JwtConfig
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/auth/config/JwtConfig.java`
- **Type:** config
- **Description:** Reads `JWT_SECRET` (base64-encoded), `JWT_ACCESS_TOKEN_EXPIRATION` (default 15min), `JWT_REFRESH_TOKEN_EXPIRATION` (default 7 days) from env. If no secret provided, generates a random key (suitable for dev only). Exposes `SecretKey` and TTL values.
- **Dependencies:** JJWT library
- **Date:** 2026-03-11

### JwtService
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/auth/service/JwtService.java`
- **Type:** service
- **Description:** JWT utility service. `generateAccessToken(User)` — creates JWT with sub=userId, role, email claims. `extractUserId`, `extractRole`, `isTokenValid` for parsing/validation. Uses JJWT library with HS256.
- **Dependencies:** JwtConfig
- **Date:** 2026-03-11

### Auth DTOs
- **File(s):**
  - `src/main/java/com/openclaw/manager/openclawserversmanager/auth/dto/LoginRequest.java`
  - `src/main/java/com/openclaw/manager/openclawserversmanager/auth/dto/LoginResponse.java`
  - `src/main/java/com/openclaw/manager/openclawserversmanager/auth/dto/RefreshTokenRequest.java`
  - `src/main/java/com/openclaw/manager/openclawserversmanager/auth/dto/UserInfoResponse.java`
- **Type:** dto
- **Description:** Java records. `LoginRequest` (email, password), `LoginResponse` (accessToken, refreshToken, expiresIn, tokenType), `RefreshTokenRequest` (refreshToken), `UserInfoResponse` (id, email, username, role).
- **Dependencies:** Jakarta Validation, Role enum
- **Date:** 2026-03-11

### AuthService
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/auth/service/AuthService.java`
- **Type:** service
- **Description:** Auth business logic. `login` — validates credentials, issues access + refresh tokens. `refresh` — validates refresh token, rotates (revoke old, create new). `logout` — revokes refresh token. `getCurrentUser` — returns user info by ID. `revokeAllUserTokens` — deletes all tokens for a user. Refresh tokens are opaque UUIDs stored in DB.
- **Dependencies:** UserRepository (users module), RefreshTokenRepository, JwtService, JwtConfig, PasswordEncoder
- **Date:** 2026-03-11

### JwtAuthenticationFilter
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/auth/filter/JwtAuthenticationFilter.java`
- **Type:** filter
- **Description:** `OncePerRequestFilter` that extracts Bearer token from Authorization header, validates it, and sets `SecurityContext` with `UsernamePasswordAuthenticationToken` (principal=userId UUID, authorities=ROLE_{role}). No `UserDetailsService` needed.
- **Dependencies:** JwtService
- **Date:** 2026-03-11

### SecurityConfig
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/auth/config/SecurityConfig.java`
- **Type:** config
- **Description:** Spring Security filter chain. CSRF disabled, stateless sessions, JWT filter added before `UsernamePasswordAuthenticationFilter`. Endpoint rules: `/api/v1/auth/**` permitAll, Swagger paths permitAll, `/api/v1/users/**` requires ADMIN role, everything else authenticated. `@EnableMethodSecurity` enabled.
- **Dependencies:** JwtAuthenticationFilter
- **Date:** 2026-03-11

### SecurityConfig — Security Headers & Swagger Toggle
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/auth/config/SecurityConfig.java` (modified)
- **Type:** config (modified)
- **Description:** Added explicit HTTP security headers to `SecurityFilterChain`: X-Frame-Options (DENY), X-Content-Type-Options (nosniff), HSTS (1 year, includeSubDomains), Referrer-Policy (strict-origin-when-cross-origin), Permissions-Policy (camera/microphone/geolocation disabled), Cache-Control (no-cache). Added Swagger UI conditional access: Swagger paths only permitAll when `SWAGGER_ENABLED=true` (via `@Value`). Refactored `authorizeHttpRequests` from chained lambda to block lambda to support conditional logic.
- **Dependencies:** `springdoc.swagger-ui.enabled` property, CorsConfigurationSource
- **Date:** 2026-03-24

### LoginSecurityProperties
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/auth/config/LoginSecurityProperties.java`
- **Type:** config
- **Description:** `@ConfigurationProperties(prefix = "login.security")` — rate limit max attempts, rate limit window seconds, lockout threshold, lockout duration minutes. All configurable via env vars.
- **Dependencies:** None
- **Date:** 2026-03-24

### LoginRateLimiterService
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/auth/service/LoginRateLimiterService.java`
- **Type:** service
- **Description:** In-memory IP-based rate limiter using ConcurrentHashMap. `isRateLimited(ip)` checks if IP exceeded max attempts within time window. `recordAttempt(ip)` increments counter. Scheduled cleanup every 5 minutes removes expired entries. Thread-safe via ConcurrentHashMap + AtomicInteger.
- **Dependencies:** LoginSecurityProperties
- **Date:** 2026-03-24

### AuthService — Rate Limiting & Account Lockout
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/auth/service/AuthService.java` (modified)
- **Type:** service (modified)
- **Description:** Login flow updated: (1) IP rate limit check → 429, (2) find user, (3) account lockout check with auto-unlock on expired lock, (4) disabled check, (5) password verify with failed attempt tracking, (6) success resets counters. New `handleFailedLogin()` method increments failed attempts and locks account at threshold. Audit logs `USER_ACCOUNT_LOCKED` on lockout.
- **Dependencies:** LoginRateLimiterService, LoginSecurityProperties, UserRepository
- **Date:** 2026-03-24

### AuthController — IP Address Extraction
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/auth/controller/AuthController.java` (modified)
- **Type:** controller (modified)
- **Description:** Login endpoint updated to extract client IP from `HttpServletRequest` (supports X-Forwarded-For) and pass to `AuthService.login()`.
- **Dependencies:** AuthService
- **Date:** 2026-03-24

### V14 Migration — Account Lockout Columns
- **File(s):** `src/main/resources/db/migration/V14__add_account_lockout_columns.sql`
- **Type:** migration
- **Description:** Adds `failed_login_attempts` (INT, default 0) and `locked_until` (TIMESTAMPTZ, nullable) columns to `users` table.
- **Dependencies:** V2 (users table)
- **Date:** 2026-03-24

### RateLimitExceededException
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/common/exception/RateLimitExceededException.java`
- **Type:** exception
- **Description:** Custom exception for HTTP 429 Too Many Requests. Thrown when IP-based login rate limit is exceeded. Handled by GlobalExceptionHandler.
- **Dependencies:** None
- **Date:** 2026-03-24
