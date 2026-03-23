# Task 9: SSH Module (Command Execution & Connection Testing)

**Status:** DONE
**Module(s):** ssh, servers, common
**Priority:** HIGH
**Created:** 2026-03-11
**Completed:** 2026-03-11

## Description
Implement the SSH module — the bridge between the control plane and actual remote infrastructure. Provides SSH connection testing, command execution, and file transfer (SFTP) capabilities. This task wires up the `testConnection` stub from task-8 to real SSH connectivity, adds the command execution endpoint, and lays the groundwork for the interactive terminal (task-10+). Uses the **sshj** library for SSH operations.

## Acceptance Criteria

### Dependencies (pom.xml)
- [ ] Add `com.hierynomus:sshj` dependency (latest stable, e.g., `0.39.0`)
- [ ] sshj pulls in Bouncy Castle and SLF4J transitively — no extra deps needed

### SshConnectionException
- [ ] Add `SshConnectionException` to common exception package (extends `RuntimeException`)
- [ ] `GlobalExceptionHandler` maps `SshConnectionException` to **502 Bad Gateway** (remote server issue, not our fault)

### SshConfig (Configuration Properties)
- [ ] `SshConfig` class with `@ConfigurationProperties(prefix = "ssh")`
- [ ] `connectionTimeout` — connection timeout in ms (default: `10000`)
- [ ] `commandTimeout` — command execution timeout in seconds (default: `60`)
- [ ] `strictHostKeyChecking` — boolean (default: `false` for managed infra)
- [ ] `maxOutputSize` — max stdout/stderr size in bytes (default: `1048576` = 1MB)
- [ ] Add defaults to `application.properties`:
  ```properties
  ssh.connection-timeout=10000
  ssh.command-timeout=60
  ssh.strict-host-key-checking=false
  ssh.max-output-size=1048576
  ```

### Models (not JPA entities — plain POJOs/records)
- [ ] `CommandResult` record — `exitCode` (int), `stdout` (String), `stderr` (String), `durationMs` (long)
- [ ] `TestConnectionResult` record — `success` (boolean), `message` (String), `latencyMs` (long)

### DTOs
- [ ] `ExecuteCommandRequest` — `command` (`@NotBlank @Size(max=4096)`), `timeoutSeconds` (optional, `@Min(1) @Max(300)`)
- [ ] `CommandResponse` — `exitCode`, `stdout`, `stderr`, `durationMs`, `serverId`, `serverName`

### SshService
- [ ] `SshService` with constructor injection of `SecretService`, `SshConfig`, `AuditService`
- [ ] **`testConnection(Server server)`** → `TestConnectionResult`
  - Creates SSHClient, connects to server hostname:port with timeout
  - Authenticates using password or private key (resolved via `SecretService.decryptSecret(credentialId)`)
  - On success: returns `{ success: true, message: "Connected successfully", latencyMs: <ms> }`
  - On failure: returns `{ success: false, message: "<error description>", latencyMs: -1 }`
  - Must NEVER throw — always returns a result (catches all exceptions internally)
  - Closes connection after test
- [ ] **`executeCommand(Server server, String command, long timeoutSeconds)`** → `CommandResult`
  - Connects + authenticates (same as testConnection)
  - Opens session, executes command, reads stdout + stderr (size-limited to `maxOutputSize`)
  - Waits for command completion with timeout
  - Returns `CommandResult` with exit code, output, duration
  - Throws `SshConnectionException` if connection fails
  - Throws `SshConnectionException` if command times out
- [ ] **`executeCommand(Server server, String command)`** — overload using default timeout from `SshConfig`
- [ ] **`uploadFile(Server server, byte[] content, String remotePath)`** — SFTP upload
  - Validates remotePath (no path traversal: must not contain `..`)
  - Connects, opens SFTP channel, writes content to remote path
  - Throws `SshConnectionException` on failure
- [ ] **`downloadFile(Server server, String remotePath)`** → `byte[]` — SFTP download
  - Validates remotePath (no path traversal)
  - Connects, opens SFTP channel, reads file content
  - Size-limited to `maxOutputSize`
  - Throws `SshConnectionException` on failure
