# Task 7: Secrets Module (Encrypted Credential Storage)

**Status:** DONE
**Module(s):** secrets, common
**Priority:** HIGH
**Created:** 2026-03-11
**Completed:** 2026-03-11

## Description
Implement the secrets module — AES-GCM encrypted storage for sensitive credentials (SSH passwords, private keys, API keys, DNS tokens). Secrets are write-only from the API (plaintext never returned). Internal `decryptSecret()` is used by other modules (SSH, deployment) to retrieve credentials when needed. The server registry module (task-8) depends on this for storing SSH credentials.

## Acceptance Criteria

### EncryptionService
- [ ] `EncryptionService` using `AES/GCM/NoPadding` with 256-bit key
- [ ] `encrypt(String plaintext)` → `EncryptedPayload` record (ciphertext bytes + IV bytes)
- [ ] `decrypt(byte[] ciphertext, byte[] iv)` → `String`
- [ ] Master key loaded from `MASTER_ENCRYPTION_KEY` env var (base64-encoded, at least 32 bytes)
- [ ] Each encrypt call generates a **unique random IV** (12 bytes for GCM) — never reuse
- [ ] Throws `EncryptionException` on any failure (bad key, corrupted data, etc.)

### Startup Validation
- [ ] Application **fails fast** on startup if `MASTER_ENCRYPTION_KEY` is missing or invalid
- [ ] Use `@PostConstruct` or `CommandLineRunner` to validate the key at boot
- [ ] Clear error message: `"MASTER_ENCRYPTION_KEY is not set or invalid. Application cannot start without an encryption key."`

### Entity & Migration
- [ ] `Secret` JPA entity — UUID PK, name (unique), type (enum), encryptedValue (byte[]), iv (byte[]), description (nullable), createdBy (FK→User, nullable), createdAt, updatedAt
- [ ] `SecretType` enum: `SSH_PASSWORD`, `SSH_PRIVATE_KEY`, `API_KEY`, `DNS_TOKEN`, `DEPLOYMENT_TOKEN`
- [ ] Flyway migration `V5__create_secrets_table.sql` with indexes on name and type

### Repository
- [ ] `SecretRepository extends JpaRepository<Secret, UUID>`
- [ ] `findByName(String)` → `Optional<Secret>`
- [ ] `findByType(SecretType)` → `List<Secret>`
- [ ] `existsByName(String)` → `boolean`

### DTOs
- [ ] `CreateSecretRequest` — name (`@NotBlank @Size(max=100)`), type (`@NotNull`), value (`@NotBlank`), description (optional)
- [ ] `UpdateSecretRequest` — name (optional), value (optional, re-encrypts if provided), description (optional)
- [ ] `SecretResponse` — id, name, type, description, createdAt, updatedAt — **NEVER includes encryptedValue or iv**
- [ ] `EncryptedPayload` — internal record holding ciphertext + IV (not exposed via API)

### Mapper
- [ ] `SecretMapper` — static methods: `toResponse(Secret)`, `toEntity(CreateSecretRequest, EncryptedPayload, UUID createdBy)`

### Service
- [ ] `SecretService` with constructor injection of `SecretRepository`, `EncryptionService`, `AuditService`
- [ ] `createSecret(CreateSecretRequest, UUID userId)` — encrypt value, save entity, audit log `SECRET_CREATED`
- [ ] `getAllSecrets(Pageable)` → `Page<SecretResponse>` (metadata only)
- [ ] `getSecretById(UUID)` → `SecretResponse` (metadata only)
- [ ] `updateSecret(UUID, UpdateSecretRequest)` — re-encrypt if value changed, audit log `SECRET_UPDATED`
- [ ] `deleteSecret(UUID)` — remove, audit log `SECRET_DELETED`
- [ ] `decryptSecret(UUID)` → `String` — **internal only**, used by SSH/deployment modules to get plaintext
- [ ] All write methods annotated with `@Transactional`
- [ ] Duplicate name check on create (throw `DuplicateResourceException`)
- [ ] Audit calls wrapped in try/catch — audit failures never break the operation
- [ ] Plaintext values NEVER logged — not in info, debug, or error logs

### Controller
- [ ] `SecretController` at `/api/v1/secrets`
- [ ] `POST /` — create secret (authenticated)
- [ ] `GET /` — list all secrets, paginated (authenticated)
- [ ] `GET /{id}` — get secret metadata (authenticated)
- [ ] `PATCH /{id}` — update secret (authenticated)
- [ ] `DELETE /{id}` — delete secret (ADMIN only)
- [ ] All request bodies validated with `@Valid`
- [ ] Swagger `@Tag(name = "Secrets")` and `@Operation` annotations

### Security Config Update
- [ ] SecurityConfig: `/api/v1/secrets/**` DELETE requires ADMIN role, other methods require authentication
- [ ] Ensure `decryptSecret()` is never exposed as a REST endpoint

### EncryptionException
- [ ] Add `EncryptionException` to common exception package (extends `RuntimeException`)
- [ ] `GlobalExceptionHandler` maps `EncryptionException` to 500

