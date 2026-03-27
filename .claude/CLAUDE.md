# OpenClaw Servers Manager — Control Plane Backend

Backend infrastructure orchestration platform for managing OpenClaw-based agent infrastructures.
Acts as a control plane that coordinates server inventory, secure secrets, SSH access, deployments, domain provisioning, and SSL automation.

## Tech Stack

- **Java 21** / **Spring Boot 4.0.3** / **Maven**
- **PostgreSQL** + **Flyway** migrations
- **Spring Security** + JWT (access + refresh tokens)
- **Spring Data JPA** (Hibernate)
- **Spring WebSocket** (terminal gateway)
- **springdoc-openapi 3.0.2** (Swagger UI)
- **sshj** or **Apache Mina SSHD** (SSH operations)
- **Docker** + **Docker Compose** (deployment)

## Project Coordinates

- Group: `com.openclaw.manager`
- Artifact: `openclaw-servers-manager`
- Base package: `com.openclaw.manager.openclawserversmanager`
- **NOTE:** Base package should be renamed to `com.openclaw.controlplane` for clarity. When creating new code, prefer the renamed package if the refactor has been done; otherwise use the existing one.

## Build & Run

```bash
./mvnw clean install                    # Build + run tests
./mvnw spring-boot:run                  # Run locally
./mvnw test                             # Run tests only
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # Run with dev profile
```

Database: PostgreSQL must be running. Migrations are handled automatically by Flyway on startup.
Migration files go in: `src/main/resources/db/migration/` using naming `V{number}__{description}.sql`

## Architecture

This is an **infrastructure control plane** — it orchestrates operations on remote servers via SSH scripts and configuration templates. It does NOT embed infrastructure logic directly in Java services.

**Pattern:** Java backend orchestrates → uploads script → executes via SSH → tracks logs/status

### Module Structure

The project is organized into these modules. Each has its own instruction file at `.claude/modules/{name}.md`:

| Module | Package suffix | Purpose |
|--------|---------------|---------|
| auth | `.auth` | JWT authentication, login, refresh, logout |
| users | `.users` | DevOps user management (ADMIN, DEVOPS roles) |
| servers | `.servers` | Server inventory and connection registry |
| secrets | `.secrets` | AES-GCM encrypted credential storage |
| ssh | `.ssh` | SSH connections, command execution, file transfer |
| terminal | `.terminal` | WebSocket-based browser terminal |
| deployment | `.deployment` | Bash script library — store named bash scripts in DB and execute them on servers |
| templates | `.templates` | Agent template provisioning — bash scripts that install agent directories with MD config files for agents and their skills |
| domains | `.domains` | DNS record + SSL certificate automation |
| audit | `.audit` | Operation audit logging |
| common | `.common` | Shared utilities, exceptions, base classes |

Each module contains: `controller/`, `service/`, `repository/`, `entity/`, `dto/`, `mapper/`, `config/`

Modules communicate through **services only** — never access another module's repository directly.

## Coding Rules

### Naming Conventions

- Classes: `PascalCase` — `ServerService`, `AuthController`
- Methods/variables: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- Packages: `lowercase`, no underscores
- REST endpoints: `kebab-case` — `/test-connection`, `/audit-logs`
- DTOs: suffix with `Request` or `Response` — `CreateServerRequest`, `ServerResponse`
- Entities: singular, no suffix — `Server`, `User`, `AuditLog`
- Mappers: `{Entity}Mapper` — `ServerMapper`, `UserMapper`
- Exceptions: `{Name}Exception` — `ResourceNotFoundException`, `EncryptionException`

### Code Structure

- **Never expose JPA entities in controllers** — always use DTOs for input and output
- **Every controller method** must use `@Valid` on request body parameters
- **Constructor injection only** — no `@Autowired` on fields
- **Return `ResponseEntity<>`** from all controller methods
- **`@Transactional`** on service methods that modify data
- **Repository returns:** use `Optional`, handle with `.orElseThrow(() -> new ResourceNotFoundException(...))`
- **Spring Data query derivation** preferred — avoid `@Query` native SQL unless necessary
- **Mappers:** static methods in a dedicated `{Entity}Mapper` class per module (no MapStruct unless requested)
- **Prefer composition over inheritance**
- **No business logic in controllers** — controllers validate input and delegate to services

### Validation Rules

All request DTOs must use **Jakarta Validation** annotations:

- `@NotBlank` for required strings
- `@NotNull` for required non-string fields
- `@Size(min, max)` for length constraints
- `@Email` for email fields
- `@Pattern(regexp)` for format constraints (hostnames, IPs, etc.)
- `@Min` / `@Max` for numeric ranges

Custom validators for domain-specific rules (valid hostname, IP address format, SSH port range).

**Validation error response format:**
```json
{
  "status": 400,
  "error": "Validation Failed",
  "messages": [
    { "field": "email", "message": "must be a valid email address" },
    { "field": "hostname", "message": "must not be blank" }
  ],
  "timestamp": "2024-01-01T00:00:00Z"
}
```

