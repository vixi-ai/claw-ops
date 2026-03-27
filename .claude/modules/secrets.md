# Secrets Module

## Purpose

Provides encrypted storage for sensitive credentials (SSH passwords, private keys, API keys, DNS tokens). All secrets are encrypted at rest using AES-GCM with a master key from the environment.

## Package

`com.openclaw.manager.openclawserversmanager.secrets`

## Components

### Entity: `Secret`

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| name | String | NOT NULL |
| type | SecretType (enum) | NOT NULL |
| encryptedValue | byte[] / String | NOT NULL (AES-GCM ciphertext) |
| iv | byte[] | NOT NULL (initialization vector) |
| description | String | nullable |
| createdBy | UUID | FK → User |
| createdAt | Instant | auto-set |
| updatedAt | Instant | auto-set on update |

### Enum: `SecretType`

- `SSH_PASSWORD`
- `SSH_PRIVATE_KEY`
- `API_KEY`
- `DNS_TOKEN`
- `DEPLOYMENT_TOKEN`

### DTOs

**`CreateSecretRequest`**
- `name` — `@NotBlank @Size(max = 100)`
- `type` — `@NotNull`
- `value` — `@NotBlank` (plaintext — will be encrypted before storage)
- `description` — optional

**`UpdateSecretRequest`**
- `name` — optional
- `value` — optional (new plaintext to re-encrypt)
- `description` — optional

**`SecretResponse`**
- `id`, `name`, `type`, `description`, `createdAt`, `updatedAt`
- **Never includes `encryptedValue` or `iv`** — secrets are write-only from the API perspective

### Service: `SecretService`

- `createSecret(CreateSecretRequest, UUID userId)` — encrypts value, saves
- `getAllSecrets(Pageable)` — returns metadata only (no values)
- `getSecretById(UUID)` — returns metadata only
- `updateSecret(UUID, UpdateSecretRequest)` — re-encrypts if value changed
- `deleteSecret(UUID)` — removes secret (check for references first)
- `decryptSecret(UUID)` — **internal only** — used by SSH/deployment modules to get plaintext

### Service: `EncryptionService`

- `encrypt(String plaintext)` → `EncryptedPayload` (ciphertext + IV)
- `decrypt(byte[] ciphertext, byte[] iv)` → `String`
- Uses `AES/GCM/NoPadding` with 256-bit key
- Master key loaded from `MASTER_ENCRYPTION_KEY` env var at startup
- Throws `EncryptionException` if master key is missing or operation fails

### Repository: `SecretRepository`

- `findByName(String)` → `Optional<Secret>`
- `findByType(SecretType)` → `List<Secret>`
- `existsByName(String)` → `boolean`

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/v1/secrets` | Yes | Store a new secret |
| GET | `/api/v1/secrets` | Yes | List all secrets (metadata only) |
| GET | `/api/v1/secrets/{id}` | Yes | Get secret metadata |
| PATCH | `/api/v1/secrets/{id}` | Yes | Update secret |
| DELETE | `/api/v1/secrets/{id}` | Yes (ADMIN) | Delete secret |

## Business Rules

- Secrets are **write-only** from the API — no endpoint to retrieve the plaintext value
- `decryptSecret()` is an internal method only, never exposed via REST
- Before deleting a secret, check if any servers reference it as `encryptedCredentialId`
- Each encrypt operation must generate a **unique IV** (never reuse IVs with AES-GCM)
- If `MASTER_ENCRYPTION_KEY` is not set, the application must fail to start with a clear error message

## Security Considerations

- Plaintext values must never appear in logs, error messages, or API responses
- The `encryptedValue` and `iv` columns must never be exposed via any API endpoint
- AES-GCM provides both confidentiality and integrity (authenticated encryption)
- Master key should be at least 256 bits (32 bytes), base64-encoded in the env var
- Consider key rotation support as a future enhancement

## Dependencies

- **users** — `createdBy` field references the user who created the secret
- **audit** — log secret creation, update, deletion events (never log the value)
