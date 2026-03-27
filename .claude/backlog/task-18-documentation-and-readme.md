# Task 18: Documentation + Professional README for ClawOps

**Status:** DONE
**Module(s):** docs, root
**Priority:** HIGH
**Created:** 2026-03-23
**Completed:** 2026-03-23

## Description

Create a professional open-source documentation suite for **ClawOps** — the project's public-facing identity. This includes a polished `README.md` at the root with the logo, a `/docs` folder with full technical and user-facing documentation, and a `CONTRIBUTING.md` for open-source contributors.

The project is being renamed/branded as **ClawOps** (from OpenClaw Servers Manager internally). The README and docs should use the ClawOps brand name consistently.

## Acceptance Criteria

- [ ] `README.md` at repo root — professional, logo included, badges, full feature overview
- [ ] `docs/` folder created with the following files:
  - [ ] `docs/getting-started.md` — install, configure, first run
  - [ ] `docs/architecture.md` — system design, module overview, data flow
  - [ ] `docs/api-reference.md` — all REST endpoints with request/response examples
  - [ ] `docs/modules/servers.md` — server management guide
  - [ ] `docs/modules/secrets.md` — secrets and credential management
  - [ ] `docs/modules/domains.md` — DNS provider setup, domain import, subdomain assignment
  - [ ] `docs/modules/ssl.md` — SSL provisioning, Cloudflare notes, retry flow
  - [ ] `docs/modules/deployment.md` — script library, running jobs, log polling
  - [ ] `docs/modules/templates.md` — agent templates, install scripts, directory structure
  - [ ] `docs/modules/terminal.md` — WebSocket terminal usage
  - [ ] `docs/modules/audit.md` — audit log overview
  - [ ] `docs/configuration.md` — all environment variables and application.properties keys
  - [ ] `docs/security.md` — auth model, roles, encryption, JWT details
- [ ] `CONTRIBUTING.md` — how to contribute, branch naming, PR process
- [ ] `logo.png` referenced correctly in README (already at repo root)

## Implementation Notes

### README.md structure

```markdown
<p align="center">
  <img src="logo.png" alt="ClawOps" width="280">
</p>

<h1 align="center">ClawOps</h1>

<p align="center">
  Open-source DevOps control plane for managing AI agent infrastructure.
  <br>
  Servers · SSH · Secrets · Domains · SSL · Deployments · Agent Templates
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-4.0.3-green" alt="Spring Boot">
  <img src="https://img.shields.io/badge/PostgreSQL-16-blue" alt="PostgreSQL">
  <img src="https://img.shields.io/badge/license-MIT-brightgreen" alt="MIT License">
</p>
```

Sections (in order):
1. **Logo + title + badges** (Java 21, Spring Boot 4, PostgreSQL, MIT license)
2. **What is ClawOps** — 2-3 paragraph description of the project vision
3. **Features** — bulleted list with emoji icons for each capability
4. **Screenshots / UI overview** — short description of each page (no actual screenshots needed — use a table or text)
5. **Tech Stack** — table: Java 21, Spring Boot 4.0.3, PostgreSQL + Flyway, Spring Security + JWT, sshj, springdoc-openapi
6. **Quick Start** — Docker Compose single-command setup
7. **Configuration** — table of key environment variables with defaults
8. **API Documentation** — mention Swagger UI at `/swagger-ui.html`
9. **Documentation** — link to `/docs` folder sections
10. **Contributing** — link to `CONTRIBUTING.md`
11. **License** — MIT

### What is ClawOps (description to write)

ClawOps is an open-source infrastructure control plane built for teams running AI agent workloads. It gives you a single web interface to:
- Register and manage remote servers with SSH access
- Store encrypted credentials and API keys (AES-GCM)
- Automatically provision subdomains and SSL certificates for every server
- Write and execute bash deployment scripts on any server
- Install OpenClaw agent directory structures from templates
- Access a live browser terminal to any managed server
- Track all operations with a full audit log

