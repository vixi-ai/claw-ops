# Task 23: .gitignore & Production Hardening

**Status:** DONE
**Module(s):** common
**Priority:** MEDIUM
**Created:** 2026-03-24
**Completed:** 2026-03-24

## Description

Harden the project for production deployments:
1. Add certificate/key file patterns to `.gitignore` to prevent accidental credential commits
2. Make SSH strict host key checking configurable via environment variable (default `true` for safety)
3. Update `.env.example` with all new env vars and security documentation comments

## Acceptance Criteria

### .gitignore Hardening
- [ ] Add patterns for certificate and key files: `*.pem`, `*.key`, `*.crt`, `*.jks`, `*.p12`, `*.pfx`, `*.keystore`
- [ ] Add pattern for SSH known hosts: `known_hosts`
- [ ] Add pattern for log files: `*.log`, `logs/`

### SSH Host Key Checking
- [ ] Change `ssh.strict-host-key-checking` to use env var: `${SSH_STRICT_HOST_KEY_CHECKING:true}`
- [ ] Default to `true` (secure by default) — dev environments override to `false` if needed
- [ ] Add `SSH_STRICT_HOST_KEY_CHECKING=false` to `.env.example` with comment explaining dev vs prod

### Documentation
- [ ] Architecture log entry in `.claude/architecture/common.md` for production hardening changes

## Implementation Notes

### Files to Modify
- `.gitignore`
- `src/main/resources/application.properties`
- `.env.example`
- `.claude/architecture/common.md`

## Files Modified
- `.gitignore` — **modified** — added certificate/key/log file patterns
- `src/main/resources/application.properties` — **modified** — SSH strict host key checking now env-configurable, defaults to `true`
- `.env.example` — **modified** — added `SSH_STRICT_HOST_KEY_CHECKING=false` for dev
- `.claude/architecture/common.md` — **modified** — appended Production Hardening entry
