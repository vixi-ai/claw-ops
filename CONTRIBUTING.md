# Contributing to ClawOps

Thank you for your interest in contributing to ClawOps! This document provides guidelines and instructions for contributing.

## Reporting Issues

- Use [GitHub Issues](https://github.com/your-org/clawops/issues) to report bugs or request features
- Include steps to reproduce for bug reports
- Include your Java version, OS, and PostgreSQL version

## Development Setup

### Prerequisites

- Java 21+
- PostgreSQL 17+
- Maven 3.9+ (or use the included `./mvnw` wrapper)

### Getting Started

```bash
git clone https://github.com/your-org/clawops.git
cd clawops
cp .env.example .env
# Edit .env with your database credentials and keys

# Start PostgreSQL (via Docker or local install)
docker-compose up -d

# Build
./mvnw clean install

# Run
./mvnw spring-boot:run
```

### Running Tests

```bash
./mvnw test                    # Run all tests
./mvnw test -pl :module-name   # Run tests for a specific module
```

## Branch Naming

Use the following prefixes for branch names:

| Prefix | Usage |
|--------|-------|
| `feature/` | New features — `feature/add-monitoring-dashboard` |
| `fix/` | Bug fixes — `fix/ssl-provisioning-timeout` |
| `docs/` | Documentation changes — `docs/update-api-reference` |
| `refactor/` | Code refactoring — `refactor/rename-base-package` |
| `test/` | Adding or updating tests — `test/server-service-unit-tests` |

## Pull Request Process

1. **Create a branch** from `main` using the naming convention above
2. **Make your changes** following the code style guidelines below
3. **Write tests** for new functionality
4. **Ensure the build passes:** `./mvnw clean install`
5. **Create a PR** with:
   - A clear title describing the change
   - A description of what was changed and why
   - Screenshots for UI changes
   - Reference to any related issues

## Code Style

ClawOps follows conventions defined in `.claude/CLAUDE.md`. Key rules:

### Naming

- Classes: `PascalCase` — `ServerService`, `AuthController`
- Methods/variables: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- REST endpoints: `kebab-case` — `/test-connection`, `/audit-logs`
- DTOs: suffix with `Request` or `Response`

### Architecture

- **Never expose JPA entities in controllers** — always use DTOs
- **Constructor injection only** — no `@Autowired` on fields
- **Return `ResponseEntity<>`** from all controller methods
- **`@Transactional`** on service methods that modify data
- **No business logic in controllers** — controllers validate and delegate

### Validation

- All request DTOs use Jakarta Validation annotations (`@NotBlank`, `@NotNull`, `@Size`, etc.)
- Every controller method uses `@Valid` on request body parameters

### Module Structure

Each module follows this package layout:

```
module/
  controller/    # REST endpoints
  service/       # Business logic
  repository/    # Data access (Spring Data JPA)
  entity/        # JPA entities
  dto/           # Request/Response records
  mapper/        # Entity <-> DTO conversion
  config/        # Module-specific configuration
```

Modules communicate through **services only** — never access another module's repository directly.

## Adding a New Module

1. Create the package structure under `com.openclaw.manager.openclawserversmanager.{module}`
2. Add Flyway migration(s) in `src/main/resources/db/migration/`
3. Create module spec at `.claude/modules/{module}.md`
4. Update `.claude/architecture/{module}.md` after implementation
5. Add security rules in `SecurityConfig.java` if needed
6. Add audit actions in `AuditAction.java`
7. Add a frontend page at `src/main/resources/static/dev/{module}.html`

## License

By contributing to ClawOps, you agree that your contributions will be licensed under the MIT License.
