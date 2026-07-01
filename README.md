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

WIP

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
