# Users — Architecture Log

Living documentation of implemented code. Agents MUST append here after writing code for this module.

## Implemented Components

### Role Enum
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/users/entity/Role.java`
- **Type:** entity (enum)
- **Description:** Defines user roles: `ADMIN`, `DEVOPS`
- **Dependencies:** None
- **Date:** 2026-03-11

### User Entity
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/users/entity/User.java`
- **Type:** entity
- **Description:** JPA entity for `users` table. UUID PK, unique email/username, BCrypt password hash, Role enum, enabled flag, timestamps with `@PreUpdate` auto-update.
- **Dependencies:** Role enum
- **Date:** 2026-03-11

### V2 Migration
- **File(s):** `src/main/resources/db/migration/V2__create_users_table.sql`
- **Type:** migration
- **Description:** Creates `users` table with UUID PK (gen_random_uuid), unique email/username, password_hash, role, enabled, timestamps.
- **Dependencies:** V1 (pgcrypto extension)
- **Date:** 2026-03-11

### UserRepository
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/users/repository/UserRepository.java`
- **Type:** repository
- **Description:** Spring Data JPA repository. Methods: `findByEmail`, `findByUsername`, `existsByEmail`, `existsByUsername`.
- **Dependencies:** User entity
- **Date:** 2026-03-11

### User DTOs
- **File(s):**
  - `src/main/java/com/openclaw/manager/openclawserversmanager/users/dto/CreateUserRequest.java`
  - `src/main/java/com/openclaw/manager/openclawserversmanager/users/dto/UpdateUserRequest.java`
  - `src/main/java/com/openclaw/manager/openclawserversmanager/users/dto/ChangePasswordRequest.java`
  - `src/main/java/com/openclaw/manager/openclawserversmanager/users/dto/UserResponse.java`
- **Type:** dto
- **Description:** Java records with Jakarta Validation. `CreateUserRequest` (email, username, password, role), `UpdateUserRequest` (partial — all nullable), `ChangePasswordRequest` (newPassword), `UserResponse` (never exposes passwordHash).
- **Dependencies:** Role enum, Jakarta Validation
- **Date:** 2026-03-11

### UserMapper
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/users/mapper/UserMapper.java`
- **Type:** mapper
- **Description:** Static utility class. `toResponse(User)` converts entity to `UserResponse`.
- **Dependencies:** User entity, UserResponse DTO
- **Date:** 2026-03-11

### PasswordEncoderConfig
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/users/config/PasswordEncoderConfig.java`
- **Type:** config
- **Description:** Defines `PasswordEncoder` bean using BCrypt.
- **Dependencies:** Spring Security
- **Date:** 2026-03-11

### UserService
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/users/service/UserService.java`
- **Type:** service
- **Description:** Business logic for user CRUD. Methods: `createUser` (hash + uniqueness check), `getAllUsers` (paginated), `getUserById`, `updateUser` (partial), `changePassword`, `deleteUser`, `findByEmail`. Uses `@Transactional` on write methods. Throws `DuplicateResourceException`/`ResourceNotFoundException`.
- **Dependencies:** UserRepository, PasswordEncoder, UserMapper, common exceptions
- **Date:** 2026-03-11

### UserController
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/users/controller/UserController.java`
- **Type:** controller
- **Description:** REST controller at `/api/v1/users`. All endpoints ADMIN-only (enforced by SecurityConfig). POST create, GET list (paginated), GET by ID, PATCH update, POST change-password, DELETE. All use `@Valid` and return `ResponseEntity`.
- **Dependencies:** UserService
- **Date:** 2026-03-11

### AdminBootstrapRunner
- **File(s):** `src/main/java/com/openclaw/manager/openclawserversmanager/users/config/AdminBootstrapRunner.java`
- **Type:** config (CommandLineRunner)
- **Description:** On startup, if no users exist and `ADMIN_EMAIL`/`ADMIN_USERNAME`/`ADMIN_PASSWORD` env vars are set, creates an ADMIN user. Skips if users already exist or env vars are missing.
- **Dependencies:** UserRepository, PasswordEncoder
- **Date:** 2026-03-11
