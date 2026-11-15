# finance-app

This repository is a personal project to build a personal finance application.

## Project Overview

finance-app is a modular personal finance application built with Java Spring Boot, Docker, and a set of supporting services. The project is structured for local development and easy orchestration of all components.

### Main Features Developed

- Modular orchestration with Docker Compose for all services (banking, database, Keycloak, RabbitMQ)
- Spring Boot backend (Java 21, Spring Boot 4.x)
- Database migrations managed with Flyway
- Secure authentication with Keycloak
- Messaging integration with RabbitMQ
- Environment variable management via .env files
- Automated test configuration with H2 in-memory database

Closed accounts can be deactivated and reactivated from the accounts table. Their transaction history remains available, but inactive accounts are excluded from current account totals and from new transaction and import selectors. Existing snapshots are not modified.

Budget plans can be marked as primary per tenant; the banking dashboard shows the primary plan for the current month. Existing plans are treated as monthly because earlier versions did not persist their period. Existing snapshots gain a monthly breakdown when recalculated.

Budget amounts are allocated by calendar month. Monthly plans use the full monthly amount even for a partial month. Quarterly, annual, and custom-period amounts are split across their months, with rounding differences assigned to the final month; custom plans are limited to their configured dates. The budget service rejects matching transactions in another currency because automatic currency conversion is not yet available.

## How to Start All Systems

1. **Prerequisites:**

    - Docker Desktop
    - Git for Windows
    - WSL (Windows Subsystem for Linux)
    - (Optional) Postgres client for manual DB access like Dbeaver

2. **Install WSL (if not present):**

    ```powershell
    wsl --install
    ```

3. **Clone the repository:**

    ```bash
    git clone <repo-url>
    cd finance-app
    ```

4. **Start all services:**

    ```bash
    ./up-all.sh
    ```

    For VPS production stack (infra + app + Caddy/HTTPS):

    ```bash
    ./up-all-prod.sh
    ```

    This script:

    - Creates the Docker network if it does not exist
    - Stops and removes previous containers
    - Starts the database, Keycloak, and RabbitMQ services
    - Builds and starts the banking backend

5. **Verify that everything is working:**

    - The Postgres database will be available on the configured port (see infra/db/.env)
    - Keycloak and RabbitMQ will be accessible according to their respective docker-compose files
    - The banking backend will be available on the port configured in its .env

### Start app services only (local)

Use the local overlay to expose frontend and gateway on host ports:

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d --build
```

Default local ports with this overlay:

- Frontend: `http://localhost:5173`
- Gateway API: `http://localhost:8088`

## Testing

Run frontend tests with `npm --prefix frontend test`. Run the account service and history integration tests with:

```bash
mvn -pl banking -Dtest=AccountsServiceTest,AccountActivityIntegrationTest test
```

Run budget unit and integration tests with `mvn -pl budget test`. The PostgreSQL integration tests use Testcontainers and are skipped when Docker is unavailable.

## Production HTTPS Deployment (Recommended)

This project includes a production-ready HTTPS entrypoint using Caddy + Let's Encrypt.

### 1. DNS records

Create DNS records pointing to your server public IP:

- `APP_DOMAIN` (example: `app.example.com`)
- `KEYCLOAK_DOMAIN` (example: `auth.example.com`)

### 2. Environment variables

In your root `.env` file, set:

```env
APP_DOMAIN=app.example.com
KEYCLOAK_DOMAIN=auth.example.com
LETSENCRYPT_EMAIL=you@example.com
VITE_KEYCLOAK_BASE_URL=https://auth.example.com
VITE_KEYCLOAK_REALM=finance-app
VITE_KEYCLOAK_CLIENT_ID=finance-client
```

### 3. Start infrastructure and app

Start infra first (db, keycloak, rabbitmq), then app with production overlay:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Or use the helper script:

```bash
./up-all-prod.sh
```

To stop full production stack:

```bash
./down-all-prod.sh
```

### 4. Exposed ports (production)

- Public: `80`, `443` (Caddy only)
- Internal only: frontend, gateway, banking, investments, wealth, budget

### 5. Security notes

- Keep backend ports closed in cloud firewall/security group.
- Keep only 22, 80, 443 open to internet.
- Caddy automatically provisions and renews TLS certificates.

## Current development status

- Modular and robust orchestration of all services
- Functional banking backend, with Flyway migrations and automated tests
- Infrastructure prepared for local development and testing

For any questions about the structure or startup, check this README or the README.md files in each subfolder.