Never trust client input — validate at the controller boundary.

## Documentation Rules (Mandatory)

These rules are **non-negotiable**. Every agent must follow them after writing or modifying code.

### Architecture Log

After writing or significantly modifying code for any module, you **MUST** append an entry to `.claude/architecture/{module}.md` with:
- Component name and file path(s)
- Component type (controller, service, entity, dto, mapper, config, etc.)
- What it does and key design decisions made
- Dependencies on other components/modules
- Date of implementation (YYYY-MM-DD)

**Before writing code** for a module, READ `.claude/architecture/{module}.md` first to understand what already exists and avoid duplicating work.

### Backlog Tasks

Tasks are individual files in `.claude/backlog/`, one file per task, named: `task-{number}-{short-description}.md`

**File naming:** `task-1-setup-openapi-swagger.md`, `task-2-create-user-entity.md`, etc.

**Task lifecycle:**
1. **Creating a task:** When the user describes work to be done, create a new task file in `.claude/backlog/` with the next available number. Include all context needed to execute it later.
2. **User validates:** The user reviews the task file and confirms it's correct before execution.
3. **Executing a task:** When the user says "do backlog task task-5-make-new-feature.md", read that task file, then read the relevant module instruction and architecture files, then execute.
4. **Completing a task:** After finishing, update the task file — set status to `DONE`, add completion date, and list what was implemented.

**Task file template:**
```markdown
# Task {number}: {Title}

**Status:** TODO | IN_PROGRESS | DONE
**Module(s):** {module names}
**Priority:** HIGH | MEDIUM | LOW
**Created:** YYYY-MM-DD
**Completed:** —

## Description
What needs to be done and why.

## Acceptance Criteria
- [ ] Criterion 1
- [ ] Criterion 2

## Implementation Notes
Any technical context, approach hints, or constraints.

## Files Modified
<!-- Filled in after completion -->
```

### Cross-Reference Workflow

When working on any module, always consult these files in this order:
1. `.claude/modules/{module}.md` — **specs & rules** (how to build it)
2. `.claude/architecture/{module}.md` — **what's already built** (avoid conflicts/duplication)
3. `.claude/backlog/task-*.md` — **relevant task file** (what needs to be done)

## Security Rules

| Data Type | Storage Method |
|-----------|---------------|
| User passwords | BCrypt or Argon2 hashing |
| SSH passwords | AES-GCM encryption |
| SSH private keys | AES-GCM encryption |
| API keys | AES-GCM encryption |
| DNS provider tokens | AES-GCM encryption |

- Master encryption key via `MASTER_ENCRYPTION_KEY` environment variable — **never in source code**
- JWT for authentication: short-lived access tokens + long-lived refresh tokens
- **No public signup** — users created via admin API or bootstrap script only
- Roles: `ADMIN`, `DEVOPS`
- All endpoints secured by default — explicit `permitAll()` only for `/api/v1/auth/**`
- Sensitive data (keys, passwords, tokens) must never appear in logs or API responses

## Error Handling

Global `@RestControllerAdvice` exception handler with consistent response format:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Server with id 123 not found",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

**Custom exception hierarchy** (in `common` module):
- `ResourceNotFoundException` — 404
- `DuplicateResourceException` — 409
- `AccessDeniedException` — 403
- `EncryptionException` — 500
- `SshConnectionException` — 502
- `DeploymentException` — 500
- `ValidationException` — 400

Never leak stack traces or internal implementation details in error responses.

## Testing Conventions

- **Unit tests:** service layer with mocked repositories (`@ExtendWith(MockitoExtension.class)`)
- **Integration tests:** `@SpringBootTest` with Testcontainers for PostgreSQL
- **Controller tests:** `@WebMvcTest` with `MockMvc`
- **Test naming:** `should_expectedBehavior_when_condition()` — e.g., `should_returnServer_when_validIdProvided()`
- Test structure mirrors `src/main/java` package layout

## API Conventions

- Base path: `/api/v1/`
- RESTful: plural nouns for resources (`/servers`, `/users`, `/templates`)
- HTTP methods: `GET` (read), `POST` (create/action), `PATCH` (partial update), `DELETE` (remove)
- Pagination: `page`, `size`, `sort` query params via Spring Data `Pageable`
- IDs: UUID preferred
- Full endpoint specs in each module's `.claude/modules/{name}.md` file

## Development Phases

**Current: Phase 1 — Not Started**

1. **Core Infrastructure** — auth, user management, server CRUD, encrypted secrets, SSH connection testing
2. **Operations** — remote command execution, deployment jobs, script runner, audit logging
3. **Interactive Features** — WebSocket terminal, session tracking, live logs
4. **Infrastructure Automation** — domain provisioning, SSL automation, reverse proxy config
5. **Advanced Features** — fine-grained RBAC, secret versioning, multi-team support, monitoring

## Project Vision

Open-source DevOps platform optimized for AI agent infrastructures. Single interface to manage servers, agent deployments, secrets, terminal access, domains, SSL, and infrastructure operations across the OpenClaw ecosystem.
