# Terminal Module

The terminal module provides a browser-based SSH terminal to any managed server using WebSockets.

## How It Works

1. **Request a session token**: `GET /api/v1/servers/{id}/ssh/session-token`
2. **Connect via WebSocket**: open a WebSocket to `ws://localhost:8080/ws/terminal?token={token}`
3. **Interactive session**: the WebSocket proxies stdin/stdout to an SSH session on the target server
4. **Auto-timeout**: sessions close after inactivity (default: 30 minutes)

## Session Token

The token is short-lived (default: 60 seconds) and single-use. It encodes the server ID and authenticated user.

```bash
curl http://localhost:8080/api/v1/servers/$SERVER_ID/ssh/session-token \
  -H "Authorization: Bearer $TOKEN"
```

**Response:**
```json
{
  "token": "eyJhbGci...",
  "expiresIn": 60
}
```

## WebSocket Protocol

- **Connect**: `ws://host:port/ws/terminal?token={token}`
- **Input**: send text frames with user keystrokes
- **Output**: receive text frames with terminal output
- **Resize**: send JSON `{"type":"resize","cols":120,"rows":40}` to resize the PTY

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `TERMINAL_SESSION_TIMEOUT` | `30` | Session timeout in minutes |
| `TERMINAL_MAX_SESSIONS` | `5` | Max concurrent sessions per user |
| `TERMINAL_TOKEN_EXPIRY` | `60` | Token validity in seconds |

## UI

The SSH & Terminal page (`/dev/ssh.html`) provides:

- A terminal emulator using xterm.js
- Server selection dropdown
- Automatic connection management
- Session info display (server name, connection time)

## Audit

Terminal sessions are logged in the audit trail:

- `TERMINAL_SESSION_REQUESTED` — when a session token is generated
- `TERMINAL_SESSION_OPENED` — when the WebSocket connection is established
- `TERMINAL_SESSION_CLOSED` — when the session ends
