# Templates Module

## Purpose

Manages agent templates for OpenClaw. A template is a named bash script that, when deployed to a server, creates the OpenClaw agent directory structure — folders for the agent and its skills, each populated with markdown (`.md`) config files. These MD files define the agent's purpose, model, instructions, and the skills it has available.

## Package

`com.openclaw.manager.openclawserversmanager.templates`

## What an Agent Directory Looks Like

When an agent template is deployed to a server, the install script creates:

```
~/openclaw/agents/{agentType}/
  agent.md                  ← agent config: name, description, model, instructions
  skills/
    skill-1.md              ← skill definition: name, description, trigger, handler
    skill-2.md
    ...
```

The `.md` files follow a structured format read by the OpenClaw runtime on the server.

## Components

### Entity: `AgentTemplate`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| name | String | UNIQUE, NOT NULL |
| description | String | nullable |
| agentType | String | NOT NULL — identifier used as directory name (e.g. `research-agent`) |
| installScript | String (TEXT) | NOT NULL — bash script that creates directory + MD files |
| createdBy | UUID | FK → User |
| createdAt | Instant | auto-set |
| updatedAt | Instant | auto-set on update |

### DTOs

**`CreateTemplateRequest`**
- `name` — `@NotBlank @Size(max = 100)`
- `description` — optional
- `agentType` — `@NotBlank @Pattern(regexp = "[a-z0-9-]+")` — kebab-case identifier
- `installScript` — `@NotBlank` (raw bash script content)

**`UpdateTemplateRequest`**
- `name` — optional
- `description` — optional
- `agentType` — optional
- `installScript` — optional

**`TemplateResponse`**
- `id`, `name`, `description`, `agentType`, `installScript`, `createdAt`, `updatedAt`

**`DeployTemplateRequest`**
- `serverId` — `@NotNull`

**`DeployTemplateResponse`**
- `jobId` — the `DeploymentJob` ID created to execute the install

### Service: `TemplateService`

- `createTemplate(CreateTemplateRequest, UUID userId)` → `TemplateResponse`
- `getAllTemplates(Pageable)` → paginated list
- `getTemplateById(UUID)` → `TemplateResponse`
- `updateTemplate(UUID, UpdateTemplateRequest)` → `TemplateResponse`
- `deleteTemplate(UUID)` — removes template
- `deployTemplate(UUID templateId, UUID serverId, UUID userId)` → `DeployTemplateResponse`

### Repository: `AgentTemplateRepository`

- `findByName(String)` → `Optional<AgentTemplate>`
- `existsByName(String)` → `boolean`
- `findByAgentType(String)` → `List<AgentTemplate>`

## Template Deployment Flow

1. `POST /api/v1/agent-templates/{id}/deploy` with `{ serverId }` body
2. `TemplateService.deployTemplate()` looks up the template and server
3. Creates a `DeploymentScript` (ephemeral) or calls `DeploymentJobService` directly with the `installScript` content
4. A `DeploymentJob` is created and executed asynchronously on the target server
5. The install script runs on the server, creating the agent directory structure
6. Returns the `DeploymentJob` ID — caller can poll for completion status

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/agent-templates` | Yes (ADMIN) | Create new agent template |
| GET | `/api/v1/agent-templates` | Yes | List all templates |
| GET | `/api/v1/agent-templates/{id}` | Yes | Get template details (includes install script) |
| PATCH | `/api/v1/agent-templates/{id}` | Yes (ADMIN) | Update template |
| DELETE | `/api/v1/agent-templates/{id}` | Yes (ADMIN) | Delete template |
| POST | `/api/v1/agent-templates/{id}/deploy` | Yes | Deploy template to a server |

## Business Rules

- Template `name` must be unique
- `agentType` must be kebab-case (e.g. `research-agent`, `coding-agent`) — used as the directory name on the server
- `installScript` must be non-empty bash — rejected if blank on creation
- Deploying a template triggers a `DeploymentJob` in the deployment module
- Deleting a template does not affect already-deployed agent directories on servers
- Template creation/modification is `ADMIN`-only; listing and deploying is open to all authenticated users

## Security Considerations

- Install scripts run with SSH user permissions on the remote server — content must be reviewed
- `agentType` is validated as kebab-case to prevent path traversal in the directory name
- All deployments are logged to audit

## Dependencies

- **deployment** — to execute the install script on the target server as a `DeploymentJob`
- **servers** — to validate target server exists
- **audit** — to log template CRUD and deployment events
