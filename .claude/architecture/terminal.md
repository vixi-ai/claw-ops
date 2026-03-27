# Terminal — Architecture Log

Living documentation of implemented code. Agents MUST append here after writing code for this module.

## Implemented Components

### TerminalConfig
- **File(s):** `src/main/java/.../terminal/config/TerminalConfig.java`
- **Type:** config
- **Description:** @ConfigurationProperties(prefix = "terminal") — sessionTimeoutMinutes (30), maxSessionsPerUser (5), tokenExpirySeconds (60). All configurable via env vars.
- **Dependencies:** None
- **Date:** 2026-03-11

### SessionTokenInfo
- **File(s):** `src/main/java/.../terminal/model/SessionTokenInfo.java`
- **Type:** model (record)
- **Description:** Holds one-time session token data: token string, userId, serverId, expiresAt. Has isExpired() helper.
- **Date:** 2026-03-11

### TerminalSession
- **File(s):** `src/main/java/.../terminal/model/TerminalSession.java`
- **Type:** model
- **Description:** Active terminal session: sessionId, serverId, userId, SshSession reference, createdAt, lastActivityAt. touch() updates activity timestamp.
- **Dependencies:** SshSession (ssh module)
- **Date:** 2026-03-11

### TerminalMessage / TerminalOutput
- **File(s):** `src/main/java/.../terminal/model/TerminalMessage.java`, `TerminalOutput.java`
- **Type:** model (records)
- **Description:** TerminalMessage (inbound): type (INPUT/RESIZE/PING), data, cols, rows. TerminalOutput (outbound): type (OUTPUT/ERROR/CLOSED/PONG), data.
- **Date:** 2026-03-11

### TerminalSessionService
- **File(s):** `src/main/java/.../terminal/service/TerminalSessionService.java`
- **Type:** service
- **Description:** Manages session tokens (ConcurrentHashMap) and active terminal sessions. generateSessionToken() creates UUID token with expiry. validateAndConsumeToken() is single-use. canOpenSession() enforces per-user limit. @Scheduled cleanup every 60s removes expired tokens and inactive sessions (30min timeout).
- **Dependencies:** TerminalConfig
- **Date:** 2026-03-11

### WebSocketConfig
- **File(s):** `src/main/java/.../terminal/config/WebSocketConfig.java`
- **Type:** config
- **Description:** @EnableWebSocket — registers TerminalWebSocketHandler at /ws/terminal with allowedOrigins("*").
- **Dependencies:** TerminalWebSocketHandler
- **Date:** 2026-03-11

### TerminalWebSocketHandler
- **File(s):** `src/main/java/.../terminal/handler/TerminalWebSocketHandler.java`
- **Type:** handler (WebSocket)
- **Description:** Core WebSocket handler. afterConnectionEstablished: validates token, checks session limit, loads server, opens SSH interactive session, starts virtual thread for output streaming. handleTextMessage: routes INPUT (write to SSH), RESIZE (logged but unsupported by sshj), PING/PONG. afterConnectionClosed: closes SSH, removes session, audit logs with duration. Thread-safe sendMessage with synchronized block. Audit logging for TERMINAL_SESSION_OPENED and TERMINAL_SESSION_CLOSED.
- **Dependencies:** TerminalSessionService, SshService, ServerService, AuditService, ObjectMapper
- **Date:** 2026-03-11

### Session Token Endpoint
- **File(s):** `src/main/java/.../servers/controller/ServerController.java` (modified)
- **Type:** controller endpoint
- **Description:** GET /api/v1/servers/{id}/ssh/session-token — generates one-time session token for WebSocket terminal. Validates server exists, audit logs TERMINAL_SESSION_REQUESTED.
- **Dependencies:** TerminalSessionService, AuditService
- **Date:** 2026-03-11

### SecurityConfig WebSocket
- **File(s):** `src/main/java/.../auth/config/SecurityConfig.java` (modified)
- **Type:** config (modified)
- **Description:** Added /ws/** to permitAll() — WebSocket auth handled by token validation in handler, not Spring Security filter chain.
- **Date:** 2026-03-11

### SSH Dev Page Terminal Tab
- **File(s):** `src/main/resources/static/dev/ssh.html` (rewritten)
- **Type:** frontend (dev page)
- **Description:** Added tabbed UI: "Commands" (existing) + "Terminal" (new). Terminal tab: connect/disconnect buttons, status indicator (Disconnected/Connecting/Connected), dark terminal display div (500px), keyboard capture for all keys + Ctrl shortcuts, paste support, auto-scroll. Sessions persist across tab switches. WebSocket cleanup on page unload.
- **Date:** 2026-03-11

### Deleted terminal.html
- **File(s):** `src/main/resources/static/dev/terminal.html` (deleted)
- **Type:** frontend (removed)
- **Description:** Placeholder page removed. Terminal functionality integrated into ssh.html.
- **Date:** 2026-03-11

### Dashboard Updated
- **File(s):** `src/main/resources/static/dev/index.html` (modified)
- **Type:** frontend (modified)
- **Description:** Removed Terminal card. Updated SSH card to "SSH & Terminal" with description "SSH commands, SFTP, and interactive terminal".
- **Date:** 2026-03-11
