# Task 3: Complete Auth & User System

**Status:** DONE
**Module(s):** auth, users, common
**Priority:** HIGH
**Created:** 2026-03-11
**Completed:** 2026-03-11

## Description
Implement the full authentication and user management system: User entity, JWT-based login/refresh/logout, Spring Security configuration, role-based access control, and user CRUD (admin-only). This is the foundation that every other module depends on.

## Acceptance Criteria

### Common Module
- [ ] Global exception handler (`@RestControllerAdvice`) with consistent error response format
- [ ] Custom exceptions: `ResourceNotFoundException`, `DuplicateResourceException`, `AccessDeniedException`
- [ ] Error response DTO: `{ status, error, message, timestamp }`

### User Module
- [ ] `User` entity with JPA mappings (UUID PK, email unique, username unique, passwordHash, role enum, enabled, timestamps)
- [ ] `Role` enum: `ADMIN`, `DEVOPS`
- [ ] Flyway migration `V2__create_users_table.sql`
- [ ] `UserRepository` with `findByEmail`, `findByUsername`, `existsByEmail`, `existsByUsername`
- [ ] `UserService` — createUser (hash password, check uniqueness), getAllUsers (paginated), getUserById, updateUser (partial), changePassword, deleteUser, disableUser
- [ ] `UserController` — all endpoints ADMIN-only, `@Valid` on requests
- [ ] Change password endpoint: `POST /api/v1/users/{id}/change-password` — ADMIN can change any user's password, user can change their own
- [ ] Delete user endpoint: `DELETE /api/v1/users/{id}` — ADMIN only, must revoke all refresh tokens and handle cascading cleanup
- [ ] DTOs: `CreateUserRequest`, `UpdateUserRequest`, `ChangePasswordRequest`, `UserResponse` — passwordHash never exposed
- [ ] `UserMapper` — static methods, entity ↔ DTO
- [ ] `PasswordEncoder` bean (BCrypt)
- [ ] Admin bootstrap: `CommandLineRunner` that creates initial admin from env vars (`ADMIN_EMAIL`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`) if no users exist

### Auth Module
- [ ] `RefreshToken` entity (UUID PK, userId FK, token unique, expiresAt, revoked, createdAt)
- [ ] Flyway migration `V3__create_refresh_tokens_table.sql`
- [ ] `RefreshTokenRepository` with `findByToken`, `deleteByUserId`
- [ ] JWT utility class — generate access token (sub=userId, role, iat, exp), validate, extract claims
- [ ] `JwtAuthenticationFilter` — `OncePerRequestFilter`, extracts Bearer token, validates, sets SecurityContext
- [ ] `SecurityConfig` — filter chain: stateless sessions, JWT filter, CORS, endpoint rules (permitAll for `/api/v1/auth/**` and Swagger paths, authenticated for rest)
- [ ] `AuthService` — login (validate creds, issue tokens), refresh (validate + rotate refresh token), logout (revoke refresh token), getCurrentUser
- [ ] `AuthController` — POST `/api/v1/auth/login`, POST `/api/v1/auth/refresh`, POST `/api/v1/auth/logout`, GET `/api/v1/auth/me`
- [ ] DTOs: `LoginRequest`, `LoginResponse` (accessToken, refreshToken, expiresIn, tokenType), `RefreshTokenRequest`, `UserInfoResponse`
- [ ] Access tokens: 15 min TTL (configurable via env)
- [ ] Refresh tokens: 7 days TTL (configurable via env), stored in DB, rotated on use
- [ ] Swagger `/api/v1/auth/**` endpoints marked as public (no lock icon)

### Dependencies to Add (pom.xml)
- [ ] `spring-boot-starter-security`
- [ ] `io.jsonwebtoken:jjwt-api` + `jjwt-impl` + `jjwt-jackson` (latest stable, e.g., 0.12.6)

### Integration
- [ ] Swagger UI still accessible without authentication
- [ ] All `/api/v1/users/**` endpoints require ADMIN role
- [ ] JWT Bearer token works in Swagger "Authorize" button
- [ ] `.env.example` updated with `ADMIN_EMAIL`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`

## Implementation Notes

### Recommended implementation order
1. **Common exceptions + error handler** — needed by everything
2. **pom.xml dependencies** — Spring Security + JJWT
3. **User entity + migration + repository** — data layer first
4. **PasswordEncoder bean + UserService + UserMapper** — business logic
5. **RefreshToken entity + migration + repository** — auth data layer
6. **JWT utility class** — token generation/validation
7. **AuthService** — login, refresh, logout logic
8. **JwtAuthenticationFilter** — request filter
9. **SecurityConfig** — wire everything together
10. **AuthController + UserController** — expose endpoints
11. **Admin bootstrap runner** — seed initial admin
12. **Update .env.example** — add admin creds vars

### Security note on passwords
- Passwords are **hashed** (BCrypt), not encrypted — hashing is one-way, you cannot retrieve the original password
- This is intentional: passwords are credentials that only need to be **verified**, never **retrieved**
- This is different from SSH keys/API tokens (in the secrets module) which use AES-GCM **encryption** because they need to be decrypted for use

### Key technical decisions
- Use JJWT library (not Spring Security's built-in OAuth2 Resource Server) for simpler JWT handling
- `PasswordEncoder` as a `@Bean` in a config class (not in UserService)
- JWT secret from `JWT_SECRET` env var, must be base64-encoded, minimum 256 bits
- RefreshToken stored as opaque UUID string (not JWT) — simpler to revoke
- On refresh: old token revoked, new token issued (rotation)
- `UserDetailsService` not needed — custom JWT filter handles auth directly
- SecurityConfig must permitAll: `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/swagger-ui/**`, `/v3/api-docs/**`, `/swagger-ui.html`

### Migration SQL hints
```sql
-- V2__create_users_table.sql
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'DEVOPS',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- V3__create_refresh_tokens_table.sql
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
```

### Package structure
```
com.openclaw.manager.openclawserversmanager/
├── common/
│   ├── config/OpenApiConfig.java (exists)
│   ├── dto/ErrorResponse.java
│   └── exception/
│       ├── GlobalExceptionHandler.java
│       ├── ResourceNotFoundException.java
│       ├── DuplicateResourceException.java
│       └── AccessDeniedException.java
├── auth/
│   ├── config/SecurityConfig.java
│   ├── config/JwtConfig.java
│   ├── controller/AuthController.java
│   ├── dto/LoginRequest.java
│   ├── dto/LoginResponse.java
│   ├── dto/RefreshTokenRequest.java
│   ├── dto/UserInfoResponse.java
│   ├── entity/RefreshToken.java
│   ├── filter/JwtAuthenticationFilter.java
│   ├── repository/RefreshTokenRepository.java
│   └── service/AuthService.java
├── users/
│   ├── config/AdminBootstrapRunner.java
│   ├── controller/UserController.java
│   ├── dto/CreateUserRequest.java
│   ├── dto/UpdateUserRequest.java
│   ├── dto/ChangePasswordRequest.java
│   ├── dto/UserResponse.java
│   ├── entity/User.java
│   ├── entity/Role.java
│   ├── mapper/UserMapper.java
│   ├── repository/UserRepository.java
│   └── service/UserService.java
```

## Files Modified

### pom.xml
- Added `spring-boot-starter-security`, `jjwt-api`, `jjwt-impl`, `jjwt-jackson` (0.12.6)

### Common module (new files)
- `src/main/java/.../common/exception/ResourceNotFoundException.java`
- `src/main/java/.../common/exception/DuplicateResourceException.java`
- `src/main/java/.../common/exception/AccessDeniedException.java`
- `src/main/java/.../common/dto/ErrorResponse.java`
- `src/main/java/.../common/exception/GlobalExceptionHandler.java`

### Users module (new files)
- `src/main/java/.../users/entity/Role.java`
- `src/main/java/.../users/entity/User.java`
- `src/main/java/.../users/repository/UserRepository.java`
- `src/main/java/.../users/dto/CreateUserRequest.java`
- `src/main/java/.../users/dto/UpdateUserRequest.java`
- `src/main/java/.../users/dto/ChangePasswordRequest.java`
- `src/main/java/.../users/dto/UserResponse.java`
- `src/main/java/.../users/mapper/UserMapper.java`
- `src/main/java/.../users/config/PasswordEncoderConfig.java`
- `src/main/java/.../users/service/UserService.java`
- `src/main/java/.../users/controller/UserController.java`
- `src/main/java/.../users/config/AdminBootstrapRunner.java`
- `src/main/resources/db/migration/V2__create_users_table.sql`

### Auth module (new files)
- `src/main/java/.../auth/entity/RefreshToken.java`
- `src/main/java/.../auth/repository/RefreshTokenRepository.java`
- `src/main/java/.../auth/config/JwtConfig.java`
- `src/main/java/.../auth/service/JwtService.java`
- `src/main/java/.../auth/dto/LoginRequest.java`
- `src/main/java/.../auth/dto/LoginResponse.java`
- `src/main/java/.../auth/dto/RefreshTokenRequest.java`
- `src/main/java/.../auth/dto/UserInfoResponse.java`
- `src/main/java/.../auth/service/AuthService.java`
- `src/main/java/.../auth/filter/JwtAuthenticationFilter.java`
- `src/main/java/.../auth/config/SecurityConfig.java`
- `src/main/java/.../auth/controller/AuthController.java`
- `src/main/resources/db/migration/V3__create_refresh_tokens_table.sql`

### Other
- `.env.example` — added `ADMIN_EMAIL`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`
