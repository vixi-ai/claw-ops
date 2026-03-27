# Task Fix: Spring Boot .env File Loading

**Status:** DONE
**Module(s):** common
**Priority:** CRITICAL (blocks local development)
**Created:** 2026-03-11
**Completed:** 2026-03-11

## Description

Spring Boot does not natively load `.env` files. Our project uses `.env` for all configuration (DB credentials, JWT secret, admin bootstrap credentials, etc.), but only Docker Compose reads `.env` automatically. When running `./mvnw spring-boot:run`, none of the `.env` variables are available to the application.

This causes:
- Admin bootstrap runner skips (ADMIN_EMAIL/USERNAME/PASSWORD are blank) → no initial admin user created → can't log in
- Any other env-var-based config (JWT_SECRET, MASTER_ENCRYPTION_KEY) also missing unless manually exported

## Root Cause

- `docker-compose.yml` has `env_file: .env` → PostgreSQL container gets the vars ✓
- `application.properties` uses `${DB_HOST}`, `${JWT_SECRET}`, etc. → Spring resolves these from **system environment variables**, NOT from `.env` files
- `AdminBootstrapRunner` uses `@Value("${ADMIN_EMAIL:}")` → defaults to empty string when env var is missing → skips bootstrap
- Spring Boot has no built-in `.env` file loader

## Solution

Add the `spring-dotenv` library which automatically loads `.env` files into Spring's Environment at startup. This is the cleanest approach — no changes to how we reference variables, just add the dependency.

## Acceptance Criteria

- [ ] Add `me.paulschwarz:spring-dotenv` dependency to `pom.xml`
- [ ] Verify `.env` variables are loaded when running `./mvnw spring-boot:run`
- [ ] Verify admin bootstrap creates the initial admin user on first startup
- [ ] Verify login works with the credentials from `.env`
- [ ] Update `application.properties` to use `env.` prefix if required by the library
- [ ] Verify Docker Compose still works (no conflict with the library)

## Implementation Notes

### Option A: spring-dotenv (Recommended)

Add to `pom.xml`:
```xml
<dependency>
    <groupId>me.paulschwarz</groupId>
    <artifactId>spring-dotenv</artifactId>
    <version>4.0.0</version>
</dependency>
```

This library loads `.env` from the project root into Spring's `Environment`. Variables are accessed with the `env.` prefix in `application.properties`:

```properties
# Before (doesn't work without exported env vars):
spring.datasource.url=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}

# After (reads from .env file automatically):
spring.datasource.url=jdbc:postgresql://${env.DB_HOST}:${env.DB_PORT}/${env.DB_NAME}
```

The `@Value` annotations in Java code also need the prefix:
```java
// Before:
@Value("${ADMIN_EMAIL:}") String adminEmail

// After:
@Value("${env.ADMIN_EMAIL:}") String adminEmail
```

**Affected files that reference env vars:**
- `src/main/resources/application.properties` — all `${VAR}` references need `${env.VAR}`
- `src/main/java/.../users/config/AdminBootstrapRunner.java` — `@Value` annotations
- `src/main/java/.../auth/config/JwtConfig.java` — `@Value` annotations for JWT_SECRET, expiration times
- Any other `@Value` annotations referencing env vars

### Option B: Custom EnvironmentPostProcessor (Alternative)

If we don't want a third-party dependency, we can write a custom `EnvironmentPostProcessor` that reads `.env` manually. More code, but zero external dependencies.

### Option C: ProcessBuilder / Shell wrapper (Simplest but least elegant)

Just document that developers must run:
```bash
export $(cat .env | xargs) && ./mvnw spring-boot:run
```

Not recommended — easy to forget, doesn't work on Windows without WSL.

### Recommendation

Go with **Option A** (`spring-dotenv`). It's a well-maintained library, zero config beyond the dependency, and works seamlessly with our existing `.env` setup. The only change is prefixing env var references with `env.` in properties and `@Value` annotations.

### Verification Steps

After implementation:
1. Delete all users from DB (or use fresh DB): `docker compose down -v && docker compose up -d`
2. Run `./mvnw spring-boot:run`
3. Check logs for: `Bootstrap admin user created: admin@openclaw.dev`
4. Go to `http://localhost:8080/dev/login.html`
5. Login with `admin@openclaw.dev` / `changeme`
6. Should redirect to dashboard successfully

## Files Modified

- `src/main/java/.../common/config/DotenvEnvironmentPostProcessor.java` — custom EnvironmentPostProcessor that reads `.env` into Spring's property sources (NEW)
- `src/main/resources/META-INF/spring.factories` — registers DotenvEnvironmentPostProcessor (NEW)
- `pom.xml` — spring-dotenv removed (incompatible with Spring Boot 4.x)
- `src/main/resources/application.properties` — reverted to plain `${VAR:default}` syntax (no `env.` prefix needed)
- `src/main/java/.../users/config/AdminBootstrapRunner.java` — reverted to plain `${VAR:}` @Value syntax
- `src/main/java/.../auth/config/JwtConfig.java` — reverted to plain `${VAR:}` @Value syntax

**Note:** Initially tried Option A (spring-dotenv 4.0.0) but it was incompatible with Spring Boot 4.0.3. Switched to Option B (custom EnvironmentPostProcessor) which works with no external dependencies and no prefix changes.
