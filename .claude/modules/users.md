# Users Module

## Purpose

Manages DevOps user accounts. Handles user creation, retrieval, updates, and disabling. Only admins can create or manage users — there is no public registration.

## Package

`com.openclaw.manager.openclawserversmanager.users`

## Components

### Entity: `User`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| email | String | UNIQUE, NOT NULL |
| username | String | UNIQUE, NOT NULL |
| passwordHash | String | NOT NULL |
| role | Role (enum) | NOT NULL, default DEVOPS |
| enabled | boolean | NOT NULL, default true |
| createdAt | Instant | auto-set |
| updatedAt | Instant | auto-set on update |

### Enum: `Role`

- `ADMIN` — full system access, can manage users
- `DEVOPS` — operational access (servers, deployments, terminal)

### DTOs

**`CreateUserRequest`**
- `email` — `@NotBlank @Email`
- `username` — `@NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[a-zA-Z0-9_-]+$")`
- `password` — `@NotBlank @Size(min = 8, max = 128)`
- `role` — `@NotNull` (ADMIN or DEVOPS)

**`UpdateUserRequest`**
- `email` — `@Email` (optional)
- `username` — `@Size(min = 3, max = 50)` (optional)
- `role` — (optional)

**`UserResponse`**
- `id`, `email`, `username`, `role`, `enabled`, `createdAt`, `updatedAt`
- **Never includes passwordHash**

### Service: `UserService`

- `createUser(CreateUserRequest)` — hashes password, saves user
- `getAllUsers(Pageable)` — paginated list
- `getUserById(UUID)` — single user lookup
- `updateUser(UUID, UpdateUserRequest)` — partial update
- `disableUser(UUID)` — soft disable (sets `enabled = false`)
- `findByEmail(String)` — used by auth module for login

### Repository: `UserRepository`

- `findByEmail(String email)` → `Optional<User>`
- `findByUsername(String username)` → `Optional<User>`
- `existsByEmail(String email)` → `boolean`
- `existsByUsername(String username)` → `boolean`

### Mapper: `UserMapper`

- `toResponse(User)` → `UserResponse`
- `toEntity(CreateUserRequest)` → `User` (password must be hashed before mapping)

## API Endpoints

| Method | Path | Auth | Role | Description |
|--------|------|------|------|-------------|
| POST | `/api/v1/users` | Yes | ADMIN | Create new user |
| GET | `/api/v1/users` | Yes | ADMIN | List all users (paginated) |
| GET | `/api/v1/users/{id}` | Yes | ADMIN | Get user by ID |
| PATCH | `/api/v1/users/{id}` | Yes | ADMIN | Update user |
| POST | `/api/v1/users/{id}/disable` | Yes | ADMIN | Disable user account |

## Business Rules

- Email and username must be unique (check before creation, throw `DuplicateResourceException`)
- Passwords are hashed with BCrypt or Argon2 before storage — never stored as plaintext
- Password retrieval / reset is not supported (admin creates new password if needed)
- Users cannot delete themselves
- Disabling a user should also revoke their active refresh tokens
- Only ADMIN role can access user management endpoints

## Security Considerations

- `passwordHash` must never appear in any API response or log output
- User enumeration should be avoided — login errors should not reveal whether an email exists
- All user management endpoints restricted to ADMIN role

## Dependencies

- **auth** — auth module calls `findByEmail` for login
- **audit** — log user creation, updates, disable events