It follows the pattern: **Java backend orchestrates → uploads script → executes via SSH → tracks status**. No agents required on the remote servers — only SSH access.

### Features list (for README)

- **Server Registry** — register servers by hostname/IP, SSH key or password auth, environment tags
- **Encrypted Secrets** — AES-GCM encrypted storage for SSH keys, passwords, API tokens
- **DNS Automation** — Cloudflare and Namecheap provider support, auto-import all domains from an account, auto-create subdomains on server registration
- **SSL Automation** — automatic Let's Encrypt certificates via certbot on every managed server
- **Script Library** — write and store bash scripts in the database, run them on any server with one click, live log streaming
- **Agent Templates** — bash scripts that install OpenClaw agent directory structures with markdown config files for agents and skills
- **WebSocket Terminal** — live browser-based SSH terminal to any managed server
- **Audit Log** — every operation logged with user, timestamp, and detail
- **JWT Auth** — short-lived access tokens + refresh tokens, ADMIN and DEVOPS roles
- **Swagger UI** — full OpenAPI docs at `/swagger-ui.html`

### Tech Stack table

| Layer | Technology |
|-------|-----------|
| Language | Java 21 |
| Framework | Spring Boot 4.0.3 |
| Database | PostgreSQL 16 + Flyway migrations |
| Auth | Spring Security + JWT (access + refresh tokens) |
| SSH | sshj 0.39 |
| WebSocket | Spring WebSocket |
| API Docs | springdoc-openapi 3 (Swagger UI) |
| Frontend | Vanilla HTML/CSS/JS (no framework) |

### Quick Start section

```bash
git clone https://github.com/your-org/clawops.git
cd clawops
cp .env.example .env           # fill in MASTER_ENCRYPTION_KEY etc.
docker-compose up -d           # starts PostgreSQL + app
open http://localhost:8080
```

Default admin credentials printed to console on first start (via `AdminBootstrapRunner`).

### Environment Variables table (for README and docs/configuration.md)

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `MASTER_ENCRYPTION_KEY` | Yes | — | 32-byte Base64 key for AES-GCM encryption of secrets |
| `DB_URL` | Yes | — | PostgreSQL JDBC URL (e.g. `jdbc:postgresql://localhost:5432/clawops`) |
| `DB_USERNAME` | Yes | — | PostgreSQL username |
| `DB_PASSWORD` | Yes | — | PostgreSQL password |
| `JWT_SECRET` | Yes | — | Secret for signing JWT tokens |
| `JWT_ACCESS_EXPIRY_MS` | No | `900000` (15m) | Access token lifetime in milliseconds |
| `JWT_REFRESH_EXPIRY_MS` | No | `604800000` (7d) | Refresh token lifetime in milliseconds |
| `SSL_ADMIN_EMAIL` | No | — | Email passed to certbot for Let's Encrypt registration |
| `SSL_TARGET_PORT` | No | `3000` | Local port nginx proxies to on managed servers |
| `ADMIN_USERNAME` | No | `admin` | Bootstrap admin username (only used if no users exist) |
| `ADMIN_PASSWORD` | No | — | Bootstrap admin password |

### docs/architecture.md content outline

- **Overview diagram** (text-based ASCII or description): Browser → Spring Boot API → SSH → Remote Servers
- **Module table**: all 11 modules with package, purpose, key classes
- **Data flow examples**:
  - "Add server" flow: REST → ServerService → DomainAssignmentService → SslService → SSH
  - "Run script" flow: REST → DeploymentJobService → ScriptRunner (@Async) → SSH → logs in DB
  - "Deploy template" flow: REST → TemplateService → ScriptRunner → SSH
- **Database schema summary**: list all tables with key columns
- **Security model**: JWT flow, role hierarchy, AES-GCM key derivation
- **Async execution**: how @Async + ThreadPoolTaskExecutor works for deployment jobs

