# Task 17: Deployment Service + Agent Templates

**Status:** DONE
**Module(s):** deployment, templates, audit, auth
**Priority:** HIGH
**Created:** 2026-03-23
**Completed:** 2026-03-23

## Description

Implement the `deployment` and `templates` modules from scratch. Neither has any Java code yet.

- **Deployment module** — a bash script library. Users write named bash scripts and store them in the database. From the UI they select a script + target server, triggering an async `DeploymentJob` that uploads and executes the script via SSH, streaming logs.
- **Templates module** — agent templates. Each template is a bash script that, when run on a server, creates the OpenClaw agent directory structure (`~/openclaw/agents/{agentType}/`) with `agent.md` and `skills/*.md` config files. Deploying a template creates a `DeploymentJob`.

## Acceptance Criteria

- [ ] V12 migration creates `deployment_scripts` and `deployment_jobs` tables
- [ ] V13 migration creates `agent_templates` table
- [ ] `DeploymentScript` CRUD works: create, list, get, update, delete
- [ ] `POST /api/v1/deployment-jobs` triggers script on server, returns `jobId` immediately (async)
- [ ] Job status transitions: PENDING → RUNNING → COMPLETED or FAILED
- [ ] Script uploaded to `/tmp/{jobId}.sh`, executed, cleaned up after run
- [ ] Concurrent job rejection: server with a RUNNING job refuses a new trigger
- [ ] `AgentTemplate` CRUD works: create, list, get, update, delete
- [ ] `POST /api/v1/agent-templates/{id}/deploy` creates a DeploymentJob and runs installScript
- [ ] `deployment.html` — script library + job history with live log polling (5s refresh)
- [ ] `templates.html` — template library + deploy to server + recent deployments
- [ ] Audit events logged: SCRIPT_CREATED, SCRIPT_DELETED, JOB_TRIGGERED, JOB_CANCELLED, TEMPLATE_CREATED, TEMPLATE_DELETED, TEMPLATE_DEPLOYED
- [ ] Build succeeds: `./mvnw clean install -DskipTests`

## Implementation Notes

### Existing infrastructure to reuse

| Component | File | What it provides |
|-----------|------|------------------|
| SSH command execution | `ssh/service/SshService.java` | `executeCommand(server, command, timeoutSeconds)` |
| SSH file upload | `ssh/service/SshService.java` | `uploadFile(server, byte[], remotePath)` |
| Server entity access | `servers/service/ServerService.java` | `getServerEntity(UUID)` |
| Audit logging | `audit/service/AuditService.java` | `log(userId, resourceId, action, detail)` |
| Exception types | `common/exception/` | ResourceNotFoundException, DuplicateResourceException |
| Entity pattern | Any existing entity | UUID PK, `@PrePersist`/`@PreUpdate`, Instant timestamps |
| DTO pattern | Any existing DTO | Java records + Jakarta validation annotations |
| Mapper pattern | Any existing mapper | Static final class, static `toResponse()`/`toEntity()` methods |
| Controller pattern | `servers/controller/ServerController.java` | `@RestController`, `ResponseEntity<>`, `@Valid`, userId from `Authentication` |

### Database Migrations

**V12__create_deployment_tables.sql**
```sql
CREATE TABLE deployment_scripts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    script_content TEXT NOT NULL,
    script_type VARCHAR(30) NOT NULL,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE deployment_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    server_id UUID NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    script_id UUID REFERENCES deployment_scripts(id) ON DELETE SET NULL,
    script_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    triggered_by UUID REFERENCES users(id) ON DELETE SET NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    logs TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_deployment_jobs_server_id ON deployment_jobs(server_id);
CREATE INDEX idx_deployment_jobs_status ON deployment_jobs(status);
```

**V13__create_agent_templates_table.sql**
```sql
CREATE TABLE agent_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    agent_type VARCHAR(100) NOT NULL,
    install_script TEXT NOT NULL,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Async Pattern (new to this project)

This module introduces `@Async` execution. Create `deployment/config/AsyncConfig.java`:

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    @Bean(name = "deploymentExecutor")
    public ThreadPoolTaskExecutor deploymentExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(4);
        exec.setMaxPoolSize(10);
        exec.setQueueCapacity(50);
        exec.setThreadNamePrefix("deploy-");
        exec.initialize();
        return exec;
    }
}
```

