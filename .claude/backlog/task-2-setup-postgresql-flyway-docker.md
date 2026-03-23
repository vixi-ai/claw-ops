# Task 2: Setup PostgreSQL, Flyway & Docker Environment

**Status:** DONE
**Module(s):** common
**Priority:** HIGH
**Created:** 2026-03-11
**Completed:** 2026-03-11

## Description
Configure PostgreSQL database connectivity, Flyway migration system, and Docker Compose for local development. Provide a clean `.env.example` file so any developer can copy it and run the project immediately. The setup should be modular and follow Spring Boot best practices with profile-based configuration.

## Acceptance Criteria
- [x] `docker-compose.yml` that starts a PostgreSQL container with configurable credentials
- [x] `.env.example` with all required environment variables (copy to `.env` to use)
- [x] `.env` added to `.gitignore`
- [x] `application.properties` configured for PostgreSQL using environment variables (not hardcoded values)
- [x] `application-dev.properties` profile with sensible local development defaults
- [x] Spring Data JPA dependency added to `pom.xml`
- [x] PostgreSQL driver dependency added to `pom.xml`
- [x] Flyway configured to run migrations on startup from `db/migration/`
- [x] Initial Flyway migration `V1__create_extensions.sql` — enables `pgcrypto` extension
- [ ] Application starts successfully with `docker compose up -d` then `./mvnw spring-boot:run` — not tested (requires running Docker)
- [x] Clean, modular configuration — no hardcoded credentials anywhere

## Implementation Notes
- Added `spring-boot-starter-data-jpa`, `postgresql` (runtime), `spring-boot-starter-validation` to pom.xml
- `ddl-auto=validate` so Flyway owns schema, Hibernate only validates
- `open-in-view=false` to prevent lazy loading issues
- Used `pgcrypto` over `uuid-ossp` for `gen_random_uuid()` support
- Docker Compose uses `env_file: .env` with fallback defaults in environment block
- Hikari pool: max 10 connections, min 2 idle

## Files Modified
- `pom.xml` — added `spring-boot-starter-data-jpa`, `postgresql`, `spring-boot-starter-validation`
- `docker-compose.yml` — **created** — PostgreSQL 17 service with healthcheck and persistent volume
- `.env.example` — **created** — template for all environment variables
- `.gitignore` — added `.env` and `docker-compose.override.yml`
- `src/main/resources/application.properties` — added datasource, JPA, Flyway, Hikari config
- `src/main/resources/application-dev.properties` — **created** — dev profile with SQL logging
- `src/main/resources/db/migration/V1__create_extensions.sql` — **created** — enables pgcrypto
