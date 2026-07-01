# AGENTS.md — finance-app

Context file for AI coding agents. Read this before making any change to the codebase.

---

## Project overview

Personal finance application built as a set of Spring Boot microservices, a dedicated API Gateway (Nginx reverse proxy), a React/TypeScript frontend, and a shared PostgreSQL database. All services run locally via Docker Compose.

---

## Repository structure

```
finance-app/
├── banking/        Spring Boot service — bank accounts & transactions  (port 8081)
├── investments/    Spring Boot service — investment portfolio          (port 8082)
├── wealth/         Spring Boot service — net-worth snapshots           (port 8083)
├── budget/         Spring Boot service — budget tracking               (port 8084)
├── gateway/        Nginx API Gateway (routes /v1/api/* to backends)   (port 8080)
├── frontend/       React + TypeScript + Vite SPA                       (port 5173/80)
├── infra/
│   ├── db/         PostgreSQL docker-compose + .env + backup scripts
│   ├── keycloak/   Keycloak docker-compose + .env
│   └── rabbitmq/   RabbitMQ docker-compose + .env
├── docker-compose.yml   Orchestrates app services only
├── up-all.sh            Full startup: infra + app services
└── pom.xml              Root Maven multi-module POM
```

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.x |
| ORM | Spring Data JPA + Hibernate |
| DB migrations | Flyway |
| Database | PostgreSQL 18 (container `finance-db`, host port 5433) |
| Auth | Keycloak (OAuth2 / OIDC); each backend is a Resource Server |
| Messaging | RabbitMQ (planned/partial) |
| Frontend | React 18, TypeScript, Vite |
| Containerization | Docker + Docker Compose |
| Testing | JUnit 5, Mockito, Testcontainers, H2 (unit tests only) |
| Build | Maven (multi-module); root `mvnw` wrapper is incomplete — use system Maven or IDE |

---

## Microservices quick reference

### banking (port 8081)
- Manages bank accounts and transactions.
- CSV import of transactions from banks.
- Hierarchical transaction categories with subcategories.
- Tags per transaction; merchants with aliases.
- Tenant isolation via `tenant_id` on every table.
- Tax withholding support on transactions.
- Linked transactions (e.g. internal transfers).
- Flyway schema: `banking`. Migrations: `V1__init.sql` … `V10__transaction_taxes.sql`.
- Calls out to: nothing (it is the source of truth for transactions).
- Consumed by: budget, wealth.

### investments (port 8082)
- Investment portfolio: instruments, operations (BUY/SELL), positions.
- FIFO lot tracking and realized P&L.
- Exchange rates and price catalogue.
- Fund/ETF exposure breakdown by COUNTRY, REGION, SECTOR, INDUSTRY, MARKET_REGIME.
- Alias mapping for untranslated exposure bucket names (e.g. from Finect).
- Price auto-refresh scheduler (cron; excludes FUND and ETF by default).
- Flyway schema: `investments`.
- Calls out to: banking (account balances).

### wealth (port 8083)
- Periodic net-worth snapshots aggregating banking + investments.
- Per-asset historical detail.
- Flyway schema: `wealth`.
- Calls out to: banking (`clients.banking.base-url`).

### budget (port 8084)
- Budget definitions and transaction matching against banking transactions.
- Flyway schema: `budget`.
- Calls out to: banking (`clients.banking.base-url`).

---

## Frontend

- **Stack:** React 18, TypeScript, Vite, nginx (production Docker image).
- **Auth:** Keycloak OIDC via `authService.ts`.
- **Services:** one file per backend domain in `frontend/src/services/`.
  - `accountsService.ts`, `transactionsService.ts`, `merchantsService.ts` → banking
  - `investmentCatalogService.ts`, `investmentOperationsService.ts`, `exchangeRatesService.ts` → investments
  - `wealthService.ts` → wealth
  - `budgetService.ts` → budget
- **Key components:** `Dashboard`, `TransactionsTable`, `InvestmentsDashboard`, `WealthPanel`, `BudgetPanel`, `TaxWithholdingReport`.
- Runtime config via `frontend/src/config/env.ts` (reads `VITE_*` env vars).
- Environment variables are set at Docker build time via `--build-arg`.

---

## Database

