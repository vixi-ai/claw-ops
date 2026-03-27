# SSH — Architecture Log

Living documentation of implemented code. Agents MUST append here after writing code for this module.

## Implemented Components

### SshConnectionException
- **File(s):** `src/main/java/.../common/exception/SshConnectionException.java`
- **Type:** exception
- **Description:** Runtime exception for SSH failures. Mapped to HTTP 502 by GlobalExceptionHandler.
- **Dependencies:** GlobalExceptionHandler (common module)
- **Date:** 2026-03-11

### SshConfig
- **File(s):** `src/main/java/.../ssh/config/SshConfig.java`
- **Type:** config
- **Description:** @ConfigurationProperties(prefix = "ssh") — connectionTimeout (10000ms), commandTimeout (60s), strictHostKeyChecking (false), maxOutputSize (1MB).
- **Dependencies:** None
- **Date:** 2026-03-11

### CommandResult / TestConnectionResult
- **File(s):** `src/main/java/.../ssh/model/CommandResult.java`, `TestConnectionResult.java`
- **Type:** model (records)
- **Description:** Internal models for command execution (exitCode, stdout, stderr, durationMs) and connection test (success, message, latencyMs).
- **Date:** 2026-03-11

### ExecuteCommandRequest / CommandResponse
- **File(s):** `src/main/java/.../ssh/dto/ExecuteCommandRequest.java`, `CommandResponse.java`
- **Type:** dto (records)
- **Description:** Request: command (@NotBlank max 4096), timeoutSeconds (optional 1-300). Response: exitCode, stdout, stderr, durationMs, serverId, serverName.
- **Date:** 2026-03-11

### SshService
- **File(s):** `src/main/java/.../ssh/service/SshService.java`
- **Type:** service
- **Description:** Core SSH service using sshj. Methods: testConnection (never throws), executeCommand (with/without timeout), uploadFile (SFTP), downloadFile (SFTP). Supports PASSWORD and PRIVATE_KEY auth with optional passphrase. PromiscuousVerifier when strictHostKeyChecking=false. Output size-limited. Path traversal validation on SFTP.
- **Dependencies:** SecretService (secrets), SshConfig, Server entity (servers)
- **Date:** 2026-03-11

### ServerService SSH wiring
- **File(s):** `src/main/java/.../servers/service/ServerService.java`
- **Type:** service (modified)
- **Description:** testConnection() calls SshService, updates server status (ONLINE/OFFLINE), logs audit. New executeCommand() delegates to SshService with audit logging.
- **Dependencies:** SshService, AuditService
- **Date:** 2026-03-11

### ServerController SSH endpoint
- **File(s):** `src/main/java/.../servers/controller/ServerController.java`
- **Type:** controller (modified)
- **Description:** POST /{id}/ssh/command endpoint. testConnection now passes userId for audit.
- **Date:** 2026-03-11

### SshSession (Interactive Session Model)
- **File(s):** `src/main/java/.../ssh/model/SshSession.java`
- **Type:** model
- **Description:** Wrapper for an interactive SSH session: SSHClient, Session, Shell. Provides getInputStream/getOutputStream for bidirectional streaming, isConnected() check, close() that cleans up shell/session/client in order. Used by WebSocket terminal handler.
- **Dependencies:** sshj library
- **Date:** 2026-03-11

### SshService.openInteractiveSession
- **File(s):** `src/main/java/.../ssh/service/SshService.java` (modified)
- **Type:** service (modified)
- **Description:** New method openInteractiveSession(Server, cols, rows) → SshSession. Allocates PTY with xterm-256color, starts shell. SSHClient is NOT auto-closed — stays alive for session duration. On failure, cleans up client before throwing SshConnectionException.
- **Dependencies:** SecretService, SshConfig
- **Date:** 2026-03-11

### SSH Dev Admin Page
- **File(s):** `src/main/resources/static/dev/ssh.html`
- **Type:** frontend (dev page)
- **Description:** Server list sidebar, command input (Enter to run), optional timeout, output panel with exit code coloring, stderr highlighting, command history (localStorage, 20 items), test connection button. Now includes Terminal tab with WebSocket interactive terminal.
- **Date:** 2026-03-11
