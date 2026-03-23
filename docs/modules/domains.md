# Domains Module

The domains module automates DNS management. It supports multiple DNS providers, auto-imports domains, and automatically creates subdomains for your managed servers.

## Concepts

- **Provider Account**: credentials for a DNS provider (Cloudflare or Namecheap)
- **Managed Zone**: a domain you use for subdomain assignment (e.g., `yourdomain.com`)
- **Domain Assignment**: a subdomain record pointing to a server or custom target
- **Auto-assign**: when enabled, new servers automatically get a subdomain

## Supported Providers

### Cloudflare

- **Auth**: API token with DNS edit permissions
- **Capabilities**: full DNS record management, domain listing
- **Setup**: create an API token in the Cloudflare dashboard with `Zone.DNS:Edit` permissions

### Namecheap

- **Auth**: API key + username
- **Capabilities**: DNS record management, domain listing
- **Setup**: enable API access in the Namecheap dashboard, whitelist your server IP

## Setup Workflow

### 1. Add a Provider Account

```bash
curl -X POST http://localhost:8080/api/v1/provider-accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Cloudflare",
    "providerType": "CLOUDFLARE",
    "apiToken": "your-cloudflare-api-token"
  }'
```

### 2. Validate Credentials

```bash
curl -X POST http://localhost:8080/api/v1/provider-accounts/$ID/validate \
  -H "Authorization: Bearer $TOKEN"
```

### 3. Sync Domains

Import all domains from the provider:

```bash
curl -X POST http://localhost:8080/api/v1/provider-accounts/$ID/sync-domains \
  -H "Authorization: Bearer $TOKEN"
```

This creates managed zones for each domain found at the provider.

### 4. Set a Default Zone

Mark a zone for auto-assignment:

```bash
curl -X POST http://localhost:8080/api/v1/zones/$ZONE_ID/set-default \
  -H "Authorization: Bearer $TOKEN"
```

Now, every new server registered will automatically get a subdomain under this zone.

## Auto-Assignment

When a server is created and a default zone exists:

1. A DNS A record is created: `{server-name}.{zone-domain}` -> server IP
2. The assignment is tracked in the `domain_assignments` table
3. SSL provisioning is triggered (if configured)

## Custom Records

Create arbitrary DNS records:

```bash
curl -X POST http://localhost:8080/api/v1/domain-assignments/custom \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "zoneId": "zone-uuid",
    "subdomain": "api",
    "recordType": "A",
    "target": "1.2.3.4"
  }'
```

Supported record types: `A`, `AAAA`, `CNAME`.

## DNS Verification

Check if a DNS record has propagated:

```bash
curl -X POST http://localhost:8080/api/v1/domain-assignments/$ID/verify \
  -H "Authorization: Bearer $TOKEN"
```

## Event History

Each assignment tracks a history of events (created, verified, failed, etc.):

```bash
curl http://localhost:8080/api/v1/domain-assignments/$ID/events \
  -H "Authorization: Bearer $TOKEN"
```