### ScriptRunner (async SSH executor)

`deployment/service/ScriptRunner.java` — annotated with `@Async("deploymentExecutor")`:

```
run(UUID jobId, UUID serverId, String scriptContent):
  1. Load job, set RUNNING + startedAt, save
  2. Load Server via serverService.getServerEntity(serverId)
  3. uploadFile(server, scriptContent.getBytes(UTF_8), "/tmp/{jobId}.sh")
  4. executeCommand(server, "bash /tmp/{jobId}.sh", 300)
  5. Set logs = stdout (fallback to stderr)
  6. If exitCode == 0 → COMPLETED, else FAILED + errorMessage
  7. finally: executeCommand(server, "rm -f /tmp/{jobId}.sh", 10)
  8. finally: set finishedAt, save job
```

**Important:** `ScriptRunner` must be a separate Spring-managed `@Service` bean (not called internally from `DeploymentJobService`) for `@Async` to work through the Spring proxy.

### DeploymentJobService.triggerJob() logic

1. Load server entity (throws 404 if missing)
2. Load script entity (throws 404 if missing)
3. Check `jobRepository.existsByServerIdAndStatus(serverId, RUNNING)` → throw `DeploymentException` if true
4. Create and save `DeploymentJob` with status PENDING
5. Call `scriptRunner.run(jobId, serverId, scriptContent)` — returns immediately
6. Log audit event
7. Return `DeploymentJobResponse`

### DeploymentJobService.cancelJob() logic

- Load job, check status == PENDING (only PENDING can be cancelled)
- Set status = CANCELLED, save
- If status is RUNNING → throw with "Cannot cancel a running job"

### TemplateService.deployTemplate() logic

1. Load template (throws 404 if missing)
2. Create `DeploymentJob` with `scriptName = "template:" + template.getName()`, no `scriptId`
3. Call `scriptRunner.run(jobId, serverId, template.getInstallScript())`
4. Log audit event
5. Return `DeployTemplateResponse(jobId)`

### Enums

**ScriptType:** `GENERAL`, `INSTALL`, `REMOVE`, `UPDATE`, `MAINTENANCE`

**DeploymentStatus:** `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`

### DTOs

All use Java records with Jakarta validation.

**CreateScriptRequest:** `name @NotBlank @Size(max=100)`, `description`, `scriptContent @NotBlank`, `scriptType @NotNull`

**UpdateScriptRequest:** all fields optional (nullable)

**ScriptResponse:** `id`, `name`, `description`, `scriptContent`, `scriptType`, `createdAt`, `updatedAt`

**TriggerDeploymentRequest:** `serverId @NotNull`, `scriptId @NotNull`

**DeploymentJobResponse:** `id`, `serverId`, `scriptId`, `scriptName`, `status`, `triggeredBy`, `startedAt`, `finishedAt`, `logs`, `errorMessage`, `createdAt`

**CreateTemplateRequest:** `name @NotBlank @Size(max=100)`, `description`, `agentType @NotBlank @Pattern("[a-z0-9-]+")`, `installScript @NotBlank`

**UpdateTemplateRequest:** all fields optional

**TemplateResponse:** `id`, `name`, `description`, `agentType`, `installScript`, `createdAt`, `updatedAt`

**DeployTemplateRequest:** `serverId @NotNull`

**DeployTemplateResponse:** `jobId`

### API Endpoints

**Deployment Scripts — `/api/v1/deployment-scripts`**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/deployment-scripts` | ADMIN | Create script |
| GET | `/api/v1/deployment-scripts` | Yes | List all (paginated) |
| GET | `/api/v1/deployment-scripts/{id}` | Yes | Get by ID |
| PATCH | `/api/v1/deployment-scripts/{id}` | ADMIN | Update |
| DELETE | `/api/v1/deployment-scripts/{id}` | ADMIN | Delete |

**Deployment Jobs — `/api/v1/deployment-jobs`**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/deployment-jobs` | Yes | Trigger script on server |
| GET | `/api/v1/deployment-jobs` | Yes | List jobs (params: `serverId`, `status`) |
| GET | `/api/v1/deployment-jobs/{id}` | Yes | Get job + logs |
| POST | `/api/v1/deployment-jobs/{id}/cancel` | Yes | Cancel PENDING job |

