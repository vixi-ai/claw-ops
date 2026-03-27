# Task 20: Security Headers Configuration

**Status:** DONE
**Module(s):** auth, common
**Priority:** HIGH
**Created:** 2026-03-24
**Completed:** 2026-03-24

## Description

The `SecurityConfig` filter chain has no explicit HTTP security headers configuration. Spring Security enables some defaults, but the project needs explicit, environment-configurable security headers to prevent clickjacking, MIME sniffing, protocol downgrade attacks, and API schema exposure.

Additionally, Swagger UI is publicly accessible in all profiles — it should be toggleable via environment variable so it can be disabled in production.

## Acceptance Criteria

### Security Headers in SecurityConfig
- [ ] Add `.headers()` configuration to the `SecurityFilterChain` in `SecurityConfig.java`
- [ ] Headers configured:
  - `X-Frame-Options: DENY` — prevents clickjacking
  - `X-Content-Type-Options: nosniff` — prevents MIME sniffing
  - `Strict-Transport-Security: max-age=31536000; includeSubDomains` — HSTS (only sent over HTTPS)
  - `Referrer-Policy: strict-origin-when-cross-origin` — limits referrer leakage
  - `X-XSS-Protection: 0` — disable browser XSS auditor (deprecated, CSP replaces it)
  - `Permissions-Policy: camera=(), microphone=(), geolocation=()` — restrict browser APIs
  - Cache-Control: `no-cache, no-store, max-age=0, must-revalidate` for API responses

### Swagger UI Toggle
- [ ] New property `springdoc.swagger-ui.enabled` backed by `SWAGGER_ENABLED` env var (default: `true`)
- [ ] When disabled, Swagger UI and API docs endpoints return 404
- [ ] `SecurityConfig` conditionally permits Swagger paths only when enabled
- [ ] Add `springdoc.api-docs.enabled=${SWAGGER_ENABLED:true}` to `application.properties`

### Environment & Config
- [ ] Add `SWAGGER_ENABLED` to `.env.example` with comment
- [ ] Add `SWAGGER_ENABLED=true` to `application.properties` default

### Documentation
- [ ] Architecture log entry in `.claude/architecture/auth.md` for SecurityConfig headers update

## Implementation Notes

### SecurityConfig Change

Add after `.cors()` and before `.csrf()`:

```java
.headers(headers -> headers
    .frameOptions(frame -> frame.deny())
    .contentTypeOptions(contentType -> {})
    .httpStrictTransportSecurity(hsts -> hsts
        .includeSubDomains(true)
        .maxAgeInSeconds(31536000))
    .referrerPolicy(referrer -> referrer
        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
    .permissionsPolicy(permissions -> permissions
        .policy("camera=(), microphone=(), geolocation=()"))
    .cacheControl(cache -> {})
)
```

### Swagger Toggle

In `application.properties`:
```properties
springdoc.swagger-ui.enabled=${SWAGGER_ENABLED:true}
springdoc.api-docs.enabled=${SWAGGER_ENABLED:true}
```

In `SecurityConfig`, conditionally permit Swagger paths using `@Value`:
```java
@Value("${springdoc.swagger-ui.enabled:true}")
private boolean swaggerEnabled;
```

Then in the filter chain, only add Swagger permitAll when enabled.

### Files to Modify
- `src/main/java/com/openclaw/manager/openclawserversmanager/auth/config/SecurityConfig.java`
- `src/main/resources/application.properties`
- `.env.example`
- `.claude/architecture/auth.md`

## Files Modified
- `src/main/java/com/openclaw/manager/openclawserversmanager/auth/config/SecurityConfig.java` — **modified** — added security headers (X-Frame-Options, HSTS, Referrer-Policy, Permissions-Policy, Cache-Control, X-Content-Type-Options), added Swagger conditional access via `@Value`, refactored authorizeHttpRequests to block lambda
- `src/main/resources/application.properties` — **modified** — added `springdoc.api-docs.enabled` and `springdoc.swagger-ui.enabled` backed by `SWAGGER_ENABLED` env var
- `.env.example` — **modified** — added `SWAGGER_ENABLED=true`
- `.claude/architecture/auth.md` — **modified** — appended Security Headers & Swagger Toggle entry