- Single PostgreSQL cluster (`finance-db` container).
- Each service owns its own **schema** (matching its service name).
- Schema is configured via `DATABASE_SCHEMA` env var and `spring.jpa.properties.hibernate.default_schema`.
- `ddl-auto=validate` on all services — Flyway manages schema exclusively. Never change DDL manually.
- Base currency: **EUR**. All cross-currency values are converted to EUR on the transaction date.

---

## Authentication

- Keycloak realm: `finance-app`, client: `finance-client`.
- Each Spring Boot service validates JWT tokens as an OAuth2 Resource Server.
- `KEYCLOAK_ISSUER_URI` and `KEYCLOAK_JWK_SET_URI` are required env vars per service.
- Tests use `spring-security-test` with mocked principals; H2 for unit DB.

---

## Environment variables

Each service loads its own `.env` file (gitignored). Templates are `.env.example` files.

Key variables shared by all backends:
```
DATABASE_URL=jdbc:postgresql://...
DATABASE_USERNAME=...
DATABASE_PASSWORD=...
DATABASE_SCHEMA=<service-name>
KEYCLOAK_ISSUER_URI=...
KEYCLOAK_JWK_SET_URI=...
SERVER_PORT=...
```

Root `.env` contains only frontend/compose-level variables (`VITE_*`, `FRONTEND_PORT`).

---

## Running the project

```bash
# Start everything (infra + app services)
./up-all.sh

# Start only infrastructure
cd infra/db && docker-compose up -d
cd infra/keycloak && docker-compose up -d
cd infra/rabbitmq && docker-compose up -d

# Start only app services
docker compose -f docker-compose.yml -f docker-compose.local.yml up --build -d
```

Docker network: `finance-net` (external; created by `up-all.sh` if missing).

---

## Backup and restore

Scripts in `infra/db/scripts/`:

| Script | Purpose |
|---|---|
| `backup-full-db.ps1` | Full `pg_dumpall` backup, optional compress + cloud upload |
| `backup-gdrive.ps1` | Wrapper: compress enabled, uploads to `gdrive:finance-app-backups` by default |
| `restore-full-db.ps1` | Full restore from `.sql` or `.zip`; blocks if app microservices are running |

Backups output to `infra/db/backups/` (gitignored). Cloud upload via `rclone`.

Restore safety: the restore script checks for active `banking`, `investments`, `wealth`, `budget`, `frontend` containers and aborts unless `-AllowActiveMicroservices` is passed.

---

## Coding conventions

- Package root: `es.triana.company.<service>` (e.g. `es.triana.company.banking`).
- Lombok is used in all services (`@Data`, `@Builder`, etc.).
- Always use explicit names in Spring MVC annotations: `@PathVariable("id")`, `@RequestParam(name="...")`. The Java `-parameters` compiler flag is not guaranteed to be active.
- `ddl-auto=validate` — never write raw DDL; always add a Flyway migration script.
- New Flyway migrations follow the naming pattern: `V<next>__<short_description>.sql`.
- Flyway scripts go in `src/main/resources/db/migration/`.
- Tests use H2 for unit tests and Testcontainers (PostgreSQL) for integration tests.
- All code, comments, and documentation should be written in **English**.
- PowerShell scripts: `param(...)` block must be the first statement (before `Set-StrictMode`).

---

## Known issues / gotchas

- Root Maven wrapper (`mvnw`) is incomplete (missing `.mvn/wrapper/maven-wrapper.properties`). Use system Maven or build via IDE.
- `pg_dumpall` restore always emits `ERROR: current user cannot be dropped` and `ERROR: role "postgres" already exists` — these are expected and harmless.
- Wealth service uses `flyway.validate-on-migrate=false` to avoid issues with evolving schemas.
- Finect fund exposure data is only available from `window.INITIAL_STATE` in the product page HTML. Unauthenticated calls to `api.finect.com` return 401.
- Each service sets `spring.jpa.properties.hibernate.jdbc.time_zone=UTC` where time precision matters (investments).

---

## Planned / not yet implemented

From `roadmap.md`:
- API Gateway (Spring Cloud Gateway or similar).
- MarketData service (price feeds, FX rates automation).
- Tax service (tax events, annual report generation).
- Notifications service (email/push alerts).
- Grafana + Prometheus observability stack.
- CI/CD pipeline (GitHub Actions).
- Cypress E2E tests.
- S3/MinIO for backup storage (currently using Google Drive via rclone).
