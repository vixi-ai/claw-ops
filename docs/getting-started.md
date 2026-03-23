# Getting Started

This guide walks you through installing, configuring, and running ClawOps for the first time.

## Prerequisites

- **Java 21** or later (JDK, not JRE)
- **PostgreSQL 17** or later
- **Maven 3.9+** (or use the included `./mvnw` wrapper)
- **Docker + Docker Compose** (optional, for running PostgreSQL)

## 1. Clone the Repository

```bash
git clone https://github.com/your-org/clawops.git
cd clawops
```

## 2. Set Up PostgreSQL

### Option A: Docker Compose (recommended)

```bash
cp .env.example .env
# Edit .env if you want to change DB credentials
docker-compose up -d
```

This starts PostgreSQL 17 on port 5432 with a database named `openclaw`.

### Option B: Local PostgreSQL

```bash
createdb openclaw
createuser openclaw -P    # set a password when prompted
```

## 3. Configure Environment

Copy the example environment file and fill in the required values:

```bash
cp .env.example .env
```

**Required variables:**

| Variable | How to generate |
|----------|----------------|
| `MASTER_ENCRYPTION_KEY` | `openssl rand -base64 32` |
| `JWT_SECRET` | `openssl rand -base64 64` |
| `DB_PASSWORD` | Your PostgreSQL password |
| `ADMIN_PASSWORD` | Password for the bootstrap admin user |

See [configuration.md](configuration.md) for all available variables.

## 4. Build

```bash
./mvnw clean install
```

This compiles the project, runs Flyway migrations (creating all database tables), and packages the application.

## 5. Run

```bash
./mvnw spring-boot:run
```

Or with the dev profile for SQL logging:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The application starts at **http://localhost:8080**.

## 6. First Login

On first startup, if no users exist in the database, `AdminBootstrapRunner` creates an admin user with the credentials from your `.env` file (`ADMIN_EMAIL`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`). These are printed to the console log.

Open the dev admin panel:

```
http://localhost:8080/dev/login.html
```

Log in with your admin credentials.

## 7. Add Your First Server

1. Navigate to **Servers** from the dashboard
2. Click **+ Add Server**
3. Fill in:
   - **Name**: a friendly name (e.g., `prod-web-1`)
   - **Hostname**: the server's hostname or IP
   - **SSH Username**: usually `ubuntu`, `root`, or your deploy user
   - **Auth Type**: `KEY` (SSH key) or `PASSWORD`
   - **Credential**: select an existing secret or create one first in the Secrets page
4. Click **Save**
5. Click **Test Connection** to verify SSH access

## 8. Add a DNS Provider (optional)

If you want automatic subdomain provisioning:

1. Navigate to **Domains**
2. Click **+ Add Account**
3. Select your provider (**Cloudflare** or **Namecheap**)
4. Enter your API credentials:
   - **Cloudflare**: API token with DNS edit permissions
   - **Namecheap**: API key + username (must enable API access in Namecheap dashboard)
5. Click **Save**, then **Sync Domains** to import your domains
6. Create a **Managed Zone** for the domain you want to use for subdomains
7. Set it as the **default zone** for auto-assignment

New servers will automatically get a subdomain assigned (e.g., `prod-web-1.yourdomain.com`).

## 9. SSL Provisioning

SSL certificates are provisioned automatically via certbot + nginx on the target server. Requirements:

- **Port 80 must be open** on the server (for HTTP-01 challenge)
- **DNS must be propagated** (the subdomain must resolve to the server's IP)
- If using **Cloudflare**: disable "Always Use HTTPS" and "Automatic HTTPS Rewrites" for the subdomain while provisioning

If SSL provisioning fails:

1. Check that port 80 is open in the server's security group/firewall
2. Verify DNS resolution: `dig +short your-subdomain.yourdomain.com`
3. Wait a few minutes for DNS propagation and retry
4. Check the application logs for detailed error messages

## What's Next?

- **Write deployment scripts**: Go to Deployments to create bash scripts you can run on any server
- **Create agent templates**: Go to Templates to define OpenClaw agent configurations
- **Use the terminal**: Go to SSH & Terminal for a browser-based SSH session to any server
- **Explore the API**: Visit `/swagger-ui.html` for the full API documentation
