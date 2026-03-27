# Task 10: WebSocket Terminal (Interactive SSH via Browser)

**Status:** DONE
**Module(s):** terminal, ssh, auth, common
**Priority:** HIGH
**Created:** 2026-03-11
**Completed:** 2026-03-11

## Description
Add WebSocket-based interactive terminal to the SSH dev page. Users select a server and open a live shell session streamed via WebSocket. The backend acts as a gateway: browser ↔ WebSocket ↔ backend ↔ SSH session. This replaces the need for a separate Terminal page — **delete `terminal.html`** and remove its card from `index.html`. The terminal is integrated directly into the SSH page as a tab alongside the existing command execution panel.

## Acceptance Criteria

### Dependencies (pom.xml)
- [ ] Add `spring-boot-starter-websocket` dependency
- [ ] No frontend JS library needed — use raw WebSocket API + basic terminal rendering (monospace pre element with ANSI-aware output). If xterm.js is desired later, it can be added, but for now keep it lightweight with a custom terminal div.

### WebSocketConfig
- [ ] `WebSocketConfig` in `terminal` package — implements `WebSocketConfigurer`
- [ ] Register WebSocket handler at `/ws/terminal`
- [ ] Set allowed origins to `*` (dev mode — can be locked down later)
- [ ] Add `WebSocketHandshakeInterceptor` that extracts `token` query param and validates it

### Session Token Endpoint
- [ ] `GET /api/v1/servers/{id}/ssh/session-token` on `ServerController`
- [ ] Generates a one-time, short-lived token (60 seconds expiry) tied to userId + serverId
- [ ] Token stored in a `ConcurrentHashMap<String, SessionTokenInfo>` in `TerminalSessionService`
- [ ] `SessionTokenInfo`: token, userId, serverId, expiresAt
- [ ] Returns `{ "token": "uuid-string", "expiresIn": 60 }`
- [ ] Audit log `TERMINAL_SESSION_REQUESTED`

### TerminalMessage / TerminalOutput models
- [ ] `TerminalMessage` (inbound from browser):
  ```json
  { "type": "INPUT | RESIZE | PING", "data": "ls -la\n", "cols": 120, "rows": 40 }
  ```
- [ ] `TerminalOutput` (outbound to browser):
  ```json
  { "type": "OUTPUT | ERROR | CLOSED", "data": "..." }
  ```

### TerminalWebSocketHandler
- [ ] Extends `TextWebSocketHandler`
- [ ] `afterConnectionEstablished(WebSocketSession session)`:
  - Extract token from query params
  - Validate token via `TerminalSessionService.validateAndConsumeToken(token)` — returns `SessionTokenInfo` or null
  - If invalid/expired → close with `CloseStatus(4001, "Invalid or expired session token")`
  - Load server from DB, open interactive SSH session via `SshService.openInteractiveSession(server)`
  - Store mapping: WebSocketSession → TerminalSession
  - Start a background thread/virtual thread to read SSH output and send to WebSocket
  - Audit log `TERMINAL_SESSION_OPENED` with serverId, userId
- [ ] `handleTextMessage(WebSocketSession, TextMessage)`:
  - Deserialize JSON to `TerminalMessage`
  - `INPUT` → write `data` bytes to SSH session's OutputStream
  - `RESIZE` → call `sshSession.resize(cols, rows)`
  - `PING` → respond with `{ "type": "PONG" }`
  - Update `lastActivityAt` on the terminal session
- [ ] `afterConnectionClosed(WebSocketSession, CloseStatus)`:
  - Close SSH session
  - Remove from active sessions
  - Audit log `TERMINAL_SESSION_CLOSED` with duration
- [ ] `handleTransportError(WebSocketSession, Throwable)`:
  - Log error, close SSH session, cleanup

### SshService.openInteractiveSession
- [ ] New method: `openInteractiveSession(Server server)` → `SshSession`
- [ ] Creates SSHClient, authenticates (reuses `createClient()`)
- [ ] Opens a session with PTY (pseudo-terminal):
  ```java
  Session session = ssh.startSession();
  session.allocatePTY("xterm-256color", 120, 40, 0, 0, Collections.emptyMap());
  session.startShell();
  ```
- [ ] Returns `SshSession` wrapper with: inputStream, outputStream, resize(), isConnected(), close()
- [ ] The `SSHClient` must NOT be auto-closed — it stays alive for the session duration
- [ ] `close()` must clean up both Session and SSHClient

### SshSession model
- [ ] In `ssh.model` package
- [ ] Fields: `sessionId` (UUID string), `sshClient` (SSHClient), `session` (Session), `shell` (Session.Shell)
- [ ] Methods:
  - `getInputStream()` → reads output from shell
  - `getOutputStream()` → writes input to shell
  - `resize(int cols, int rows)` → sends window-change signal
  - `isConnected()` → checks both SSH client and session
  - `close()` → closes shell, session, client in order

