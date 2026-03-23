const API_BASE = '/api/v1';

function getToken() {
    return localStorage.getItem('access_token');
}

function getRefreshToken() {
    return localStorage.getItem('refresh_token');
}

function setTokens(accessToken, refreshToken) {
    localStorage.setItem('access_token', accessToken);
    localStorage.setItem('refresh_token', refreshToken);
}

function clearTokens() {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
}

async function apiFetch(path, options = {}) {
    const token = getToken();
    const headers = { 'Content-Type': 'application/json', ...options.headers };
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const response = await fetch(`${API_BASE}${path}`, { ...options, headers });

    if (response.status === 401 || response.status === 403) {
        const refreshed = await tryRefresh();
        if (refreshed) {
            headers['Authorization'] = `Bearer ${getToken()}`;
            return fetch(`${API_BASE}${path}`, { ...options, headers });
        }
        logout();
        return response;
    }

    return response;
}

async function tryRefresh() {
    const refreshToken = getRefreshToken();
    if (!refreshToken) return false;

    try {
        const res = await fetch(`${API_BASE}/auth/refresh`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken })
        });
        if (!res.ok) return false;
        const data = await res.json();
        setTokens(data.accessToken, data.refreshToken);
        return true;
    } catch {
        return false;
    }
}

async function login(email, password) {
    const res = await fetch(`${API_BASE}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
    });

    if (!res.ok) {
        const err = await res.json();
        throw new Error(err.message || 'Login failed');
    }

    const data = await res.json();
    setTokens(data.accessToken, data.refreshToken);
    return data;
}

function logout() {
    const refreshToken = getRefreshToken();
    if (refreshToken && getToken()) {
        apiFetch('/auth/logout', {
            method: 'POST',
            body: JSON.stringify({ refreshToken })
        }).catch(() => {});
    }
    clearTokens();
    window.location.href = '/dev/login.html';
}

function requireAuth() {
    if (!getToken()) {
        window.location.href = '/dev/login.html';
    }
}

function showAlert(id, message, type) {
    const el = document.getElementById(id);
    if (!el) return;
    el.className = `alert alert-${type} show`;
    el.textContent = message;
    if (type === 'success') setTimeout(() => el.classList.remove('show'), 3000);
}

function showError(message, id = 'alert') { showAlert(id, message, 'error'); }
function showSuccess(message, id = 'alert') { showAlert(id, message, 'success'); }

function hideAlert(id = 'alert') {
    const el = document.getElementById(id);
    if (el) el.classList.remove('show');
}

function formatDate(iso) {
    if (!iso) return '-';
    return new Date(iso).toLocaleString();
}

function headerHTML(title) {
    return `
    <div class="header">
        <h1><a href="/dev/index.html"><span>OpenClaw</span> Control Plane</a> &mdash; ${title}</h1>
        <div class="header-actions">
            <a href="/dev/index.html">Dashboard</a>
            <a href="/swagger-ui.html" target="_blank">Swagger</a>
            <button class="btn btn-ghost btn-sm" onclick="logout()">Logout</button>
        </div>
    </div>`;
}
