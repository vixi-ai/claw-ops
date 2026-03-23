# Configuration

ClawOps is configured through environment variables and Spring application properties. Environment variables take precedence.

## Environment Variables

### Required

| Variable | Description |
|----------|-------------|
| `MASTER_ENCRYPTION_KEY` | 32-byte Base64 key for AES-256-GCM encryption of secrets. Generate with: `openssl rand -base64 32` |
| `DB_PASSWORD` | PostgreSQL database password |
| `JWT_SECRET` | Secret key for signing JWT tokens. Generate with: `openssl rand -base64 64` |

### Database

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `openclaw` | Database name |
| `DB_USERNAME` | `openclaw` | Database username |
| `DB_PASSWORD` | `changeme` | Database password |

### Application

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | HTTP port the application listens on |
| `SPRING_PROFILES_ACTIVE` | — | Active Spring profiles (e.g., `dev` for SQL logging) |

### Authentication

| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_SECRET` | — | Secret for signing JWT tokens |
| `JWT_ACCESS_TOKEN_EXPIRATION` | `900000` (15 min) | Access token lifetime in milliseconds |
| `JWT_REFRESH_TOKEN_EXPIRATION` | `604800000` (7 days) | Refresh token lifetime in milliseconds |

### Admin Bootstrap

These variables are only used on first startup when no users exist in the database:

| Variable | Default | Description |
|----------|---------|-------------|
| `ADMIN_EMAIL` | `admin@openclaw.dev` | Bootstrap admin email |
| `ADMIN_USERNAME` | `admin` | Bootstrap admin username |
| `ADMIN_PASSWORD` | — | Bootstrap admin password |

### SSL Provisioning

| Variable | Default | Description |
|----------|---------|-------------|
| `SSL_ADMIN_EMAIL` | `admin@openclaw.com` | Email passed to certbot for Let's Encrypt registration |
| `SSL_TARGET_PORT` | `3000` | Local port that nginx proxies to on managed servers |

### Terminal

| Variable | Default | Description |
|----------|---------|-------------|
| `TERMINAL_SESSION_TIMEOUT` | `30` | Terminal session timeout in minutes |
| `TERMINAL_MAX_SESSIONS` | `5` | Maximum concurrent terminal sessions per user |
| `TERMINAL_TOKEN_EXPIRY` | `60` | WebSocket session token validity in seconds |

## Application Properties

These are configured in `src/main/resources/application.properties` and generally don't need to be changed:

### Database Connection Pool (HikariCP)

```properties
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.idle-timeout=30000
spring.datasource.hikari.connection-timeout=20000
```

### JPA / Hibernate

```properties
spring.jpa.hibernate.ddl-auto=validate     # Flyway handles schema
spring.jpa.open-in-view=false              # No lazy loading in views
spring.jpa.show-sql=false                  # Enable in dev profile
```

### SSH

```properties
ssh.connection-timeout=10000     # Connection timeout (ms)
ssh.command-timeout=60           # Default command timeout (seconds)
ssh.strict-host-key-checking=false
ssh.max-output-size=1048576      # Max command output (1MB)
```

### Flyway

```properties
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=false
```

## Dev Profile

Activate with `SPRING_PROFILES_ACTIVE=dev` or `-Dspring-boot.run.profiles=dev`:

```properties
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
logging.level.org.springframework.web=DEBUG
```

## .env.example

A template is provided at the repo root:

```env
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=openclaw
DB_USERNAME=openclaw
DB_PASSWORD=changeme

# Application
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=dev

# Encryption (generate with: openssl rand -base64 32)
MASTER_ENCRYPTION_KEY=

# JWT
JWT_SECRET=
JWT_ACCESS_TOKEN_EXPIRATION=900000
JWT_REFRESH_TOKEN_EXPIRATION=604800000

# Admin bootstrap (creates initial admin user if no users exist)
ADMIN_EMAIL=admin@openclaw.dev
ADMIN_USERNAME=admin
ADMIN_PASSWORD=changeme
```
