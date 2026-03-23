# Servers Module

The servers module manages your fleet of SSH-accessible machines. Each server entry stores connection details, authentication configuration, and environment metadata.

## Concepts

- **Server**: a remote machine you manage via SSH (cloud VM, bare metal, container host)
- **Auth Type**: how ClawOps authenticates — `KEY` (SSH private key) or `PASSWORD`
- **Credential**: a reference to an encrypted secret (SSH key or password) stored in the secrets module
- **Environment**: a tag like `production`, `staging`, `development`
- **Status**: connection status — `UNKNOWN`, `ONLINE`, `OFFLINE`

## Registering a Server

```bash
curl -X POST http://localhost:8080/api/v1/servers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "prod-web-1",
    "hostname": "ec2-1-2-3-4.compute.amazonaws.com",
    "ipAddress": "1.2.3.4",
    "sshPort": 22,
    "sshUsername": "ubuntu",
    "authType": "KEY",
    "credentialId": "secret-uuid-with-ssh-key",
    "environment": "production"
  }'
```

### What Happens on Create

1. Server record is saved to the database
2. If a default managed zone is configured, a subdomain is auto-assigned (e.g., `prod-web-1.yourdomain.com`)
3. If auto-SSL is enabled, SSL provisioning is triggered asynchronously

### Fields

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Unique friendly name (max 100 chars) |
| `hostname` | Yes | Server hostname or FQDN |
| `ipAddress` | No | IP address (used for DNS A records) |
| `sshPort` | No | SSH port (default: 22) |
| `sshUsername` | Yes | SSH login username |
| `authType` | Yes | `KEY` or `PASSWORD` |
| `credentialId` | No | UUID of the secret containing the SSH key or password |
| `passphraseCredentialId` | No | UUID of the secret containing the SSH key passphrase |
| `environment` | No | Environment tag (default: `production`) |
| `metadata` | No | Arbitrary JSON metadata |

## Testing Connections

After registering a server, test the SSH connection:

```bash
curl -X POST http://localhost:8080/api/v1/servers/$ID/test-connection \
  -H "Authorization: Bearer $TOKEN"
```

This attempts to establish an SSH connection and returns success/failure with latency.

## Executing Commands

Run ad-hoc SSH commands on a server:

```bash
curl -X POST http://localhost:8080/api/v1/servers/$ID/ssh/command \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"command": "df -h", "timeoutSeconds": 30}'
```

For more complex deployments, use the [deployment module](deployment.md) instead.

## Domain Auto-Assignment

When a server is created and a default managed zone exists, ClawOps automatically:

1. Creates a DNS record: `{server-name}.{zone-domain}` -> server IP
2. Provisions an SSL certificate (if configured)

See [domains.md](domains.md) and [ssl.md](ssl.md) for details.