- [ ] Private helper: `createClient(Server server)` → authenticated `SSHClient`
  - Handles both PASSWORD and PRIVATE_KEY auth types
  - For PASSWORD: `ssh.authPassword(username, decryptedPassword)`
  - For PRIVATE_KEY: `ssh.authPublickey(username, keyProvider)` using `OpenSSHKeyV1KeyFile` or `PKCS8KeyFile`
  - Uses `PromiscuousVerifier` when `strictHostKeyChecking=false`
  - Sets connection timeout from `SshConfig`
- [ ] All credentials resolved via `SecretService.decryptSecret(server.getCredentialId())`
- [ ] Credentials NEVER logged — not in info, debug, error, or exception messages

### Wire Up ServerService.testConnection
- [ ] Replace the stub in `ServerService.testConnection(UUID)` with real SSH call
- [ ] Inject `SshService` into `ServerService`
- [ ] Load the `Server` entity (not just DTO), pass to `SshService.testConnection()`
- [ ] Update server `status` field based on result: success → `ONLINE`, failure → `OFFLINE` or `ERROR`
- [ ] Save updated status to DB
- [ ] Audit log `SERVER_CONNECTION_TESTED` with result details
- [ ] Return `TestConnectionResponse` (existing DTO from task-8)

### SSH Command Endpoint
- [ ] Add to `ServerController`: `POST /api/v1/servers/{id}/ssh/command`
- [ ] Accepts `@Valid @RequestBody ExecuteCommandRequest`
- [ ] Loads server, calls `SshService.executeCommand()`, returns `CommandResponse`
- [ ] Audit log `SSH_COMMAND_EXECUTED` with command text (NOT credentials), exit code, server name
- [ ] Swagger `@Operation` annotation

### Security
- [ ] Command execution endpoint requires authentication (not ADMIN-only — DEVOPS can execute too)
- [ ] Audit every command execution — the command text is logged but credentials are never included
- [ ] Output size is limited to prevent memory exhaustion from malicious/runaway commands

### Dev Admin Page
- [ ] Update `/dev/ssh.html` from placeholder to functional page:
  - Server dropdown (populated from `/api/v1/servers`)
  - Command input textarea
  - Timeout input (optional, defaults to 60s)
  - "Execute" button
  - Output panel showing: exit code (color-coded: 0=green, other=red), stdout (pre-formatted), stderr (pre-formatted, red), duration
  - Command history (localStorage, last 20 commands)
  - Test connection button per server (reuses existing endpoint)

## Implementation Notes

### sshj dependency
```xml
<dependency>
    <groupId>com.hierynomus</groupId>
    <artifactId>sshj</artifactId>
    <version>0.39.0</version>
</dependency>
```

### sshj connection pattern
```java
private SSHClient createClient(Server server) throws IOException {
    SSHClient ssh = new SSHClient();

    if (!sshConfig.isStrictHostKeyChecking()) {
        ssh.addHostKeyVerifier(new PromiscuousVerifier());
    }

    ssh.setConnectTimeout(sshConfig.getConnectionTimeout());
    ssh.connect(server.getHostname(), server.getSshPort());

    String decryptedCredential = secretService.decryptSecret(server.getCredentialId());

    switch (server.getAuthType()) {
        case PASSWORD -> ssh.authPassword(server.getSshUsername(), decryptedCredential);
        case PRIVATE_KEY -> {
            KeyProvider keyProvider = ssh.loadKeys(decryptedCredential, null, null);
            ssh.authPublickey(server.getSshUsername(), keyProvider);
        }
    }

    return ssh;
}
```

### Command execution pattern
```java
public CommandResult executeCommand(Server server, String command, long timeoutSeconds) {
    long startTime = System.currentTimeMillis();
    try (SSHClient ssh = createClient(server)) {
        try (Session session = ssh.startSession()) {
            Session.Command cmd = session.exec(command);

            String stdout = readStream(cmd.getInputStream(), sshConfig.getMaxOutputSize());
            String stderr = readStream(cmd.getErrorStream(), sshConfig.getMaxOutputSize());

            cmd.join(timeoutSeconds, TimeUnit.SECONDS);

            long durationMs = System.currentTimeMillis() - startTime;
            Integer exitCode = cmd.getExitStatus();

            return new CommandResult(
                exitCode != null ? exitCode : -1,
                stdout,
                stderr,
                durationMs
            );
        }
    } catch (ConnectionException e) {
        throw new SshConnectionException("Failed to connect to %s:%d — %s"
            .formatted(server.getHostname(), server.getSshPort(), e.getMessage()));
    } catch (TransportException e) {
        throw new SshConnectionException("SSH transport error on %s — %s"
            .formatted(server.getHostname(), e.getMessage()));
    }
}
```

