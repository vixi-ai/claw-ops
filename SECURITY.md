# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in ClawOps, please report it responsibly.

**Do NOT open a public GitHub issue for security vulnerabilities.**

Instead, please report vulnerabilities by emailing the maintainers or using [GitHub's private vulnerability reporting](https://github.com/vixi-ai/claw-ops/security/advisories/new).

Include:

- A description of the vulnerability
- Steps to reproduce the issue
- The potential impact
- Any suggested fix (optional)

We will acknowledge receipt within 48 hours and aim to provide a fix or mitigation plan within 7 days for critical issues.

## Supported Versions

| Version | Supported |
|---------|-----------|
| Latest on `main` | Yes |
| Older releases | Best effort |

## Security Best Practices for Operators

- **Never commit `.env` files** or expose `MASTER_ENCRYPTION_KEY`, `JWT_SECRET`, or database credentials
- **Rotate the `MASTER_ENCRYPTION_KEY`** periodically (note: this requires re-encrypting all stored secrets)
- **Use strong passwords** for the admin bootstrap account
- **Restrict network access** to the ClawOps instance — it should not be publicly exposed without a reverse proxy and authentication
- **Keep dependencies updated** — run `./mvnw versions:display-dependency-updates` to check for updates

See [docs/security.md](docs/security.md) for full details on authentication, encryption, and access control.
