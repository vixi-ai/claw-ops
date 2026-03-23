# Auth Module

## Purpose

Handles authentication and token management. Provides JWT-based login, token refresh, logout, and current-user retrieval. This is the gateway to the entire system — no other module is accessible without valid authentication.

## Package

`com.openclaw.manager.openclawserversmanager.auth`

## Components

### Entity: `RefreshToken`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| userId | UUID | FK → User, NOT NULL |
| token | String | UNIQUE, NOT NULL |
| expiresAt | Instant | NOT NULL |
| revoked | boolean | default false |
| createdAt | Instant | auto-set |

### DTOs

**`LoginRequest`**
- `email` — `@NotBlank @Email`
- `password` — `@NotBlank @Size(min = 8)`

**`LoginResponse`**
- `accessToken` — JWT access token
- `refreshToken` — opaque refresh token
- `expiresIn` — seconds until access token expiry
- `tokenType` — always "Bearer"

**`RefreshTokenRequest`**
- `refreshToken` — `@NotBlank`

**`UserInfoResponse`**
- `id`, `email`, `username`, `role`

### Service: `AuthService`

- `login(LoginRequest)` — validates credentials, generates JWT + refresh token
- `refresh(RefreshTokenRequest)` — validates refresh token, issues new JWT
- `logout(refreshToken)` — revokes refresh token
- `getCurrentUser(Authentication)` — returns current user info

### Config: `SecurityConfig`

- Configures `SecurityFilterChain`
- JWT filter in the chain (`JwtAuthenticationFilter`)
- Stateless session management
- CORS configuration
- Endpoint security rules

### Config: `JwtConfig`

- Properties: `jwt.secret`, `jwt.access-token-expiration`, `jwt.refresh-token-expiration`
- JWT utility methods: generate, validate, extract claims

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/auth/login` | Public | Authenticate and receive tokens |
| POST | `/api/v1/auth/refresh` | Public | Refresh access token |
| POST | `/api/v1/auth/logout` | Authenticated | Revoke refresh token |
| GET | `/api/v1/auth/me` | Authenticated | Get current user info |

## Business Rules

- Access tokens are short-lived (15–30 minutes)
- Refresh tokens are long-lived (7–30 days) and stored in DB
- On logout, refresh token is revoked (not deleted — kept for audit)
- Failed login attempts should be logged (audit module)
- **No public signup** — users are created through the Users module by admins only
- JWT secret must come from environment variable, never hardcoded

## Security Considerations

- Passwords verified using BCrypt/Argon2 (never compared as plaintext)
- JWT must include: `sub` (user ID), `role`, `iat`, `exp`
- Refresh token rotation: issuing a new refresh token invalidates the old one
- Rate limiting on login endpoint (future enhancement)

## Dependencies

- **users** — to look up user by email and verify password
- **audit** — to log login/logout events
