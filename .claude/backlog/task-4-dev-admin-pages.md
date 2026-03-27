# Task 4: Development Admin Pages (Static HTML)

**Status:** DONE
**Module(s):** common, auth, users
**Priority:** MEDIUM
**Created:** 2026-03-11
**Completed:** 2026-03-11

## Description
Create static HTML pages in `src/main/resources/static/` for managing the system during development. These are simple, self-contained HTML pages (no frontend framework) that call the existing REST API using fetch(). Each module gets its own page. Pages are only intended for development use — not a production UI.

## Acceptance Criteria

### Core
- [ ] Landing page at `/dev/index.html` — dashboard with links to all module pages
- [ ] Shared `dev/common.js` — API client helper (fetch wrapper with JWT token management, base URL, error handling)
- [ ] Shared `dev/common.css` — minimal clean styling (no framework, just CSS)
- [ ] JWT token stored in localStorage after login, attached to all API requests as Bearer token
- [ ] Login page that must be completed before accessing other pages

### Module Pages
- [ ] `/dev/login.html` — login form (email + password), stores JWT, redirects to dashboard
- [ ] `/dev/users.html` — list all users (table), create user form, edit user, change password, disable/delete user
- [ ] Placeholder pages for future modules (just title + "Coming soon" + back link):
  - [ ] `/dev/servers.html`
  - [ ] `/dev/secrets.html`
  - [ ] `/dev/ssh.html`
  - [ ] `/dev/deployments.html`
  - [ ] `/dev/templates.html`
  - [ ] `/dev/domains.html`
  - [ ] `/dev/audit.html`
  - [ ] `/dev/terminal.html`

### Security
- [ ] SecurityConfig updated: permit `/dev/**` only when `dev` profile is active (or always permit static resources in dev)
- [ ] Pages check for JWT on load — redirect to login if not present
- [ ] Logout button that clears token and redirects to login

## Implementation Notes

### File structure
```
src/main/resources/static/dev/
├── index.html          — Dashboard with module links
├── login.html          — Login form
├── users.html          — User management CRUD
├── servers.html        — Placeholder
├── secrets.html        — Placeholder
├── ssh.html            — Placeholder
├── deployments.html    — Placeholder
├── templates.html      — Placeholder
├── domains.html        — Placeholder
├── audit.html          — Placeholder
├── terminal.html       — Placeholder
├── common.js           — Shared API client + auth helpers
└── common.css          — Shared styles
```

### common.js should provide
```javascript
// Base URL for API
const API_BASE = '/api/v1';

// Get stored token
function getToken() { return localStorage.getItem('access_token'); }

// Authenticated fetch wrapper
async function apiFetch(path, options = {}) { ... }

// Login helper
async function login(email, password) { ... }

// Logout helper
function logout() { localStorage.clear(); window.location = '/dev/login.html'; }

// Auth guard — call on page load
function requireAuth() { if (!getToken()) window.location = '/dev/login.html'; }

// Render error messages
function showError(message) { ... }
function showSuccess(message) { ... }
```

### users.html functionality
- Table listing all users (id, email, username, role, enabled, createdAt)
- "Create User" form (email, username, password, role dropdown)
- Edit user inline or modal (email, username, role)
- Change password button per user
- Disable/Enable toggle per user
- Delete button with confirmation
- Pagination controls
- All operations call the existing `/api/v1/users/**` endpoints

### SecurityConfig update
Add `/dev/**` to permitted paths. Since these are static files served by Spring Boot's default resource handler, they just need to bypass auth:
```java
.requestMatchers("/dev/**").permitAll()
```

Consider restricting to dev profile only using a `@Profile("dev")` conditional config, or accept that static files in dev/ are harmless since the API itself is still protected by JWT.

### Design approach
- Plain HTML + vanilla JS (no React, no Vue, no build tools)
- Clean, functional UI — tables, forms, buttons
- Use CSS grid/flexbox for layout
- Dark theme preferred (matches developer tools aesthetic)
- Each page is fully self-contained (includes common.js and common.css via script/link tags)

## Files Modified

### Java (modified)
- `src/main/java/.../auth/config/SecurityConfig.java` — added `.requestMatchers("/dev/**").permitAll()`

### Static resources (all new)
- `src/main/resources/static/dev/common.css` — dark theme shared styles
- `src/main/resources/static/dev/common.js` — API client with JWT management, auto-refresh, auth guard
- `src/main/resources/static/dev/login.html` — login form
- `src/main/resources/static/dev/index.html` — dashboard with module cards
- `src/main/resources/static/dev/users.html` — full CRUD (table, modals, pagination)
- `src/main/resources/static/dev/servers.html` — placeholder
- `src/main/resources/static/dev/secrets.html` — placeholder
- `src/main/resources/static/dev/ssh.html` — placeholder
- `src/main/resources/static/dev/deployments.html` — placeholder
- `src/main/resources/static/dev/templates.html` — placeholder
- `src/main/resources/static/dev/domains.html` — placeholder
- `src/main/resources/static/dev/audit.html` — placeholder
- `src/main/resources/static/dev/terminal.html` — placeholder
