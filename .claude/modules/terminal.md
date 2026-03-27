# Terminal Module (WebSocket)

## Purpose

Provides browser-based terminal access to remote servers via WebSocket. The backend acts as a gateway: the browser connects via WebSocket, the backend opens an SSH session, and streams I/O bidirectionally.

## Package

`com.openclaw.manager.openclawserversmanager.terminal`

## Components

### Config: `WebSocketConfig`

- Registers WebSocket endpoint at `/ws/terminal`
- Configures allowed origins
- Handshake interceptor for JWT validation

### Handler: `TerminalWebSocketHandler`

Implements `WebSocketHandler` (or `TextWebSocketHandler`):

- `afterConnectionEstablished(WebSocketSession)` — validates session token, opens SSH session, starts I/O streaming
- `handleTextMessage(WebSocketSession, TextMessage)` — routes incoming messages by type
- `afterConnectionClosed(WebSocketSession, CloseStatus)` — cleans up SSH session, logs to audit

### Model: `TerminalMessage` (inbound)

```json
{
  "type": "INPUT | RESIZE | PING",
  "sessionId": "uuid",
  "data": "ls -la\n",
  "cols": 120,
  "rows": 40
}
```

### Model: `TerminalOutput` (outbound)

```json
{
  "type": "OUTPUT | ERROR | CLOSED",
  "sessionId": "uuid",
  "data": "..."
}
```

### Service: `TerminalSessionService`

- `createSession(UUID serverId, UUID userId)` → `TerminalSession` — opens SSH session, assigns session ID
- `getSession(String sessionId)` → `TerminalSession`
- `closeSession(String sessionId)` — closes SSH session, removes from active sessions
- `getActiveSessionsForUser(UUID userId)` → `List<TerminalSession>`
- `cleanupExpiredSessions()` — scheduled cleanup of timed-out sessions

### Model: `TerminalSession`

| Field | Type | Description |
|-------|------|-------------|
| sessionId | String | Unique session identifier |
| serverId | UUID | Target server |
| userId | UUID | Authenticated user |
| sshSession | SshSession | Underlying SSH connection |
| createdAt | Instant | Session start time |
| lastActivityAt | Instant | Last I/O timestamp |

## API Endpoints

| Protocol | Path | Auth | Description |
|----------|------|------|-------------|
| WebSocket | `/ws/terminal` | Session token (query param) | Interactive terminal |

Connection flow:
1. Client calls `GET /api/v1/servers/{id}/ssh/session-token` (in SSH module) to get a one-time token
2. Client connects to `ws://host/ws/terminal?token={sessionToken}&serverId={id}`
3. Backend validates token, opens SSH session, begins streaming

## Business Rules

- Each session must have a **unique session ID**
- Session timeout: close after N minutes of inactivity (configurable, default 30 min)
- **Limit active sessions per user** (configurable, default 5)
- All terminal sessions must be logged to audit (open, close, duration)
- Session token is single-use and short-lived (60 seconds)
- On WebSocket disconnect, SSH session must be closed and cleaned up immediately
- Terminal resize messages must propagate to the SSH session (PTY window change)

## Security Considerations

- WebSocket connection must be authenticated via session token (not raw JWT in query params for security)
- Session tokens are generated server-side, tied to a specific user and server
- Input sanitization is NOT done here (the user has shell access by design) — but all sessions are audit-logged
- Rate limiting on session creation to prevent abuse
- Maximum session duration limit (configurable)

## Dependencies

- **ssh** — to open interactive SSH sessions
- **servers** — to resolve server details
- **secrets** — indirectly via SSH module for credentials
- **auth** — to validate user identity
- **audit** — to log session lifecycle events
