# Common Module — Architecture Log

## OpenApiConfig

- **File:** `src/main/java/com/openclaw/manager/openclawserversmanager/common/config/OpenApiConfig.java`
- **Type:** Configuration
- **What it does:** Configures springdoc-openapi with API metadata (title, version, description, contact, license) and declares a JWT Bearer `@SecurityScheme` so Swagger UI displays an "Authorize" button for testing authenticated endpoints.
- **Dependencies:** `springdoc-openapi-starter-webmvc-ui` (3.0.2)
- **Properties added:** `springdoc.api-docs.path`, `springdoc.swagger-ui.path`, `springdoc.swagger-ui.tags-sorter`, `springdoc.swagger-ui.operations-sorter` in `application.properties`
- **Date:** 2026-03-11

## PostgreSQL / Flyway / Docker Setup

- **Files:**
  - `docker-compose.yml` — PostgreSQL 17 container with healthcheck, env-based config, persistent volume
  - `.env.example` — Template for all environment variables (DB, app, encryption, JWT)
  - `src/main/resources/application.properties` — Datasource, Hikari pool, JPA (validate mode), Flyway config using `${ENV_VAR:default}` syntax
  - `src/main/resources/application-dev.properties` — Dev profile: SQL logging, formatted output, debug logging
  - `src/main/resources/db/migration/V1__create_extensions.sql` — Enables `pgcrypto` extension
- **Type:** Infrastructure / Configuration
- **What it does:** Provides the full database stack — Docker Compose for local PostgreSQL, Spring Data JPA for ORM, Flyway for schema migrations, Hikari for connection pooling. All credentials come from environment variables with sensible defaults.
- **Dependencies added to pom.xml:** `spring-boot-starter-data-jpa`, `postgresql` (runtime), `spring-boot-starter-validation`
- **Key decisions:**
  - `ddl-auto=validate` — Flyway owns the schema, Hibernate only validates
  - `open-in-view=false` — prevents lazy loading issues in controllers
  - `pgcrypto` extension chosen over `uuid-ossp` (more versatile, supports `gen_random_uuid()`)
- **Date:** 2026-03-11

## Custom Exceptions + Global Error Handler

- **Files:**
  - `src/main/java/com/openclaw/manager/openclawserversmanager/common/exception/ResourceNotFoundException.java` — 404
  - `src/main/java/com/openclaw/manager/openclawserversmanager/common/exception/DuplicateResourceException.java` — 409
  - `src/main/java/com/openclaw/manager/openclawserversmanager/common/exception/AccessDeniedException.java` — 403
  - `src/main/java/com/openclaw/manager/openclawserversmanager/common/dto/ErrorResponse.java` — error response DTO (record)
  - `src/main/java/com/openclaw/manager/openclawserversmanager/common/exception/GlobalExceptionHandler.java` — `@RestControllerAdvice`
- **Type:** Exception / DTO / Config
- **What it does:** Provides consistent error responses across all modules. `ErrorResponse` supports both single-message errors and validation field-level errors. `GlobalExceptionHandler` maps exceptions to HTTP status codes with the standard `{status, error, message, timestamp}` format. Also handles `MethodArgumentNotValidException` for `@Valid` failures.
- **Dependencies:** None (used by all modules)
- **Date:** 2026-03-11

## DotenvEnvironmentPostProcessor (.env File Loading)

- **Files:**
  - `src/main/java/.../common/config/DotenvEnvironmentPostProcessor.java` — custom `EnvironmentPostProcessor`
  - `src/main/resources/META-INF/spring.factories` — registers the post-processor
- **Type:** Configuration / Infrastructure
- **What it does:** Loads `.env` file from the working directory into Spring's `Environment` at startup, so `${VAR}` placeholders in `application.properties` and `@Value` annotations resolve correctly without exporting shell variables. Parses key=value lines, ignores comments and blanks, strips surrounding quotes. Added with lowest precedence so real env vars and system properties always win.
- **Key decisions:** Custom `EnvironmentPostProcessor` chosen over `spring-dotenv` library (incompatible with Spring Boot 4.x) and over shell wrapper (cross-platform, can't forget). No `env.` prefix needed — existing `${VAR}` references work as-is.
- **Date:** 2026-03-11

## StrongPassword Validator (Password Policy)

- **Files:**
  - `src/main/java/com/openclaw/manager/openclawserversmanager/common/validation/StrongPassword.java` — custom Jakarta Validation annotation
  - `src/main/java/com/openclaw/manager/openclawserversmanager/common/validation/StrongPasswordValidator.java` — `ConstraintValidator` implementation
- **Type:** Validation
- **What it does:** Enforces password complexity: min 8 chars, max 128, at least 1 uppercase, 1 lowercase, 1 digit, 1 special character. Each failed rule produces a specific error message (not generic). Applied to `CreateUserRequest.password` and `ChangePasswordRequest.newPassword`. Returns `true` for null/blank (defers to `@NotBlank`).
- **Dependencies:** Jakarta Validation API
- **Date:** 2026-03-24

## RateLimitExceededException

- **File:** `src/main/java/com/openclaw/manager/openclawserversmanager/common/exception/RateLimitExceededException.java`
- **Type:** Exception
- **What it does:** Custom exception for HTTP 429 Too Many Requests. Thrown when IP-based login rate limit is exceeded. Handled by GlobalExceptionHandler returning `{"status": 429, "error": "Too Many Requests", "message": "..."}`.
- **Dependencies:** GlobalExceptionHandler
- **Date:** 2026-03-24

## Production Hardening (.gitignore & SSH Host Key Checking)

- **Files:**
  - `.gitignore` — added certificate/key patterns (`*.pem`, `*.key`, `*.crt`, `*.jks`, `*.p12`, `*.pfx`, `*.keystore`, `known_hosts`) and log patterns (`*.log`, `logs/`)
  - `src/main/resources/application.properties` — changed `ssh.strict-host-key-checking` from hardcoded `false` to `${SSH_STRICT_HOST_KEY_CHECKING:true}` (secure by default)
  - `.env.example` — added `SSH_STRICT_HOST_KEY_CHECKING=false` for dev override
- **Type:** Configuration / Infrastructure
- **What it does:** Hardens the project for production: prevents accidental commit of certificate/key/log files, defaults SSH to strict host key checking (prevents MITM), makes the setting overridable for dev environments.
- **Key decisions:** Default `true` for SSH host key checking (fail-safe) — dev environments opt out by setting `false` in `.env`.
- **Date:** 2026-03-24

## Dev Admin Pages (Static HTML)

- **Files:** `src/main/resources/static/dev/` — `index.html`, `login.html`, `users.html`, `common.js`, `common.css`, plus 8 placeholder pages (servers, secrets, ssh, deployments, templates, domains, audit, terminal)
- **Type:** Static resources (dev tooling)
- **What it does:** Self-contained HTML/JS/CSS pages for managing the system during development. `common.js` provides JWT-based API client with auto-refresh. `login.html` authenticates and stores tokens in localStorage. `users.html` has full CRUD (table, create/edit modals, change password, enable/disable, delete with confirmation, pagination). Dark theme UI.
- **Dependencies:** Auth + Users REST API endpoints. SecurityConfig updated to `permitAll()` for `/dev/**`.
- **Date:** 2026-03-11
