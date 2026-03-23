# Deployment Module

## Purpose

A bash script library and execution engine. Users create named bash scripts and store them in the database. From the UI (or API), they select a script and a target server, triggering a `DeploymentJob` that uploads and executes the script on the remote server via SSH. This is a general-purpose managed command runner — not tied to any specific deployment type.

## Package

`com.openclaw.manager.openclawserversmanager.deployment`

## Components

### Entity: `DeploymentScript`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| name | String | UNIQUE, NOT NULL |
| description | String | nullable |
| scriptContent | String (TEXT) | NOT NULL — raw bash script content |
| scriptType | ScriptType (enum) | NOT NULL |
| createdBy | UUID | FK → User |
| createdAt | Instant | auto-set |
| updatedAt | Instant | auto-set on update |

### Enum: `ScriptType`

- `GENERAL` — uncategorized/misc scripts
- `INSTALL` — installation scripts
- `REMOVE` — removal/cleanup scripts
- `UPDATE` — update scripts
- `MAINTENANCE` — maintenance operations

### Entity: `DeploymentJob`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| serverId | UUID | FK → Server, NOT NULL |
| scriptId | UUID | FK → DeploymentScript, NOT NULL |
| status | DeploymentStatus (enum) | NOT NULL, default PENDING |
| triggeredBy | UUID | FK → User |
| startedAt | Instant | nullable (set when RUNNING) |
| finishedAt | Instant | nullable (set when COMPLETED/FAILED) |
| logs | String (TEXT) | accumulated stdout/stderr output |
| errorMessage | String | nullable |
| createdAt | Instant | auto-set |

### Enum: `DeploymentStatus`

- `PENDING` — queued, not yet started
- `RUNNING` — script executing on remote server
- `COMPLETED` — finished successfully (exit code 0)
- `FAILED` — finished with error (non-zero exit code)
- `CANCELLED` — manually cancelled before completion

### DTOs

**`CreateScriptRequest`**
- `name` — `@NotBlank @Size(max = 100)`
- `description` — optional
- `scriptContent` — `@NotBlank` (raw bash script)
- `scriptType` — `@NotNull`

**`UpdateScriptRequest`**
- `name` — optional
- `description` — optional
- `scriptContent` — optional
- `scriptType` — optional

**`ScriptResponse`**
- `id`, `name`, `description`, `scriptContent`, `scriptType`, `createdAt`, `updatedAt`

**`TriggerDeploymentRequest`**
- `serverId` — `@NotNull`
- `scriptId` — `@NotNull`

**`DeploymentJobResponse`**
- `id`, `serverId`, `scriptId`, `scriptName`, `status`, `startedAt`, `finishedAt`, `logs`, `errorMessage`, `createdAt`

### Service: `DeploymentScriptService`

- `createScript(CreateScriptRequest, UUID userId)` → `ScriptResponse`
- `getAllScripts(Pageable)` → paginated list
- `getScriptById(UUID)` → `ScriptResponse`
- `updateScript(UUID, UpdateScriptRequest)` → `ScriptResponse`
- `deleteScript(UUID)` — removes script (only if no running jobs reference it)

### Service: `DeploymentJobService`

- `triggerJob(TriggerDeploymentRequest, UUID userId)` → `DeploymentJobResponse`
- `getJob(UUID jobId)` → `DeploymentJobResponse`
- `getJobsForServer(UUID serverId, Pageable)` → paginated list
- `cancelJob(UUID jobId, UUID userId)` — marks job CANCELLED if PENDING

### Service: `ScriptRunner` (internal, async)

- Uploads script content to `/tmp/{jobId}.sh` via SFTP
- Executes it via SSH: `bash /tmp/{jobId}.sh`
- Streams stdout/stderr output to `DeploymentJob.logs`
- Sets status to `COMPLETED` or `FAILED` on completion
- Cleans up: removes `/tmp/{jobId}.sh` after execution
- Handles timeouts

### Repository: `DeploymentScriptRepository`

- `findByName(String)` → `Optional<DeploymentScript>`
- `existsByName(String)` → `boolean`

### Repository: `DeploymentJobRepository`

- `findByServerId(UUID, Pageable)` → `Page<DeploymentJob>`
- `findByStatus(DeploymentStatus)` → `List<DeploymentJob>`
- `findByServerIdAndStatus(UUID, DeploymentStatus)` → `List<DeploymentJob>`

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/deployment-scripts` | Yes (ADMIN) | Create a new script |
| GET | `/api/v1/deployment-scripts` | Yes | List all scripts (paginated) |
| GET | `/api/v1/deployment-scripts/{id}` | Yes | Get script details and content |
| PATCH | `/api/v1/deployment-scripts/{id}` | Yes (ADMIN) | Update script |
| DELETE | `/api/v1/deployment-scripts/{id}` | Yes (ADMIN) | Delete script |
| POST | `/api/v1/deployment-jobs` | Yes | Trigger: run a script on a server |
| GET | `/api/v1/deployment-jobs/{id}` | Yes | Get job status and logs |
| GET | `/api/v1/deployment-jobs` | Yes | List jobs (filter by serverId, status) |
| POST | `/api/v1/deployment-jobs/{id}/cancel` | Yes | Cancel a pending job |

## Business Rules

- Script content is stored as raw bash text in the database — no file storage
- Execution uploads the script content to `/tmp/{jobId}.sh`, runs it, then deletes it
- Only one job can run per server at a time — concurrent jobs are rejected with a clear error
- Jobs run **asynchronously** — the trigger endpoint returns the job ID immediately
- Scripts must not contain hardcoded credentials — secrets are injected at runtime via environment or SSH session
- Validate script content is non-empty; do not validate syntax (that's the user's responsibility)
- `PENDING` jobs can be cancelled; `RUNNING` jobs cannot be cancelled safely — reject with clear message

## Security Considerations

- Script creation/modification restricted to `ADMIN` role — only admins can add scripts to the library
- All job triggers are logged to audit with the triggering user and server
- Scripts run with the SSH user's permissions — no privilege escalation
- Validate `serverId` and `scriptId` exist before triggering

## Dependencies

- **servers** — to get server SSH connection details
- **ssh** — to upload and execute scripts via SFTP/SSH
- **secrets** — indirectly for SSH credentials
- **audit** — to log all script executions
- **templates** — calls into this module to execute template install scripts