**Agent Templates — `/api/v1/agent-templates`**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/agent-templates` | ADMIN | Create template |
| GET | `/api/v1/agent-templates` | Yes | List all |
| GET | `/api/v1/agent-templates/{id}` | Yes | Get (includes installScript) |
| PATCH | `/api/v1/agent-templates/{id}` | ADMIN | Update |
| DELETE | `/api/v1/agent-templates/{id}` | ADMIN | Delete |
| POST | `/api/v1/agent-templates/{id}/deploy` | Yes | Deploy to server → returns `{ jobId }` |

### Frontend: deployment.html

Match the style/layout of `servers.html` and `domains.html`.

**Section 1 — Script Library**
- Table: Name | Type | Description | Created | Actions
- Actions: Edit (opens modal pre-filled), Delete (confirm), Run (opens server-picker modal)
- "+ New Script" button → modal: Name, Type `<select>`, Description, `<textarea rows=15>` for script content
- "Run" modal: server `<select>` populated from `GET /api/v1/servers` → on confirm, `POST /api/v1/deployment-jobs` → shows job badge with link to logs

**Section 2 — Job History**
- Table: Script | Server | Status (color badge) | Started | Duration | Actions
- Filter bar: Server dropdown + Status dropdown
- "View Logs" → modal showing `job.logs` in monospace `<pre>` block
- Page auto-refreshes job list every 5s if any row has status RUNNING or PENDING

### Frontend: templates.html

**Section 1 — Agent Templates**
- Table: Name | Agent Type | Description | Created | Actions
- Actions: Edit, Delete, Deploy
- "+ New Template" → modal: Name, Agent Type (text input, kebab-case hint), Description, `<textarea rows=15>` for install script
- "Deploy" → server-picker modal → `POST /api/v1/agent-templates/{id}/deploy` → shows job ID with link to deployment.html

**Section 2 — Recent Template Deployments**
- `GET /api/v1/deployment-jobs?size=20` → filter client-side for `scriptName.startsWith("template:")`
- Table: Template | Server | Status | Deployed At | View Logs button

### Audit Actions to Add

In `audit/entity/AuditAction.java`:
- `SCRIPT_CREATED`, `SCRIPT_UPDATED`, `SCRIPT_DELETED`
- `JOB_TRIGGERED`, `JOB_CANCELLED`
- `TEMPLATE_CREATED`, `TEMPLATE_UPDATED`, `TEMPLATE_DELETED`, `TEMPLATE_DEPLOYED`

### SecurityConfig Rules to Add

In `auth/config/SecurityConfig.java`:
- `POST /api/v1/deployment-scripts`, `PATCH`, `DELETE` → ADMIN only
- `GET /api/v1/deployment-scripts/**` → authenticated
- `POST /api/v1/deployment-jobs`, `GET /api/v1/deployment-jobs/**` → authenticated
- `POST /api/v1/deployment-jobs/*/cancel` → authenticated
- `POST /api/v1/agent-templates`, `PATCH /api/v1/agent-templates/*`, `DELETE /api/v1/agent-templates/*` → ADMIN only
- `GET /api/v1/agent-templates/**`, `POST /api/v1/agent-templates/*/deploy` → authenticated

### DeploymentException

Add `DeploymentException` to `common/exception/` if not present:
```java
public class DeploymentException extends RuntimeException {
    public DeploymentException(String message) { super(message); }
}
```
Register it in `GlobalExceptionHandler` → HTTP 409 or 422.

## New Files

| File | Type |
|------|------|
| `db/migration/V12__create_deployment_tables.sql` | Migration |
| `db/migration/V13__create_agent_templates_table.sql` | Migration |
| `deployment/entity/DeploymentScript.java` | Entity |
| `deployment/entity/DeploymentJob.java` | Entity |
| `deployment/entity/ScriptType.java` | Enum |
| `deployment/entity/DeploymentStatus.java` | Enum |
| `deployment/repository/DeploymentScriptRepository.java` | Repository |
| `deployment/repository/DeploymentJobRepository.java` | Repository |
| `deployment/dto/CreateScriptRequest.java` | DTO |
| `deployment/dto/UpdateScriptRequest.java` | DTO |
| `deployment/dto/ScriptResponse.java` | DTO |
| `deployment/dto/TriggerDeploymentRequest.java` | DTO |
| `deployment/dto/DeploymentJobResponse.java` | DTO |
| `deployment/mapper/DeploymentMapper.java` | Mapper |
| `deployment/service/DeploymentScriptService.java` | Service |
| `deployment/service/DeploymentJobService.java` | Service |
| `deployment/service/ScriptRunner.java` | Async Service |
| `deployment/controller/DeploymentScriptController.java` | Controller |
| `deployment/controller/DeploymentJobController.java` | Controller |
| `deployment/config/AsyncConfig.java` | Config |
| `templates/entity/AgentTemplate.java` | Entity |
| `templates/repository/AgentTemplateRepository.java` | Repository |
| `templates/dto/CreateTemplateRequest.java` | DTO |
| `templates/dto/UpdateTemplateRequest.java` | DTO |
| `templates/dto/TemplateResponse.java` | DTO |
| `templates/dto/DeployTemplateRequest.java` | DTO |
| `templates/dto/DeployTemplateResponse.java` | DTO |
| `templates/mapper/AgentTemplateMapper.java` | Mapper |
| `templates/service/TemplateService.java` | Service |
| `templates/controller/AgentTemplateController.java` | Controller |
| `static/dev/deployment.html` | Frontend |
| `static/dev/templates.html` | Frontend |

## Modified Files

| File | Change |
|------|--------|
| `audit/entity/AuditAction.java` | Add deployment + template audit actions |
| `auth/config/SecurityConfig.java` | Add endpoint security rules |
| `common/exception/GlobalExceptionHandler.java` | Handle DeploymentException |
| `static/dev/index.html` | Add links to deployment.html and templates.html |

## Files Modified

### New Files (31)
- `db/migration/V12__create_deployment_tables.sql`
- `db/migration/V13__create_agent_templates_table.sql`
- `deployment/entity/ScriptType.java`, `DeploymentStatus.java`, `DeploymentScript.java`, `DeploymentJob.java`
- `deployment/repository/DeploymentScriptRepository.java`, `DeploymentJobRepository.java`
- `deployment/dto/CreateScriptRequest.java`, `UpdateScriptRequest.java`, `ScriptResponse.java`, `TriggerDeploymentRequest.java`, `DeploymentJobResponse.java`
- `deployment/mapper/DeploymentMapper.java`
- `deployment/config/AsyncConfig.java`
- `deployment/service/ScriptRunner.java`, `DeploymentScriptService.java`, `DeploymentJobService.java`
- `deployment/controller/DeploymentScriptController.java`, `DeploymentJobController.java`
- `templates/entity/AgentTemplate.java`
- `templates/repository/AgentTemplateRepository.java`
- `templates/dto/CreateTemplateRequest.java`, `UpdateTemplateRequest.java`, `TemplateResponse.java`, `DeployTemplateRequest.java`, `DeployTemplateResponse.java`
- `templates/mapper/AgentTemplateMapper.java`
- `templates/service/TemplateService.java`
- `templates/controller/AgentTemplateController.java`
- `static/dev/deployment.html`, `static/dev/templates.html`
- `common/exception/DeploymentException.java`

### Modified Files (4)
- `audit/entity/AuditAction.java` — Added SCRIPT_CREATED/UPDATED/DELETED, JOB_TRIGGERED/CANCELLED, TEMPLATE_UPDATED/DELETED
- `auth/config/SecurityConfig.java` — Added ADMIN-only rules for deployment-scripts and agent-templates create/update/delete
- `common/exception/GlobalExceptionHandler.java` — Added DeploymentException handler (409)
- `static/dev/index.html` — Changed Deployments and Templates cards from "Coming soon" to "Active"
