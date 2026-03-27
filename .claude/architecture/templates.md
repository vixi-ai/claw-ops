# Templates — Architecture Log

Living documentation of implemented code. Agents MUST append here after writing code for this module.

## Implemented Components

### V13 Migration
- **File(s):** `src/main/resources/db/migration/V13__create_agent_templates_table.sql`
- **Type:** migration
- **Description:** Creates `agent_templates` table with name (unique), description, agentType, installScript, createdBy, timestamps
- **Dependencies:** users table
- **Date:** 2026-03-23

### AgentTemplate Entity
- **File(s):** `src/main/java/.../templates/entity/AgentTemplate.java`
- **Type:** entity
- **Description:** Agent template with install script. Fields: id, name (unique), description, agentType, installScript, createdBy, timestamps
- **Date:** 2026-03-23

### AgentTemplateRepository
- **File(s):** `src/main/java/.../templates/repository/AgentTemplateRepository.java`
- **Type:** repository
- **Description:** JPA repo. Custom: existsByName()
- **Date:** 2026-03-23

### DTOs
- **File(s):** `src/main/java/.../templates/dto/CreateTemplateRequest.java`, `UpdateTemplateRequest.java`, `TemplateResponse.java`, `DeployTemplateRequest.java`, `DeployTemplateResponse.java`
- **Type:** dto
- **Description:** Java records. CreateTemplateRequest requires name, agentType (pattern: [a-z0-9-]+), installScript. DeployTemplateRequest requires serverId. DeployTemplateResponse returns jobId.
- **Date:** 2026-03-23

### AgentTemplateMapper
- **File(s):** `src/main/java/.../templates/mapper/AgentTemplateMapper.java`
- **Type:** mapper
- **Description:** Static utility. toResponse(), toEntity()
- **Date:** 2026-03-23

### TemplateService
- **File(s):** `src/main/java/.../templates/service/TemplateService.java`
- **Type:** service
- **Description:** CRUD + deployTemplate(). Deploy creates a DeploymentJob via DeploymentJobService.triggerTemplateJob() with scriptName="template:{name}". Audit logging for all operations.
- **Dependencies:** AgentTemplateRepository, DeploymentJobService, AuditService
- **Date:** 2026-03-23

### AgentTemplateController
- **File(s):** `src/main/java/.../templates/controller/AgentTemplateController.java`
- **Type:** controller
- **Description:** REST /api/v1/agent-templates — POST, GET, GET/{id}, PATCH/{id}, DELETE/{id}, POST/{id}/deploy
- **Dependencies:** TemplateService
- **Date:** 2026-03-23

### templates.html
- **File(s):** `src/main/resources/static/dev/templates.html`
- **Type:** frontend
- **Description:** Two sections: Agent Templates (CRUD + Deploy modal with server picker) and Recent Template Deployments (filtered from deployment-jobs where scriptName starts with "template:", log viewer)
- **Date:** 2026-03-23