### TerminalSessionService
- [ ] Manages active terminal sessions and session tokens
- [ ] `generateSessionToken(UUID serverId, UUID userId)` → String token
- [ ] `validateAndConsumeToken(String token)` → `SessionTokenInfo` or null (single-use)
- [ ] `registerSession(String sessionId, TerminalSession)` — track active sessions
- [ ] `removeSession(String sessionId)` — cleanup
- [ ] `getActiveSessionCount(UUID userId)` → int (limit to 5 per user)
- [ ] `@Scheduled(fixedRate = 60000)` cleanup method: expire tokens older than 60s, close sessions inactive > 30 min

### TerminalSession model
- [ ] `sessionId` (String), `serverId` (UUID), `userId` (UUID), `sshSession` (SshSession), `createdAt` (Instant), `lastActivityAt` (Instant)

### SecurityConfig updates
- [ ] Add `/ws/**` to permitAll (WebSocket auth is handled by the handshake interceptor, not Spring Security filter chain)
- [ ] The JWT filter should skip WebSocket upgrade requests (or just let them through since `/ws/**` is permitted)

### AuditAction additions
- [ ] Add to `AuditAction` enum: `TERMINAL_SESSION_REQUESTED`, `TERMINAL_SESSION_OPENED`, `TERMINAL_SESSION_CLOSED`

### SSH Dev Page updates (ssh.html)
- [ ] Add two tabs at the top: **"Commands"** (existing) and **"Terminal"** (new)
- [ ] Terminal tab contains:
  - Server dropdown (reuse server list from left panel — clicking a server in the list selects it for both tabs)
  - "Connect" button → calls session-token endpoint, then opens WebSocket
  - Terminal display div: black background, monospace font, scrollable, captures keyboard input
  - "Disconnect" button (visible when connected)
  - Connection status indicator (Disconnected / Connecting / Connected)
- [ ] Terminal rendering:
  - All keyboard input captured and sent as `INPUT` messages
  - Output received via WebSocket displayed in terminal div
  - Handle basic ANSI escape codes for colors (or just strip them for now — full xterm.js can come later)
  - Auto-scroll to bottom on new output
  - Handle terminal resize: detect container size, send `RESIZE` message
- [ ] On tab switch, don't disconnect — sessions persist across tab switches
- [ ] On page unload, send WebSocket close

### Delete terminal.html
- [ ] Delete `src/main/resources/static/dev/terminal.html`
- [ ] Remove Terminal card from `index.html` dashboard
- [ ] Update SSH card description in `index.html` to mention terminal capability

### application.properties
- [ ] Add terminal config properties:
  ```properties
  terminal.session-timeout-minutes=30
  terminal.max-sessions-per-user=5
  terminal.token-expiry-seconds=60
  ```

## Implementation Notes

### WebSocket authentication flow
1. User clicks "Connect" on SSH page with a server selected
2. Frontend calls `GET /api/v1/servers/{serverId}/ssh/session-token` (authenticated with JWT)
3. Backend generates a UUID token, stores it with userId+serverId+expiry in memory map
4. Frontend receives token, opens `ws://host/ws/terminal?token={token}&serverId={serverId}`
5. `WebSocketHandshakeInterceptor` validates the token (single-use, not expired)
6. `TerminalWebSocketHandler.afterConnectionEstablished()` opens SSH session
7. Bidirectional streaming begins

### Why not pass JWT directly in WebSocket?
WebSocket connections pass auth via query params (no custom headers in browser WebSocket API). Putting the JWT in the URL would expose it in server logs, browser history, and proxy logs. A short-lived, single-use session token is safer.

### PTY allocation with sshj
```java
Session session = sshClient.startSession();
session.allocatePTY("xterm-256color", cols, rows, 0, 0, Collections.emptyMap());
Session.Shell shell = session.startShell();
// shell.getInputStream() → read output
// shell.getOutputStream() → write input
```

### Terminal resize signal
sshj doesn't have a direct `resize()` on Shell. Workaround: store the Session reference and use `session.sendWindowChange(cols, rows)` — but sshj Session doesn't expose this directly. Alternative: close and reopen the session (bad UX), or use a lower-level channel approach. Check sshj API for `PTYMode` or `WindowChangeRequest`.

Actually, sshj's `Session` has no public `sendWindowChange`. The recommended approach is to access the underlying channel:
```java
// session.getSubsystem() or use the Shell's underlying channel
// May need to cast or use reflection — document this limitation
```
If sshj doesn't support window-change natively, document it as a known limitation and skip resize for now. Can switch to Apache Mina SSHD later if needed.

