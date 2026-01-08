# ROADMAP

## Checklist por semana

 Código compilando y tests pasando.
 Docker Compose funcionando (Postgres, Keycloak, MQ).
 Documentación Swagger/OpenAPI por servicio.
 Scripts para importar datos CSV (banco, broker).
 Métricas y logs visibles en Grafana.

## Fase 0: Preparación (antes de Semana 1)

### Configura el entorno local

Instala Java 17+, Spring Boot CLI, Docker, Docker Compose.
Prepara contenedores: Postgres, Keycloak, RabbitMQ (o Kafka).

Prepara servicios base: API Gateway, Identity (Keycloak), Banking, Budgeting, Investments, MarketData, Tax, Reporting, Notifications.
Configura almacenamiento S3/MinIO para backups y exportaciones.
Configura Grafana/Prometheus para métricas y dashboards.

Crea un repositorio Git con estructura:
/gateway
/identity
/banking
/budgeting
/investments
/marketdata
/tax
/reporting
/notifications
/infra (docker-compose, scripts)

### Define convenciones

Nombres de paquetes, DTOs, endpoints REST.
Estrategia de multi-tenant (tenant_id en todas las tablas).

Define eventos y contratos para RabbitMQ (bus de eventos entre servicios).

### Configura CI/CD básico

GitHub Actions o GitLab CI para build + test.
Testcontainers para integración con Postgres.

## Semana 1–2: Fundamentos

### Identity + Gateway

Levanta Keycloak (realm, client, roles).
Configura Spring Security en Gateway (OAuth2 Resource Server).

Configura rutas en API Gateway para todos los servicios (Banking, Budgeting, Investments, Reporting, Tax, Notifications, MarketData).

### Plantillas Spring Boot

Crea proyectos base para Banking, Budgeting, Investments (con Spring Web, Data JPA, Actuator).

Crea proyectos base para MarketData, Tax, Reporting, Notifications (Spring Web, Data JPA, Actuator, integración con RabbitMQ y S3/MinIO donde aplique).

### Esquemas Postgres + Flyway

Define tablas iniciales:

Banking: accounts, transactions.
Budgeting: budgets, budget_lines.
Investments: assets, orders, lots.

MarketData: precios, tipos de cambio.
Tax: tax_events, informes.
Reporting: vistas materializadas para dashboards.
Notifications: alertas, logs de envíos.

Configura migraciones Flyway en cada servicio.

## Semana 2–3: Banking + Budgeting

### Banking

Endpoints: GET /accounts, POST /transactions/import (CSV).
Lógica: persistir transacciones, calcular saldo.
Categorización simple: reglas por merchant.

Publica eventos en RabbitMQ (imported, categorized, suspicious).
Exporta backups y CSV a S3/MinIO.

### Budgeting

CRUD presupuestos: POST /budgets, GET /budgets/{month}.
Resumen mensual: gasto vs objetivo por categoría.

Publica y consume eventos de presupuesto en RabbitMQ.
Exporta backups a S3/MinIO.

## Semana 3–4: Investments + MarketData

### Investments

CRUD assets: POST /assets.
Órdenes BUY/SELL: POST /orders.
Cálculo FIFO: lotes y plusvalías realizadas.

Publica y consume eventos de inversión en RabbitMQ.
Exporta backups a S3/MinIO.

### MarketData/FX

Importación manual CSV de precios y tipos de cambio.
Job nocturno para recalcular valoraciones.

Provee precios y FX rates a Investments y Banking vía HTTPs/AMQP.

## Semana 4–5: Reporting + Observabilidad

### Reporting

Materialized views en Postgres (gasto por categoría, net worth).
API /dashboard/summary.

Consume eventos de RabbitMQ para dashboards.
Exporta métricas a Grafana/Prometheus.

### Observabilidad

Micrometer + Prometheus + Grafana.
Logs estructurados + trazas con OpenTelemetry.

## Semana 5–6: Tax + Notificaciones

### Tax

Registrar eventos en ventas (tax_events).
Generar borrador informe anual (JSON/CSV).

Lee y escribe en Postgres, exporta backups a S3/MinIO.
Recibe datos de Reporting para informes anuales.

### Notificaciones

Enviar alertas cuando presupuesto excede límite.
Integración con email (SMTP) o push.

Consume eventos de RabbitMQ.
Lee y escribe en Postgres.

## Semana 6–8: UI + Hardening

### Front-end

Decide stack (React + TS recomendado).
Consume APIs Banking, Budgeting, Investments, Reporting.

Incluye endpoints de Notifications, Tax, MarketData si aplica.

### Hardening

Rate limiting en Gateway.
Auditoría y backup (pg_dump + S3/MinIO).

### Pruebas E2E

Cypress para UI.
Testcontainers para integración.
