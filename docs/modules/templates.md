# Templates Module

The templates module manages agent templates — bash scripts that install OpenClaw agent directory structures on remote servers. When you deploy a template, it runs as a deployment job.

## Concepts

- **Agent Template**: a named bash script that sets up an agent directory with markdown config files
- **Agent Type**: a kebab-case identifier for the agent (e.g., `web-scraper`, `data-pipeline`)
- **Install Script**: the bash script that creates the directory structure

## Agent Directory Structure

Templates typically create the following structure on the target server:

```
~/openclaw/agents/{agent-type}/
  agent.md          # Agent configuration
  skills/
    skill-1.md      # Skill configuration files
    skill-2.md
```

## Creating a Template

```bash
curl -X POST http://localhost:8080/api/v1/agent-templates \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Web Scraper Agent",
    "agentType": "web-scraper",
    "description": "Installs web scraper agent with crawl and extract skills",
    "installScript": "#!/bin/bash\nset -e\n\nAGENT_TYPE=\"web-scraper\"\nBASE_DIR=\"$HOME/openclaw/agents/$AGENT_TYPE\"\n\nmkdir -p \"$BASE_DIR/skills\"\n\ncat > \"$BASE_DIR/agent.md\" << '\''EOF'\''\n# Web Scraper Agent\n\nType: web-scraper\nDescription: Crawls websites and extracts structured data\nEOF\n\ncat > \"$BASE_DIR/skills/crawl.md\" << '\''EOF'\''\n# Crawl Skill\n\nDescription: Navigate and crawl web pages\nEOF\n\necho \"Agent $AGENT_TYPE installed at $BASE_DIR\""
  }'
```

**Requirements:**
- `name`: unique, max 100 characters
- `agentType`: must match `[a-z0-9-]+` (lowercase alphanumeric with hyphens)
- `installScript`: the bash script to execute

**Note:** creating, updating, and deleting templates requires ADMIN role.

## Deploying a Template

Deploy to a server (any authenticated user):

```bash
curl -X POST http://localhost:8080/api/v1/agent-templates/$TEMPLATE_ID/deploy \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"serverId": "server-uuid"}'
```

**Response:**
```json
{
  "jobId": "deployment-job-uuid"
}
```

### What Happens

1. The template's `installScript` is used to create a deployment job
2. The job's `scriptName` is set to `template:{template-name}` (to distinguish from regular scripts)
3. The script runs asynchronously via the ScriptRunner (same as regular deployment jobs)
4. You can monitor the job via the deployment jobs API

## Monitoring Deployments

Template deployments are regular deployment jobs with `scriptName` starting with `template:`:

```bash
# View the job
curl http://localhost:8080/api/v1/deployment-jobs/$JOB_ID \
  -H "Authorization: Bearer $TOKEN"
```

## UI

The templates page (`/dev/templates.html`) has two sections:

1. **Agent Templates** — create, edit, delete, and deploy templates
2. **Recent Template Deployments** — filtered view of deployment jobs where `scriptName` starts with `template:`

The UI includes:
- A file upload button to load `.sh` install scripts
- A server picker modal for deployment
- Log viewer for deployment results
