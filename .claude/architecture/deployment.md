# Deployment — Architecture Log

Living documentation of implemented code. Agents MUST append here after writing code for this module.

## Implemented Components

### V12 Migration
- **File(s):** `src/main/resources/db/migration/V12__create_deployment_tables.sql`
- **Type:** migration
- **Description:** Creates `deployment_scripts` and `deployment_jobs` tables with indexes on server_id and status
- **Dependencies:** servers, users tables
- **Date:** 2026-03-23

### ScriptType Enum
- **File(s):** `src/main/java/.../deployment/entity/ScriptType.java`
- **Type:** entity (enum)
- **Description:** GENERAL, INSTALL, REMOVE, UPDATE, MAINTENANCE
- **Date:** 2026-03-23

### DeploymentStatus Enum
- **File(s):** `src/main/java/.../deployment/entity/DeploymentStatus.java`
- **Type:** entity (enum)
- **Description:** PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
- **Date:** 2026-03-23

### DeploymentScript Entity
- **File(s):** `src/main/java/.../deployment/entity/DeploymentScript.java`
- **Type:** entity
- **Description:** Named bash script stored in DB. Fields: id, name (unique), description, scriptContent, scriptType, createdBy, timestamps
- **Date:** 2026-03-23

### DeploymentJob Entity
- **File(s):** `src/main/java/.../deployment/entity/DeploymentJob.java`
- **Type:** entity
- **Description:** Tracks execution of a script on a server. Fields: id, serverId, scriptId (nullable for templates), scriptName, status, triggeredBy, startedAt, finishedAt, logs, errorMessage, createdAt
- **Date:** 2026-03-23

### DeploymentScriptRepository
- **File(s):** `src/main/java/.../deployment/repository/DeploymentScriptRepository.java`
- **Type:** repository
- **Description:** JPA repo for DeploymentScript. Custom: existsByName()
- **Date:** 2026-03-23

### DeploymentJobRepository
- **File(s):** `src/main/java/.../deployment/repository/DeploymentJobRepository.java`
- **Type:** repository
- **Description:** JPA repo for DeploymentJob. Custom: existsByServerIdAndStatus(), findByServerId(), findByStatus(), findByServerIdAndStatus()
- **Date:** 2026-03-23

### DTOs
- **File(s):** `src/main/java/.../deployment/dto/CreateScriptRequest.java`, `UpdateScriptRequest.java`, `ScriptResponse.java`, `TriggerDeploymentRequest.java`, `DeploymentJobResponse.java`
- **Type:** dto
- **Description:** Java records with Jakarta validation. CreateScriptRequest requires name, scriptContent, scriptType. TriggerDeploymentRequest requires serverId, scriptId.
- **Date:** 2026-03-23

### DeploymentMapper
- **File(s):** `src/main/java/.../deployment/mapper/DeploymentMapper.java`
- **Type:** mapper
- **Description:** Static utility class. toScriptResponse(), toEntity(), toJobResponse()
- **Date:** 2026-03-23

### AsyncConfig
- **File(s):** `src/main/java/.../deployment/config/AsyncConfig.java`
- **Type:** config
- **Description:** Enables @Async with ThreadPoolTaskExecutor named "deploymentExecutor" (core=4, max=10, queue=50)
- **Date:** 2026-03-23

### ScriptRunner
- **File(s):** `src/main/java/.../deployment/service/ScriptRunner.java`
- **Type:** service
- **Description:** @Async("deploymentExecutor") bean. Uploads script to /tmp/{jobId}.sh, executes via SSH, captures logs, updates job status, cleans up. Separate bean for Spring proxy to work.
- **Dependencies:** DeploymentJobRepository, ServerService, SshService
- **Date:** 2026-03-23

### DeploymentScriptService
- **File(s):** `src/main/java/.../deployment/service/DeploymentScriptService.java`
- **Type:** service
- **Description:** CRUD for deployment scripts. Duplicate name check, audit logging.
- **Dependencies:** DeploymentScriptRepository, AuditService
- **Date:** 2026-03-23

### DeploymentJobService
- **File(s):** `src/main/java/.../deployment/service/DeploymentJobService.java`
- **Type:** service
- **Description:** triggerJob() validates server/script, checks no RUNNING job on server, creates PENDING job, fires ScriptRunner. triggerTemplateJob() for template deployments (no scriptId). cancelJob() only for PENDING. Filterable getJobs().
- **Dependencies:** DeploymentJobRepository, DeploymentScriptService, ServerService, ScriptRunner, AuditService
- **Date:** 2026-03-23

### DeploymentScriptController
- **File(s):** `src/main/java/.../deployment/controller/DeploymentScriptController.java`
- **Type:** controller
- **Description:** REST /api/v1/deployment-scripts — POST, GET (paginated), GET/{id}, PATCH/{id}, DELETE/{id}
- **Dependencies:** DeploymentScriptService
- **Date:** 2026-03-23

### DeploymentJobController
- **File(s):** `src/main/java/.../deployment/controller/DeploymentJobController.java`
- **Type:** controller
- **Description:** REST /api/v1/deployment-jobs — POST (trigger), GET (filterable by serverId, status), GET/{id}, POST/{id}/cancel
- **Dependencies:** DeploymentJobService
- **Date:** 2026-03-23

### deployment.html
- **File(s):** `src/main/resources/static/dev/deployment.html`
- **Type:** frontend
- **Description:** Two sections: Script Library (CRUD + Run modal with server picker) and Job History (filterable, auto-refreshes every 5s when active jobs exist, log viewer modal)
- **Date:** 2026-03-23