### Dev Admin Page
- [ ] Update `/dev/secrets.html` from placeholder to functional page:
  - Table showing secrets (id, name, type, description, createdAt)
  - Create secret form (name, type dropdown, value textarea, description)
  - Edit secret (name, new value, description)
  - Delete button with confirmation
  - **Value field should show "••••••••" after creation** — never display plaintext
  - Pagination controls

## Implementation Notes

### Migration SQL
```sql
-- V5__create_secrets_table.sql
CREATE TABLE secrets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    type VARCHAR(30) NOT NULL,
    encrypted_value BYTEA NOT NULL,
    iv BYTEA NOT NULL,
    description TEXT,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_secrets_name ON secrets(name);
CREATE INDEX idx_secrets_type ON secrets(type);
```

### EncryptionService implementation hints
```java
@Service
public class EncryptionService {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;  // GCM recommended
    private static final int TAG_LENGTH = 128; // GCM auth tag bits
    private final SecretKey masterKey;

    public EncryptionService(@Value("${env.MASTER_ENCRYPTION_KEY:}") String keyBase64) {
        if (keyBase64 == null || keyBase64.isBlank()) {
            throw new EncryptionException("MASTER_ENCRYPTION_KEY is not set");
        }
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        if (keyBytes.length < 32) {
            throw new EncryptionException("MASTER_ENCRYPTION_KEY must be at least 256 bits (32 bytes)");
        }
        this.masterKey = new SecretKeySpec(keyBytes, "AES");
    }

    public EncryptedPayload encrypt(String plaintext) {
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        // ... Cipher.getInstance(ALGORITHM), init ENCRYPT_MODE, doFinal
    }

    public String decrypt(byte[] ciphertext, byte[] iv) {
        // ... Cipher.getInstance(ALGORITHM), init DECRYPT_MODE, doFinal
    }
}
```

### EncryptedPayload record
```java
public record EncryptedPayload(byte[] ciphertext, byte[] iv) {}
```

### Generating a MASTER_ENCRYPTION_KEY
Users can generate a valid key with:
```bash
openssl rand -base64 32
```
Add to `.env`:
```
MASTER_ENCRYPTION_KEY=<base64 output>
```

### Startup validation approach
The `EncryptionService` constructor validates the key. If the key is missing/invalid, the bean fails to create → Spring Boot fails to start with a clear error. No separate `CommandLineRunner` needed.

### Recommended implementation order
1. `EncryptionException` (common module)
2. `EncryptedPayload` record
3. `EncryptionService` (with startup validation)
4. `SecretType` enum
5. `Secret` entity + `V5` migration
6. `SecretRepository`
7. `SecretResponse`, `CreateSecretRequest`, `UpdateSecretRequest` DTOs
8. `SecretMapper`
9. `SecretService`
10. `SecretController` + SecurityConfig update
11. Update `secrets.html` dev page
12. Update GlobalExceptionHandler for EncryptionException
13. Update architecture log

### Package structure
```
com.openclaw.manager.openclawserversmanager/
├── common/
│   └── exception/EncryptionException.java
└── secrets/
    ├── controller/SecretController.java
    ├── dto/CreateSecretRequest.java
    ├── dto/UpdateSecretRequest.java
    ├── dto/SecretResponse.java
    ├── dto/EncryptedPayload.java
    ├── entity/Secret.java
    ├── entity/SecretType.java
    ├── mapper/SecretMapper.java
    ├── repository/SecretRepository.java
    └── service/EncryptionService.java
    └── service/SecretService.java
```

## Files Modified

### New files (secrets module)
- `src/main/java/.../secrets/entity/SecretType.java` — 5-value enum
- `src/main/java/.../secrets/entity/Secret.java` — JPA entity
- `src/main/java/.../secrets/repository/SecretRepository.java` — JpaRepository
- `src/main/java/.../secrets/dto/EncryptedPayload.java` — internal record (ciphertext + iv)
- `src/main/java/.../secrets/dto/CreateSecretRequest.java` — validated request DTO
- `src/main/java/.../secrets/dto/UpdateSecretRequest.java` — partial update DTO
- `src/main/java/.../secrets/dto/SecretResponse.java` — response DTO (no encrypted data)
- `src/main/java/.../secrets/mapper/SecretMapper.java` — static toResponse/toEntity
- `src/main/java/.../secrets/service/EncryptionService.java` — AES-256-GCM encrypt/decrypt
- `src/main/java/.../secrets/service/SecretService.java` — CRUD + decryptSecret (internal)
- `src/main/java/.../secrets/controller/SecretController.java` — REST endpoints
- `src/main/resources/db/migration/V5__create_secrets_table.sql` — table + indexes

### New files (common module)
- `src/main/java/.../common/exception/EncryptionException.java` — runtime exception

### Modified files
- `src/main/java/.../auth/config/SecurityConfig.java` — DELETE /api/v1/secrets/** ADMIN only
- `src/main/java/.../common/exception/GlobalExceptionHandler.java` — EncryptionException handler
- `.env` — added MASTER_ENCRYPTION_KEY value
- `.env.example` — updated comment for encryption key
- `src/main/resources/static/dev/secrets.html` — full secrets management page
- `src/main/resources/static/dev/index.html` — Secrets marked as "Active"
