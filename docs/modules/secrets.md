# Secrets Module

The secrets module provides encrypted storage for sensitive credentials: SSH private keys, passwords, API tokens, and other secrets.

## How It Works

- Secrets are encrypted using **AES-256-GCM** before being stored in the database
- Each secret gets a unique random **initialization vector (IV)**
- The encryption key is derived from the `MASTER_ENCRYPTION_KEY` environment variable
- Secret values are **never returned in API list responses** — only metadata (name, type, description)

## Secret Types

| Type | Usage |
|------|-------|
| `SSH_KEY` | SSH private key (PEM or OpenSSH format) |
| `SSH_PASSWORD` | SSH login password |
| `API_KEY` | Generic API key or token |
| `PASSWORD` | Generic password |

## Creating a Secret

```bash
curl -X POST http://localhost:8080/api/v1/secrets \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "prod-ssh-key",
    "type": "SSH_KEY",
    "value": "-----BEGIN OPENSSH PRIVATE KEY-----\nb3BlbnNza...",
    "description": "Production server SSH key"
  }'
```

The `value` is encrypted before storage. The response includes the secret ID but not the value.

## Using Secrets

Secrets are referenced by UUID in other modules:

- **Servers**: `credentialId` points to an SSH key or password secret
- **Servers**: `passphraseCredentialId` points to a key passphrase secret
- **DNS Providers**: provider account credentials are stored as encrypted secrets internally

## Security Notes

- The `MASTER_ENCRYPTION_KEY` must be set as an environment variable — it is never stored in the database or source code
- If the master key is lost, **all secrets become unrecoverable**
- Secret values are decrypted only when needed (e.g., establishing an SSH connection)
- The `value` field is never included in API list responses
- Deleting a secret requires ADMIN role