### docs/api-reference.md content outline

Group endpoints by module, each with:
- Method + path
- Auth requirement (role)
- Request body fields
- Response fields
- Example curl command

Modules to cover: auth, users, servers, secrets, ssh, terminal, domains (provider-accounts, managed-zones, domain-assignments), ssl-certificates, deployment-scripts, deployment-jobs, agent-templates, audit-logs

### docs/getting-started.md content outline

1. Prerequisites (Java 21, PostgreSQL 16, or Docker)
2. Clone + build: `./mvnw clean install`
3. Environment setup: copy `.env.example`, fill in values
4. Run: `./mvnw spring-boot:run` OR `docker-compose up`
5. First login: admin credentials from console
6. Add first server: walkthrough
7. Add DNS provider: Cloudflare vs Namecheap instructions
8. Provision SSL: what to check if it fails (port 80, Cloudflare "Always Use HTTPS")

### docs/security.md content outline

- **Authentication**: JWT access + refresh token flow, token storage advice
- **Roles**: ADMIN (full access) vs DEVOPS (no user management, no secret deletion)
- **Secret encryption**: AES-GCM 256-bit, MASTER_ENCRYPTION_KEY env var, per-secret IV
- **SSH key handling**: private keys never returned in API responses
- **No public signup**: all users created by admin
- **Audit trail**: every mutation logged

### CONTRIBUTING.md structure

- Welcome message
- How to report issues (GitHub Issues)
- Development setup (same as getting-started)
- Branch naming: `feature/`, `fix/`, `docs/`
- PR process: description, screenshots for UI changes, tests required
- Code style: follows project CLAUDE.md conventions
- License agreement: MIT

## New Files

| File | Description |
|------|-------------|
| `README.md` | Root README — logo, features, quick start, config, links |
| `CONTRIBUTING.md` | Contributor guide |
| `docs/getting-started.md` | Installation and first-run walkthrough |
| `docs/architecture.md` | System design, module map, data flows, DB schema |
| `docs/api-reference.md` | All REST endpoints with examples |
| `docs/configuration.md` | All env vars and application.properties keys |
| `docs/security.md` | Auth, roles, encryption, JWT details |
| `docs/modules/servers.md` | Server management module guide |
| `docs/modules/secrets.md` | Secrets and credential management guide |
| `docs/modules/domains.md` | DNS providers, domain import, subdomains |
| `docs/modules/ssl.md` | SSL provisioning, troubleshooting |
| `docs/modules/deployment.md` | Script library, jobs, log polling |
| `docs/modules/templates.md` | Agent templates and install scripts |
| `docs/modules/terminal.md` | WebSocket terminal guide |
| `docs/modules/audit.md` | Audit log reference |

## Files Created

| File | Description |
|------|-------------|
| `README.md` | Professional README with logo, badges, features, quick start, config table |
| `CONTRIBUTING.md` | Contributor guide with code style, branch naming, PR process |
| `docs/getting-started.md` | Install, configure, first-run walkthrough |
| `docs/architecture.md` | System design, module map, data flows, DB schema |
| `docs/api-reference.md` | All 51 REST endpoints with curl examples |
| `docs/configuration.md` | All env vars and application properties |
| `docs/security.md` | Auth model, roles, encryption, JWT details |
| `docs/modules/servers.md` | Server management guide |
| `docs/modules/secrets.md` | Secrets and credential management |
| `docs/modules/domains.md` | DNS providers, domain import, subdomains |
| `docs/modules/ssl.md` | SSL provisioning, troubleshooting |
| `docs/modules/deployment.md` | Script library, jobs, log polling |
| `docs/modules/templates.md` | Agent templates, install scripts |
| `docs/modules/terminal.md` | WebSocket terminal guide |
| `docs/modules/audit.md` | Audit log reference with all actions |
