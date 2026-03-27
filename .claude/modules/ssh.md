# SSH Module

## Purpose

Handles SSH connections to remote servers. Provides command execution, file upload/download, and connection testing. This module is the bridge between the control plane and the actual infrastructure.

## Package

`com.openclaw.manager.openclawserversmanager.ssh`

## Components

### Service: `SshService`

Core service that manages SSH sessions and operations:

- `testConnection(Server server)` → `TestConnectionResult` — verifies SSH connectivity
- `executeCommand(Server server, String command)` → `CommandResult` — runs a command and returns output
- `executeCommand(Server server, String command, long timeoutSeconds)` → `CommandResult` — with timeout
- `uploadFile(Server server, byte[] content, String remotePath)` — uploads a file via SFTP
- `downloadFile(Server server, String remotePath)` → `byte[]` — downloads a file via SFTP
- `openInteractiveSession(Server server)` → `SshSession` — for terminal module (long-lived)

### Model: `CommandResult`

| Field | Type | Description |
|-------|------|-------------|
| exitCode | int | Command exit code (0 = success) |
| stdout | String | Standard output |
| stderr | String | Standard error |
| durationMs | long | Execution time |

### Model: `SshSession`

Wraps a live SSH connection for interactive terminal use:

- `getInputStream()` — reads output from remote shell
- `getOutputStream()` — writes input to remote shell
- `resize(int cols, int rows)` — terminal resize signal
- `isConnected()` — connection status check
- `close()` — cleanup and disconnect

### Model: `TestConnectionResult`

- `success` — boolean
- `message` — description
- `latencyMs` — round-trip time

### Config: `SshConfig`

- `ssh.connection-timeout` — connection timeout in ms (default: 10000)
- `ssh.command-timeout` — command execution timeout in seconds (default: 60)
- `ssh.known-hosts-file` — path to known hosts (or disable strict host checking for managed infra)

### DTOs

**`ExecuteCommandRequest`**
- `command` — `@NotBlank @Size(max = 4096)`
- `timeoutSeconds` — optional, `@Min(1) @Max(300)`

**`CommandResponse`**
- `exitCode`, `stdout`, `stderr`, `durationMs`

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/servers/{id}/ssh/command` | Yes | Execute command on server |
| GET | `/api/v1/servers/{id}/ssh/session-token` | Yes | Get WebSocket session token |

## Business Rules

- SSH credentials are resolved through the **secrets module** — never stored in the SSH module
- All SSH operations must have timeouts — no indefinite hanging connections
- Connection failures should throw `SshConnectionException` with a descriptive message
- Command output should be size-limited (e.g., max 1MB) to prevent memory issues
- Session tokens for WebSocket terminal are short-lived (single use, expires in 60 seconds)
- File uploads must validate remote path to prevent path traversal attacks

## Security Considerations

- Never log SSH credentials or private keys
- Command execution is a privileged operation — log every command to audit
- Limit command length and output size
- Consider command allowlisting/denylisting for dangerous commands (rm -rf /, etc.)
- Interactive sessions must be authenticated and authorized per-user

## Library Choice

Use **sshj** (preferred) or **Apache Mina SSHD**:

**sshj example pattern:**
```java
try (SSHClient ssh = new SSHClient()) {
    ssh.addHostKeyVerifier(new PromiscuousVerifier()); // for managed infra
    ssh.connect(server.getHostname(), server.getSshPort());
    ssh.authPassword(username, decryptedPassword);
    // or ssh.authPublickey(username, keyProvider);

    try (Session session = ssh.startSession()) {
        Session.Command cmd = session.exec(command);
        // read stdout/stderr
        cmd.join(timeout, TimeUnit.SECONDS);
    }
}
```

## Dependencies

- **servers** — to get server connection details
- **secrets** — to decrypt SSH credentials
- **audit** — to log command executions
