# Deployment Module

The deployment module provides a bash script library and an async job execution engine. Write scripts, store them in the database, and run them on any managed server with one click.

## Concepts

- **Deployment Script**: a named bash script stored in the database
- **Deployment Job**: a record of running a script on a server (tracks status, logs, timing)
- **Script Runner**: the async engine that uploads and executes scripts via SSH

## Script Types

| Type | Purpose |
|------|---------|
| `GENERAL` | General-purpose scripts |
| `INSTALL` | Software installation |
| `REMOVE` | Software removal |
| `UPDATE` | Update/upgrade operations |
| `MAINTENANCE` | Maintenance tasks (cleanup, backups, etc.) |

## Creating a Script

```bash
curl -X POST http://localhost:8080/api/v1/deployment-scripts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "install-docker",
    "description": "Install Docker CE on Ubuntu",
    "scriptType": "INSTALL",
    "scriptContent": "#!/bin/bash\nset -e\n\napt-get update\napt-get install -y docker.io\nsystemctl enable docker\nsystemctl start docker\ndocker --version"
  }'
```

**Note:** creating, updating, and deleting scripts requires ADMIN role.

## Running a Script

Trigger a deployment job (any authenticated user):

```bash
curl -X POST http://localhost:8080/api/v1/deployment-jobs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "serverId": "server-uuid",
    "scriptId": "script-uuid"
  }'
```

The job returns immediately with status `PENDING`. The script executes asynchronously.

### What Happens

1. A `DeploymentJob` record is created with status `PENDING`
2. The `ScriptRunner` picks it up asynchronously (thread pool: 4-10 threads)
3. Status changes to `RUNNING`, `startedAt` is recorded
4. The script is uploaded to `/tmp/{jobId}.sh` on the server via SFTP
5. `bash /tmp/{jobId}.sh` is executed (timeout: 5 minutes)
6. stdout/stderr are captured as `logs`
7. Status changes to `COMPLETED` (exit code 0) or `FAILED` (non-zero)
8. The remote script file is cleaned up
9. `finishedAt` is recorded

### Concurrency Control

Only **one job can be RUNNING per server** at a time. If you try to trigger a job on a server that already has a running job, you get a 409 Conflict error.

## Monitoring Jobs

### List Jobs

```bash
# All jobs
curl "http://localhost:8080/api/v1/deployment-jobs?sort=createdAt,desc" \
  -H "Authorization: Bearer $TOKEN"

# Filter by server
curl "http://localhost:8080/api/v1/deployment-jobs?serverId=$SID" \
  -H "Authorization: Bearer $TOKEN"

# Filter by status
curl "http://localhost:8080/api/v1/deployment-jobs?status=RUNNING" \
  -H "Authorization: Bearer $TOKEN"
```

### View Logs

```bash
curl http://localhost:8080/api/v1/deployment-jobs/$JOB_ID \
  -H "Authorization: Bearer $TOKEN"
```

The response includes `logs` (script output) and `errorMessage` (if failed).

### Cancel a Job

Only PENDING jobs can be cancelled (RUNNING jobs cannot be interrupted):

```bash
curl -X POST http://localhost:8080/api/v1/deployment-jobs/$JOB_ID/cancel \
  -H "Authorization: Bearer $TOKEN"
```

## Job Statuses

| Status | Meaning |
|--------|---------|
| `PENDING` | Created, waiting to be picked up by the executor |
| `RUNNING` | Script is being executed on the server |
| `COMPLETED` | Script finished with exit code 0 |
| `FAILED` | Script finished with non-zero exit code or an exception |
| `CANCELLED` | Cancelled before execution started |

## UI

The deployment page (`/dev/deployment.html`) has two sections:

1. **Script Library** — create, edit, delete, and run scripts
2. **Job History** — filtered list of jobs with live status refresh (every 5 seconds when active jobs exist), log viewer modal

The UI includes a file upload button to load `.sh` files directly into the script editor.
