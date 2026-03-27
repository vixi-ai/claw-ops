# Secrets — Architecture Log

Living documentation of implemented code. Agents MUST append here after writing code for this module.

## Implemented Components

### EncryptionException
- **File(s):** `src/main/java/.../common/exception/EncryptionException.java`
- **Type:** exception
- **Description:** Runtime exception for encryption failures. GlobalExceptionHandler maps to 500 with generic message.
- **Date:** 2026-03-11

### EncryptionService + EncryptedPayload
- **File(s):** `src/main/java/.../secrets/service/EncryptionService.java`, `.../secrets/dto/EncryptedPayload.java`
- **Type:** service + dto
- **Description:** AES-256-GCM encrypt/decrypt. Master key from MASTER_ENCRYPTION_KEY env var (base64, min 32 bytes). Fails fast on startup if missing. Unique 12-byte IV per call.
- **Date:** 2026-03-11

### Secret Entity + SecretType + Migration
- **File(s):** `.../secrets/entity/Secret.java`, `SecretType.java`, `db/migration/V5__create_secrets_table.sql`
- **Type:** entity + enum + migration
- **Description:** UUID PK, name (unique), type (5 values), encryptedValue/iv (BYTEA), description, createdBy FK. Indexes on name and type.
- **Date:** 2026-03-11

### SecretRepository + DTOs + Mapper
- **File(s):** `SecretRepository.java`, `CreateSecretRequest.java`, `UpdateSecretRequest.java`, `SecretResponse.java`, `SecretMapper.java`
- **Type:** repository + dto + mapper
- **Description:** JpaRepository with findByName/findByType/existsByName. Response never includes encrypted data.
- **Date:** 2026-03-11

### SecretService
- **File(s):** `.../secrets/service/SecretService.java`
- **Type:** service
- **Description:** CRUD + internal decryptSecret(). Encrypts via EncryptionService. Duplicate name check. Audit logging. @Transactional on writes.
- **Date:** 2026-03-11

### SecretController + Security + Error Handling
- **File(s):** `SecretController.java`, `SecurityConfig.java`, `GlobalExceptionHandler.java`
- **Type:** controller + config
- **Description:** /api/v1/secrets REST endpoints. DELETE ADMIN only. EncryptionException mapped to 500.
- **Date:** 2026-03-11

### Dev Page: secrets.html
- **File(s):** `static/dev/secrets.html`, `static/dev/index.html`
- **Type:** static resource
- **Description:** Full secrets management page. Values shown as masked dots. Dashboard shows Secrets as Active.
- **Date:** 2026-03-11
