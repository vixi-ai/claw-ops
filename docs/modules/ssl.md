# SSL Module

The SSL module automates Let's Encrypt certificate provisioning on managed servers using certbot and nginx.

## How It Works

When SSL is provisioned for a server:

1. **Check/install nginx + certbot** — if not already installed, runs `sudo apt-get install nginx certbot python3-certbot-nginx`
2. **Upload nginx config** — creates a site config that reverse-proxies to a local port (default: 3000)
3. **Configure nginx** — removes the default site, enables the new site, tests and starts nginx
4. **Wait for DNS** — checks DNS resolution up to 6 times with 10-second intervals
5. **Run certbot** — `sudo certbot --nginx -d {domain} --non-interactive --agree-tos`
6. **Record certificate** — saves the certificate details in the database

## Prerequisites

Before provisioning SSL on a server:

- **Port 80 must be open** — Let's Encrypt uses HTTP-01 challenge
- **DNS must resolve** — the domain must point to the server's IP
- **SSH access with sudo** — certbot and nginx require root privileges
- **Ubuntu/Debian** — the install commands use `apt-get`

## Provisioning

### Single Server

```bash
curl -X POST http://localhost:8080/api/v1/ssl-certificates \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "serverId": "server-uuid",
    "domain": "myserver.example.com"
  }'
```

### All Servers

Provision SSL for all servers that don't have an active certificate:

```bash
curl -X POST http://localhost:8080/api/v1/ssl-certificates/provision-all \
  -H "Authorization: Bearer $TOKEN"
```

## Nginx Configuration

The generated nginx site config:

```nginx
server {
    listen 80;
    server_name {domain};

    location / {
        proxy_pass http://127.0.0.1:{target_port};
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_for_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

After certbot runs, it modifies this config to add SSL (port 443) and redirect HTTP to HTTPS.

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `SSL_ADMIN_EMAIL` | `admin@openclaw.com` | Email for Let's Encrypt account |
| `SSL_TARGET_PORT` | `3000` | Port nginx proxies to on the server |

## Troubleshooting

### "DNS not yet propagated"

The domain doesn't resolve to the server's IP yet. Wait a few minutes and retry. Check with:

```bash
dig +short your-domain.example.com
```

### "Certbot failed: Some challenges have failed"

- Port 80 is not open on the server's firewall or security group
- If using Cloudflare: disable "Always Use HTTPS" and "Automatic HTTPS Rewrites" for the domain during provisioning (they interfere with the HTTP-01 challenge)
- Check the remote certbot log: `sudo cat /var/log/letsencrypt/letsencrypt.log`

### "Nginx failed to start"

- The default nginx site was conflicting — ClawOps automatically removes it, but if the issue persists:
  - Check what's using port 80: `sudo ss -tlnp | grep :80`
  - Check nginx logs: `sudo journalctl -u nginx -n 20`

### "SFTP upload failed — Permission denied"

The upload uses a temp file and `sudo mv` to avoid permission issues. Ensure the SSH user has sudo access.

## Renewal

Certificates can be renewed manually:

```bash
curl -X POST http://localhost:8080/api/v1/ssl-certificates/$ID/renew \
  -H "Authorization: Bearer $TOKEN"
```

## Certificate Status Check

Verify the certificate status on the remote server:

```bash
curl -X POST http://localhost:8080/api/v1/ssl-certificates/$ID/check \
  -H "Authorization: Bearer $TOKEN"
```