### Stream reading with size limit
```java
private String readStream(InputStream is, int maxBytes) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int totalRead = 0;
    int bytesRead;
    while ((bytesRead = is.read(buffer)) != -1) {
        if (totalRead + bytesRead > maxBytes) {
            baos.write(buffer, 0, maxBytes - totalRead);
            break; // truncate
        }
        baos.write(buffer, 0, bytesRead);
        totalRead += bytesRead;
    }
    return baos.toString(StandardCharsets.UTF_8);
}
```

### Private key loading
sshj supports loading private keys from a string. For OpenSSH format keys:
```java
KeyProvider keyProvider = new OpenSSHKeyV1KeyFile();
keyProvider.init(new StringReader(decryptedPrivateKey));
// or for passphrase-protected keys:
// keyProvider.init(new StringReader(decryptedPrivateKey), passwordFinder);
```

For PEM/PKCS8 keys, sshj also handles them automatically via `ssh.loadKeys()`.

### Handling server without credentials
If `server.getCredentialId()` is null (e.g., secret was deleted via ON DELETE SET NULL), `testConnection` should return `{ success: false, message: "No credential configured for this server" }` and `executeCommand` should throw `SshConnectionException("No credential configured")`.

### Recommended implementation order
1. `SshConnectionException` (common module)
2. `GlobalExceptionHandler` update for 502
3. sshj dependency in `pom.xml`
4. `SshConfig` configuration properties + `application.properties` defaults
5. `CommandResult`, `TestConnectionResult` records
6. `ExecuteCommandRequest`, `CommandResponse` DTOs
7. `SshService` (connection, test, execute, upload, download)
8. Wire `ServerService.testConnection()` to `SshService`
9. Add `POST /{id}/ssh/command` to `ServerController`
10. Update `ssh.html` dev page
11. Update architecture logs (ssh + servers)

### Package structure
```
com.openclaw.manager.openclawserversmanager/
├── common/
│   └── exception/SshConnectionException.java
└── ssh/
    ├── config/SshConfig.java
    ├── dto/ExecuteCommandRequest.java
    ├── dto/CommandResponse.java
    ├── model/CommandResult.java
    ├── model/TestConnectionResult.java
    └── service/SshService.java
```

Note: SSH endpoints live on `ServerController` (`/api/v1/servers/{id}/ssh/...`) since they're server-scoped operations. No separate `SshController` needed.

## Files Modified
- `pom.xml` — added sshj 0.39.0 dependency
- `src/main/resources/application.properties` — added ssh.* config properties
- `src/main/java/.../common/exception/SshConnectionException.java` — NEW
- `src/main/java/.../common/exception/GlobalExceptionHandler.java` — added 502 handler
- `src/main/java/.../ssh/config/SshConfig.java` — NEW
- `src/main/java/.../ssh/model/CommandResult.java` — NEW
- `src/main/java/.../ssh/model/TestConnectionResult.java` — NEW
- `src/main/java/.../ssh/dto/ExecuteCommandRequest.java` — NEW
- `src/main/java/.../ssh/dto/CommandResponse.java` — NEW
- `src/main/java/.../ssh/service/SshService.java` — NEW (core SSH service)
- `src/main/java/.../servers/service/ServerService.java` — wired SshService, real testConnection, new executeCommand
- `src/main/java/.../servers/controller/ServerController.java` — added POST /{id}/ssh/command
- `src/main/resources/static/dev/ssh.html` — functional SSH dev page
- `src/main/resources/static/dev/index.html` — SSH badge set to Active
- `.claude/architecture/ssh.md` — architecture log updated