### Output streaming thread
```java
Thread.ofVirtual().name("terminal-" + sessionId).start(() -> {
    try {
        byte[] buffer = new byte[4096];
        int bytesRead;
        InputStream in = sshSession.getInputStream();
        while ((bytesRead = in.read(buffer)) != -1) {
            String output = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
            TerminalOutput msg = new TerminalOutput("OUTPUT", output);
            webSocketSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
        }
    } catch (IOException e) {
        // Connection closed or error — send CLOSED message
        try {
            TerminalOutput msg = new TerminalOutput("CLOSED", "Session ended");
            webSocketSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
        } catch (IOException ignored) {}
    }
});
```

### Simple terminal rendering (no xterm.js)
```javascript
const termEl = document.getElementById('terminal');
termEl.style.cssText = 'background:#1a1a2e;color:#e0e0e0;font-family:monospace;font-size:13px;padding:12px;height:500px;overflow-y:auto;white-space:pre-wrap;word-break:break-all;';

// On output message:
termEl.textContent += data; // append raw text
termEl.scrollTop = termEl.scrollHeight;

// On key press:
document.addEventListener('keydown', (e) => {
    if (!terminalFocused) return;
    // Map key events to terminal input
    let data = '';
    if (e.key.length === 1) data = e.key; // printable char
    else if (e.key === 'Enter') data = '\r';
    else if (e.key === 'Backspace') data = '\x7f';
    else if (e.key === 'Tab') { data = '\t'; e.preventDefault(); }
    else if (e.key === 'ArrowUp') data = '\x1b[A';
    else if (e.key === 'ArrowDown') data = '\x1b[B';
    else if (e.key === 'ArrowRight') data = '\x1b[C';
    else if (e.key === 'ArrowLeft') data = '\x1b[D';
    // Ctrl+C
    if (e.ctrlKey && e.key === 'c') data = '\x03';
    if (e.ctrlKey && e.key === 'd') data = '\x04';

    if (data) ws.send(JSON.stringify({ type: 'INPUT', data }));
});
```

### Recommended implementation order
1. Add `spring-boot-starter-websocket` to pom.xml
2. Add `AuditAction` entries (TERMINAL_SESSION_REQUESTED, OPENED, CLOSED)
3. `SshSession` model in ssh.model
4. `SshService.openInteractiveSession()` method
5. `TerminalSession`, `SessionTokenInfo` models in terminal
6. `TerminalMessage`, `TerminalOutput` models in terminal
7. `TerminalSessionService` (token management, session tracking, cleanup)
8. `WebSocketConfig` + `WebSocketHandshakeInterceptor`
9. `TerminalWebSocketHandler`
10. Session token endpoint on `ServerController`
11. SecurityConfig update for `/ws/**`
12. application.properties terminal config
13. Update ssh.html with terminal tab
14. Delete terminal.html, update index.html
15. Update architecture logs (terminal, ssh)

### Package structure
```
com.openclaw.manager.openclawserversmanager/
├── ssh/
│   ├── model/
│   │   └── SshSession.java          (NEW)
│   └── service/SshService.java       (MODIFIED — add openInteractiveSession)
└── terminal/
    ├── config/
    │   ├── WebSocketConfig.java
    │   └── WebSocketHandshakeInterceptor.java
    ├── handler/TerminalWebSocketHandler.java
    ├── model/
    │   ├── TerminalMessage.java
    │   ├── TerminalOutput.java
    │   ├── TerminalSession.java
    │   └── SessionTokenInfo.java
    └── service/TerminalSessionService.java
```

## Files Modified
- `pom.xml` — added spring-boot-starter-websocket dependency
- `OpenclawServersManagerApplication.java` — added @EnableScheduling
- `AuditAction.java` — added TERMINAL_SESSION_REQUESTED
- `SecurityConfig.java` — added /ws/** permitAll()
- `application.properties` — added terminal.* config properties
- `ServerController.java` — added GET /{id}/ssh/session-token endpoint, injected TerminalSessionService + AuditService
- `ServerService.java` — added public getServerEntity(UUID) method
- `SshService.java` — added openInteractiveSession(Server, cols, rows) method
- `ssh/model/SshSession.java` — NEW: interactive SSH session wrapper
- `terminal/config/TerminalConfig.java` — NEW: terminal config properties
- `terminal/config/WebSocketConfig.java` — NEW: WebSocket endpoint registration
- `terminal/handler/TerminalWebSocketHandler.java` — NEW: core WebSocket handler
- `terminal/model/SessionTokenInfo.java` — NEW: session token model
- `terminal/model/TerminalSession.java` — NEW: active session model
- `terminal/model/TerminalMessage.java` — NEW: inbound message model
- `terminal/model/TerminalOutput.java` — NEW: outbound message model
- `terminal/service/TerminalSessionService.java` — NEW: token + session management
- `static/dev/ssh.html` — rewritten with Commands + Terminal tabs
- `static/dev/index.html` — removed Terminal card, updated SSH card
- `static/dev/terminal.html` — DELETED
