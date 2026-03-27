# Task 1: Setup OpenAPI / Swagger Configuration

**Status:** DONE
**Module(s):** common
**Priority:** HIGH
**Created:** 2026-03-11
**Completed:** 2026-03-11

## Description
Configure springdoc-openapi so Swagger UI is accessible and properly documents all API endpoints. The project already has the `springdoc-openapi-starter-webmvc-ui` dependency (v3.0.2) in pom.xml but no configuration exists yet.

## Acceptance Criteria
- [x] Swagger UI accessible at `/swagger-ui.html` (or `/swagger-ui/index.html`)
- [x] OpenAPI JSON/YAML spec available at `/v3/api-docs`
- [x] API info configured: title ("OpenClaw Control Plane API"), version, description
- [x] API grouped by module tags (auth, users, servers, secrets, ssh, deployments, templates, domains, audit)
- [x] Security scheme configured for JWT Bearer token in Swagger UI (so endpoints can be tested with auth)
- [ ] Swagger endpoints excluded from Spring Security authentication (permitAll) — deferred until Security config is implemented

## Implementation Notes
- Used `@SecurityScheme` annotation for JWT Bearer auth on the config class
- Used `@Bean OpenAPI` for API metadata (title, version, description, contact, license)
- Added springdoc properties to `application.properties` for paths and sorting
- Tag grouping will happen automatically as controllers are annotated with `@Tag`
- Security permitAll for Swagger endpoints will be configured when Spring Security is set up

## Files Modified
- `src/main/java/com/openclaw/manager/openclawserversmanager/common/config/OpenApiConfig.java` — **created**
- `src/main/resources/application.properties` — added springdoc configuration properties
