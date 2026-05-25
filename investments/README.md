# 📈 Investments — Finance App Microservice

REST API microservice for personal investment portfolio management. Handles investment positions, market operations (BUY/SELL), automatic FIFO-based fiscal calculation, market price refresh, and EUR exchange rate management.

Part of the **finance-app** multi-module monorepo alongside the `banking` module.

---

## ✨ Características principales

- **Gestión de posiciones de inversión**: CRUD completo para carteras multi-tenant (fondos, ETFs, acciones, criptomonedas).
- **Registro de operaciones BUY/SELL**: Validación completa, cálculo automático de totales y conversión a EUR.
- **FIFO automático**: Al registrar una venta, el sistema calcula y persiste los lotes FIFO aplicables (criterio AEAT — por instrumento financiero, no por posición individual).
- **Resumen fiscal anual**: `GET /tax-summary?year=YYYY` devuelve ganancia/pérdida realizada por instrumento y moneda, listo para declarar en renta.
- **Reconstrucción FIFO**: Endpoint para recalcular todos los lotes de un instrumento desde cero (útil ante operaciones backdated).
- **Refresco de precios de mercado**: Integración con TwelveData API para actualizar precios automáticamente (cron configurable).
- **Tipos de cambio ECB**: Carga diaria automática de las tasas EUR publicadas por el Banco Central Europeo. Backfill automático al arranque de los últimos 90 días laborables con huecos, más endpoint on-demand para un día concreto.
- **Multi-tenancy**: Aislamiento completo de datos por `tenant_id` extraído del JWT de Keycloak.
- **Seguridad**: OAuth2 Resource Server con Keycloak como IdP.

---

## 🏛️ Arquitectura

### Tipo de aplicación

Microservicio REST (Spring Boot). Forma parte de un sistema multi-módulo Maven gestionado por un `pom.xml` raíz.

### Organización de paquetes

```
es.triana.company.investments
├── controller/          # Capa REST — InvestmentsController, OperationsController
│   └── InvestmentExceptionHandler  # @RestControllerAdvice global
├── model/
│   ├── api/             # DTOs de entrada/salida (records y @Data/@Builder)
│   └── db/              # Entidades JPA (Lombok @Entity)
├── repository/          # Spring Data JPA repositories
├── security/            # TenantContext — extrae tenant_id del JWT Keycloak
└── service/             # Lógica de negocio
    ├── InvestmentService.java
    ├── OperationService.java
    ├── PriceRefreshService.java
    ├── ExchangeRateRefreshService.java
    ├── EcbExchangeRateClient.java
    ├── MarketPriceClient.java
    └── exception/       # InvestmentNotFoundException, InvestmentValidationException
```

### Responsabilidad de cada capa

| Capa | Responsabilidad |
|---|---|
| `controller` | Recibe peticiones HTTP, extrae `tenant_id` del token, delega en servicios, construye `ApiResponse<T>` |
| `service` | Toda la lógica de negocio: validaciones, FIFO, conversión de divisas, cálculos fiscales, llamadas externas |
| `repository` | Acceso a datos via Spring Data JPA; queries JPQL complejas con PESSIMISTIC_WRITE donde se necesita serializar escrituras concurrentes |
| `model/db` | Entidades JPA mapeadas al esquema PostgreSQL `investments` |
| `model/api` | DTOs que cruzan la frontera HTTP; records Java para inmutabilidad |
| `security` | `TenantContext` — componente auxiliar que lee el claim `tenant_id` del JWT |

### Flujo de una petición SELL

```
POST /v1/api/investments/operations
         │
         ▼
OperationsController.register()
  └─ extrae tenantId del JWT (TenantContext)
  └─ crea CreateOperationRequest con tenantId seguro (anti-spoofing)
         │
         ▼
OperationService.registerOperation()
  ├─ SELECT ... FOR UPDATE en Investment (evita race conditions)
  ├─ validateFifoCoverage() — comprueba stock disponible
  ├─ resolveEurRate() — busca la tasa ECB más cercana a la fecha
  ├─ persiste InvestmentOperation
  ├─ applyFifo() — consume lotes BUY, persiste OperationFifoLot
  └─ updateInvestmentPosition() — ajusta quantity e investedAmount
         │
         ▼
ApiResponse<OperationDTO> con lotes FIFO incluidos
```

### Patrones usados

- **Multi-tenancy por claim JWT**: Ningún endpoint acepta `tenantId` del body/path; siempre se extrae del token.
- **Pessimistic locking**: `SELECT ... FOR UPDATE` en `Investment` e `InvestmentOperation` para serializar operaciones concurrentes sobre la misma posición.
- **Upsert pattern**: Para tipos de cambio — si ya existe el par `(from, to, asOf)` se actualiza, si no se inserta.
- **Builder + DTO/entity separation**: Entidades no se exponen directamente en la API.
- **Event-driven startup**: `@EventListener(ApplicationReadyEvent.class)` para el backfill de divisas al arranque.
- **Strategy / SPI**: `MarketPriceClient` es una interfaz — permite sustituir el proveedor de precios sin modificar `PriceRefreshService`.

### Integraciones externas

| Integración | Protocolo | Descripción |
|---|---|---|
| **Keycloak** | OAuth2 / OIDC | Validación de JWT; claim `tenant_id` para multi-tenancy |
| **ECB XML Feed** | HTTPS/XML | Tasas de referencia EUR diarias y de los últimos 90 días |
| **TwelveData API** | HTTPS/JSON | Precios de mercado (acciones, ETFs, criptomonedas) |

---

## 🛠️ Tecnologías usadas

| Categoría | Tecnología | Versión |
|---|---|---|
| Lenguaje | Java | 21 |
| Framework | Spring Boot | 4.0.1 |
| Seguridad | Spring Security + OAuth2 Resource Server | (BOM) |
| Persistencia | Spring Data JPA + Hibernate | (BOM) |
| Base de datos | PostgreSQL | 17+ |
| Migraciones | Flyway | (BOM) |
| Herramientas | Lombok | (BOM) |
| Validación | Jakarta Bean Validation | (BOM) |
| Monitoreo | Spring Actuator | (BOM) |
| Build | Maven (wrapper incluido) | 3.9+ |
| Contenedor | Docker + Eclipse Temurin 21 JRE Alpine | — |
| Testing | JUnit 5, Mockito 5, Spring Test, H2 | — |

---

## ✅ Requisitos previos

| Requisito | Versión mínima |
|---|---|
| Java | 21 |
| Maven | 3.9+ (o usar `./mvnw` incluido) |
| Docker + Docker Compose | 24+ |
| PostgreSQL | 14+ (o via Docker) |
| Keycloak | 24+ (o via Docker — ver `infra/keycloak/`) |

> **Nota**: Para desarrollo local sin Docker se necesita una instancia de PostgreSQL con la base de datos `finance_db` creada y el schema `investments` accesible con las credenciales configuradas.

---

## 🚀 Instalación

### 1. Clonar el repositorio

```bash
git clone https://github.com/tu-org/finance-app.git
cd finance-app
```

### 2. Configurar variables de entorno

```bash
cp investments/.env.example investments/.env
# Editar investments/.env con los valores reales
```

### 3. Levantar infraestructura (PostgreSQL + Keycloak)

```bash
# Desde la raíz del proyecto
cd infra/db && docker compose up -d
cd ../keycloak && docker compose up -d
```

### 4. Instalar dependencias y compilar

```bash
# Desde la raíz del monorepo
./mvnw -pl investments clean package -DskipTests
```

---

## ⚙️ Configuración

### application.properties

```properties
# Server
server.port=${SERVER_PORT:8082}
spring.application.name=investments

# Base de datos
spring.datasource.url=${DATABASE_URL}
spring.datasource.username=${DATABASE_USERNAME}
spring.datasource.password=${DATABASE_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.default_schema=${DATABASE_SCHEMA:investments}
spring.jpa.properties.hibernate.jdbc.time_zone=UTC

# Flyway
spring.flyway.enabled=true
spring.flyway.schemas=${DATABASE_SCHEMA:investments}
spring.flyway.default-schema=${DATABASE_SCHEMA:investments}
spring.flyway.locations=classpath:db/migration
spring.flyway.baseline-on-migrate=true

# Precios de mercado (TwelveData)
investments.prices.auto-refresh-cron=${INVESTMENTS_PRICES_AUTO_REFRESH_CRON:0 0 */12 * * *}
investments.prices.auto-refresh-excluded-type-codes=${INVESTMENTS_PRICES_AUTO_REFRESH_EXCLUDED_TYPE_CODES:FUND,ETF}
investments.prices.crypto.quote-currency=${INVESTMENTS_PRICES_CRYPTO_QUOTE_CURRENCY:USD}
investments.prices.providers.twelvedata.time-series-url=${INVESTMENTS_PRICES_PROVIDER_TWELVEDATA_TIME_SERIES_URL}
investments.prices.providers.twelvedata.api-key=${INVESTMENTS_PRICES_PROVIDER_TWELVEDATA_API_KEY}
investments.prices.providers.twelvedata.interval=${INVESTMENTS_PRICES_PROVIDER_TWELVEDATA_INTERVAL:1day}
investments.prices.providers.twelvedata.outputsize=${INVESTMENTS_PRICES_PROVIDER_TWELVEDATA_OUTPUTSIZE:1}

# Tipos de cambio ECB
investments.exchange-rates.refresh-cron=${INVESTMENTS_EXCHANGE_RATES_REFRESH_CRON:0 30 15 * * MON-FRI}
investments.exchange-rates.providers.ecb.daily-url=${INVESTMENTS_EXCHANGE_RATES_PROVIDER_ECB_DAILY_URL}
investments.exchange-rates.providers.ecb.hist90d-url=${INVESTMENTS_EXCHANGE_RATES_PROVIDER_ECB_HIST90D_URL}

# Actuator
management.endpoints.web.exposure.include=health,info

# Logging
logging.level.root=INFO
logging.level.es.triana.company.investments=DEBUG
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} %-5level %logger{36} - %msg%n
logging.file.name=investments.log
```

### Variables de entorno

| Variable | Requerida | Default | Descripción |
|---|---|---|---|
| `SERVER_PORT` | No | `8082` | Puerto HTTP del servicio |
| `DATABASE_URL` | **Sí** | — | JDBC URL de PostgreSQL |
| `DATABASE_USERNAME` | **Sí** | — | Usuario de base de datos |
| `DATABASE_PASSWORD` | **Sí** | — | Contraseña de base de datos |
| `DATABASE_SCHEMA` | No | `investments` | Schema PostgreSQL |
| `INVESTMENTS_PRICES_AUTO_REFRESH_CRON` | No | `0 0 */12 * * *` | Cron de refresco automático de precios |
| `INVESTMENTS_PRICES_AUTO_REFRESH_EXCLUDED_TYPE_CODES` | No | `FUND,ETF` | Tipos excluidos del refresco automático (CSV por código de tipo) |
| `INVESTMENTS_PRICES_CRYPTO_QUOTE_CURRENCY` | No | `USD` | Moneda de cotización para criptomonedas |
| `INVESTMENTS_PRICES_PROVIDER_TWELVEDATA_TIME_SERIES_URL` | **Sí** | — | URL de la API TwelveData |
| `INVESTMENTS_PRICES_PROVIDER_TWELVEDATA_API_KEY` | **Sí** | — | API Key de TwelveData |
| `INVESTMENTS_PRICES_PROVIDER_TWELVEDATA_INTERVAL` | No | `1day` | Intervalo de la serie temporal |
| `INVESTMENTS_PRICES_PROVIDER_TWELVEDATA_OUTPUTSIZE` | No | `1` | Número de velas a obtener |
| `INVESTMENTS_EXCHANGE_RATES_REFRESH_CRON` | No | `0 30 15 * * MON-FRI` | Cron de refresco de divisas |
| `INVESTMENTS_EXCHANGE_RATES_PROVIDER_ECB_DAILY_URL` | **Sí** | — | URL feed XML ECB diario |
| `INVESTMENTS_EXCHANGE_RATES_PROVIDER_ECB_HIST90D_URL` | **Sí** | — | URL feed XML ECB 90 días |

### Archivo `.env` de ejemplo

```dotenv
SERVER_PORT=8082
DATABASE_URL=jdbc:postgresql://finance-db:5432/finance_db
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres
DATABASE_SCHEMA=investments
INVESTMENTS_PRICES_AUTO_REFRESH_CRON=0 0 */12 * * *
INVESTMENTS_PRICES_AUTO_REFRESH_EXCLUDED_TYPE_CODES=FUND,ETF
INVESTMENTS_PRICES_CRYPTO_QUOTE_CURRENCY=USD
INVESTMENTS_PRICES_PROVIDER_TWELVEDATA_TIME_SERIES_URL=https://api.twelvedata.com/time_series
INVESTMENTS_PRICES_PROVIDER_TWELVEDATA_API_KEY=<TU_API_KEY>
INVESTMENTS_PRICES_PROVIDER_TWELVEDATA_INTERVAL=1day
INVESTMENTS_PRICES_PROVIDER_TWELVEDATA_OUTPUTSIZE=1
INVESTMENTS_EXCHANGE_RATES_REFRESH_CRON=0 30 15 * * MON-FRI
INVESTMENTS_EXCHANGE_RATES_PROVIDER_ECB_DAILY_URL=https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml
INVESTMENTS_EXCHANGE_RATES_PROVIDER_ECB_HIST90D_URL=https://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist-90d.xml
```

---

## ▶️ Ejecución local

### Maven (desde la raíz del monorepo)

```bash
# Solo el módulo investments
./mvnw -pl investments spring-boot:run

# Con perfil de desarrollo y variables de entorno cargadas desde .env
set -a && source investments/.env && set +a
./mvnw -pl investments spring-boot:run
```

### IntelliJ IDEA

1. Importar el proyecto como **Maven multi-módulo**.
2. Crear una **Run Configuration** apuntando a `InvestmentsApplication`.
3. En la sección *Environment Variables*, cargar los valores del `.env`.

### VS Code

1. Instalar la extensión **Spring Boot Dashboard** y **Java Extension Pack**.
2. El proyecto se detecta automáticamente.
3. Configurar las variables de entorno en `.vscode/launch.json`:

```json
{
  "configurations": [
    {
      "type": "java",
      "name": "InvestmentsApplication",
      "request": "launch",
      "mainClass": "es.triana.company.investments.InvestmentsApplication",
      "envFile": "${workspaceFolder}/investments/.env"
    }
  ]
}
```

---

## 🐳 Ejecución con Docker

### Dockerfile

El `Dockerfile` del módulo usa un build multi-stage:

```dockerfile
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY ../pom.xml ./pom.xml
COPY ../investments /app/investments
RUN mvn -f investments/pom.xml -B -U -DskipTests package spring-boot:repackage

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/investments/target/investments-*.jar /app/app.jar
EXPOSE 8082
ENTRYPOINT ["sh","-c","java ${JAVA_OPTS:- -Xms256m -Xmx512m} -jar /app/app.jar"]
```

### Build y ejecución manual

```bash
# Desde la raíz del monorepo
docker build -f investments/Dockerfile -t investments:local .
docker run --rm -p 8082:8082 --env-file investments/.env investments:local
```

### Docker Compose (stack completo)

```bash
# Desde la raíz del monorepo
docker compose up --build
```

Este comando levanta `banking`, `investments` y `frontend` en la red `finance-net` (debe existir previamente):

```bash
docker network create finance-net
```

El `docker-compose.yml` del proyecto:

```yaml
services:
  investments:
    build:
      context: .
      dockerfile: investments/Dockerfile
    env_file:
      - ./investments/.env
    ports:
      - "8082:8082"
    networks:
      - finance-net

networks:
  finance-net:
    external: true
```

> **Tip**: Para levantar solo la infraestructura (DB + Keycloak), ver `infra/db/` e `infra/keycloak/`.

---

## 🌐 API REST

Base URL: `http://localhost:8082/v1/api/investments`

Todos los endpoints requieren un JWT válido emitido por Keycloak en la cabecera `Authorization: Bearer <token>`.

Las respuestas siguen el wrapper estándar:

```json
{
  "status": 200,
  "message": "Investment retrieved successfully",
  "data": { ... }
}
```

### Endpoints

#### Inversiones (posiciones)

| Método | Path | Descripción |
|---|---|---|
| `GET` | `/` | Lista todas las posiciones del tenant autenticado |
| `GET` | `/{id}` | Obtiene una posición por ID |
| `GET` | `/summary` | Resumen global: total invertido, valor actual, P&L, desglose por tipo |
| `POST` | `/` | Crea una nueva posición de inversión |
| `PUT` | `/{id}` | Actualiza una posición existente |
| `DELETE` | `/{id}` | Elimina una posición |

#### Operaciones BUY/SELL

| Método | Path | Descripción |
|---|---|---|
| `POST` | `/operations` | Registra una operación BUY o SELL (FIFO automático en SELL) |
| `GET` | `/operations` | Lista todas las operaciones del tenant |
| `GET` | `/operations/by-investment?investmentId=X` | Operaciones de una posición concreta |
| `GET` | `/operations/tax-summary?year=YYYY` | Resumen fiscal del año (ganancia/pérdida realizada) |
| `POST` | `/operations/rebuild-fifo?instrumentId=X` | Reconstruye lotes FIFO de un instrumento desde cero |

#### Precios de mercado

| Método | Path | Descripción |
|---|---|---|
| `POST` | `/prices/refresh` | Refresco manual de precios para una lista de instrumentos |
| `POST` | `/prices/refresh/auto` | Lanza el refresco automático ahora (igual que el cron) |

#### Tipos de cambio (forex)

| Método | Path | Descripción |
|---|---|---|
| `POST` | `/forex/refresh-day?asOf=YYYY-MM-DD` | Carga y persiste las tasas ECB de un día específico |

### Ejemplo: Registrar operación BUY

```bash
curl -X POST http://localhost:8082/v1/api/investments/operations \
  -H "Authorization: Bearer <TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "investmentId": 1,
    "tenantId": 1,
    "type": "BUY",
    "operationDate": "2026-04-15",
    "quantity": 10,
    "unitPrice": 175.50,
    "fees": 1.95,
    "currency": "USD",
    "notes": "Compra inicial AAPL"
  }'
```

Respuesta:

```json
{
  "status": 201,
  "message": "Operation registered successfully",
  "data": {
    "id": 42,
    "investmentId": 1,
    "tenantId": 1,
    "type": "BUY",
    "operationDate": "2026-04-15",
    "quantity": 10.0,
    "unitPrice": 175.50,
    "fees": 1.95,
    "totalAmount": 1756.95,
    "currency": "USD",
    "eurExchangeRate": 1.0823,
    "totalAmountEur": 1623.34,
    "fifoLots": []
  }
}
```

### Ejemplo: Resumen fiscal

```bash
curl http://localhost:8082/v1/api/investments/operations/tax-summary?year=2025 \
  -H "Authorization: Bearer <TOKEN>"
```

Respuesta:

```json
{
  "status": 200,
  "message": "Tax summary retrieved successfully",
  "data": {
    "tenantId": 1,
    "year": 2025,
    "totalGainLossEur": 1234.5600,
    "byInstrument": [
      {
        "instrumentId": 3,
        "code": "AAPL",
        "symbol": "AAPL",
        "name": "Apple Inc.",
        "gainLossEur": 987.1200
      }
    ],
    "byCurrency": [
      {
        "currency": "USD",
        "gainLossEur": 1234.5600
      }
    ]
  }
}
```

### Códigos HTTP de respuesta

| Código | Descripción |
|---|---|
| `200 OK` | Consulta o actualización exitosa |
| `201 Created` | Recurso creado correctamente |
| `400 Bad Request` | Parámetros inválidos o validación fallida |
| `401 Unauthorized` | Token ausente o inválido |
| `403 Forbidden` | Token válido pero sin permisos |
| `404 Not Found` | Recurso no encontrado (o no pertenece al tenant) |
| `500 Internal Server Error` | Error inesperado del servidor |

---

## 🔒 Seguridad

### Autenticación

El microservice actúa como **OAuth2 Resource Server**. Todas las peticiones deben incluir un JWT válido firmado por Keycloak:

```
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

El JWT es validado automáticamente por Spring Security contra el JWKS endpoint de Keycloak.

### Multi-tenancy

El `TenantContext` extrae el claim `tenant_id` del JWT en cada petición. Este valor se usa en **todas** las consultas como filtro de aislamiento. Nunca se acepta del cuerpo de la petición (el controlador de operaciones sobreescribe el `tenantId` del request con el del token).

```
JWT claim: tenant_id (Number | String) → Long tenantId
```

### Roles

La gestión de roles se delega completamente en Keycloak. Spring Security valida la firma del token; la aplicación solo confía en el claim `tenant_id`.

### Buenas prácticas aplicadas

- `spring.jpa.open-in-view=false` — evita lazy loading en la capa web.
- Contraseñas y API keys gestionadas via variables de entorno (nunca hardcoded).
- XXE Protection activada en el parser XML del cliente ECB.
- Pessimistic locking para prevenir race conditions en operaciones concurrentes.

---

## 🗄️ Base de datos

### Motor

**PostgreSQL** (schema `investments` dentro de la base de datos `finance_db`).

### Sistema de migraciones: Flyway

| Migración | Descripción |
|---|---|
| `V1__init.sql` | Schema inicial: tipos de inversión, plataformas, instrumentos, precios, posiciones |
| `V2__exchange_rates.sql` | Tabla `exchange_rates` con índices y constraint de unicidad por par+fecha |
| `V3__investment_operations.sql` | Tabla `investment_operations` y tabla `operation_fifo_lots` para matching FIFO |

### Modelo de datos simplificado

```
investment_types ──────────────────────────────┐
investment_platforms ───────────────────────────┤
investment_instruments (type_id FK) ────────────┤
                                                 ▼
                              investments (tenant_id, type_id FK, instrument_id FK, platform_id FK)
                                                 │
                              investment_operations (investment_id FK, tenant_id)
                                                 │
                              operation_fifo_lots (sell_operation_id FK, buy_operation_id FK)

exchange_rates (from_currency, to_currency, as_of) — independent
prices (instrument_id FK)                          — independent
```

### Tablas principales

| Tabla | Descripción |
|---|---|
| `investment_types` | Catálogo: FUND, ETF, CRYPTO, STOCK |
| `investment_platforms` | Catálogo: MYINVESTOR, IBKR… |
| `investment_instruments` | Instrumentos financieros (símbolo, mercado, moneda) |
| `investments` | Posiciones del portfolio por tenant |
| `prices` | Histórico de precios por instrumento |
| `investment_operations` | Operaciones BUY/SELL con tipo de cambio denormalizado |
| `operation_fifo_lots` | Emparejamiento FIFO sell→buy con ganancia/pérdida en EUR |
| `exchange_rates` | Tasas de cambio EUR de la fuente ECB |

### Índices relevantes

```sql
-- Aislamiento multi-tenant
idx_investments_tenant_id         ON investments(tenant_id)
idx_operations_tenant             ON investment_operations(tenant_id)

-- Consultas FIFO
idx_fifo_sell                     ON operation_fifo_lots(sell_operation_id)
idx_fifo_buy                      ON operation_fifo_lots(buy_operation_id)

-- Forex lookups
idx_exchange_rates_pair_date      ON exchange_rates(from_currency, to_currency, as_of)
```

### Consideraciones de rendimiento

- La constraint `uq_exchange_rates_pair_date` facilita el patrón upsert sin full-scan.
- `investment_operations` tiene el campo `eur_exchange_rate` denormalizado: los registros fiscales son inmunes a la purga de la tabla de divisas.
- Las consultas FIFO usan `PESSIMISTIC_WRITE` para evitar phantom reads sin necesitar serializable isolation.

---

## 🧪 Testing

### Estructura

```
src/test/java/
└── es.triana.company.investments
    ├── InvestmentsApplicationTests.java   # Context load test (requiere .env completo)
    └── service/
        └── OperationServiceTest.java      # 18 tests unitarios con H2 in-memory
            ├── BuyOperations              # 5 tests
            ├── SellOperations             # 5 tests
            ├── ErrorCases                 # 4 tests
            ├── FifoRebuild                # 2 tests
            └── TaxSummary                 # 2 tests
```

### Ejecutar los tests

```bash
# Todos los tests del módulo
./mvnw -pl investments test

# Solo una clase
./mvnw -pl investments test -Dtest=OperationServiceTest

# Omitir tests de contexto (requieren variables de entorno externas)
./mvnw -pl investments test -Dtest=OperationServiceTest#BuyOperations
```

### Base de datos de test

Los tests de servicio usan **H2 in-memory** en modo PostgreSQL:

```properties
spring.datasource.url=jdbc:h2:mem:investments-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE
spring.jpa.hibernate.ddl-auto=create-drop
spring.flyway.enabled=false
```

### Estado actual

| Suite | Tests | Estado |
|---|---|---|
| `OperationServiceTest` | 18 | ✅ Pasando |
| `InvestmentsApplicationTests` | 1 | ⚠️ Requiere variables externas completas (TwelveData URL) |

---

## 📊 Logs y monitoreo

### Formato de log

```
2026-05-01 15:32:10 INFO  e.t.c.i.s.ExchangeRateRefreshService - Exchange rates startup check found 3 missing business days in last 90 days — backfilling
2026-05-01 15:32:11 INFO  e.t.c.i.s.ExchangeRateRefreshService - Exchange rates startup backfill finished — 87 rates upserted, 0 business days unresolved
2026-05-01 15:32:15 INFO  e.t.c.i.s.OperationService - Registered SELL operation id=42 investment=1 quantity=5.0000000000 totalEur=876.5400
```

### Niveles configurados

| Logger | Nivel |
|---|---|
| `root` | `INFO` |
| `es.triana.company.investments` | `DEBUG` |

El log se escribe simultáneamente en consola y en archivo `investments.log`.

### Spring Actuator

Los siguientes endpoints están expuestos:

```
GET /actuator/health   → Estado de la aplicación y datasource
GET /actuator/info     → Información del build
```

Ejemplo de respuesta de salud:

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

---

## 🔄 CI/CD

No existe pipeline configurado en este repositorio actualmente. A continuación se propone una estrategia estándar:

### Pipeline sugerido (GitHub Actions)

```yaml
name: investments-ci
on:
  push:
    paths:
      - 'investments/**'
      - 'pom.xml'
  pull_request:
    paths:
      - 'investments/**'

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: ./mvnw -pl investments clean verify
      
  docker-build:
    needs: build-and-test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - uses: actions/checkout@v4
      - run: docker build -f investments/Dockerfile -t investments:${{ github.sha }} .
```

### Estrategia de ramas sugerida

| Rama | Propósito |
|---|---|
| `main` | Producción — merges via PR únicamente |
| `develop` | Integración continua |
| `feature/<descripcion>` | Nuevas funcionalidades |
| `fix/<descripcion>` | Correcciones de bugs |

---

## 📁 Estructura del proyecto

```
investments/
├── .env                          # Variables de entorno locales (no commitear)
├── Dockerfile                    # Build multi-stage para producción
├── pom.xml                       # Dependencias del módulo
└── src/
    ├── main/
    │   ├── java/es/triana/company/investments/
    │   │   ├── InvestmentsApplication.java        # Entry point de Spring Boot
    │   │   ├── controller/
    │   │   │   ├── InvestmentsController.java     # CRUD posiciones + precios + forex
    │   │   │   ├── OperationsController.java      # BUY/SELL, FIFO, fiscal
    │   │   │   └── InvestmentExceptionHandler.java# Manejo global de errores
    │   │   ├── model/
    │   │   │   ├── api/                           # DTOs (records + @Data/@Builder)
    │   │   │   └── db/                            # Entidades JPA
    │   │   ├── repository/                        # Spring Data JPA repositories
    │   │   ├── security/
    │   │   │   └── TenantContext.java             # Extrae tenant_id del JWT
    │   │   └── service/
    │   │       ├── InvestmentService.java         # CRUD e inversiones
    │   │       ├── OperationService.java          # BUY/SELL, FIFO, tax summary
    │   │       ├── PriceRefreshService.java       # Refresco de precios de mercado
    │   │       ├── ExchangeRateRefreshService.java# Tasas de cambio ECB
    │   │       ├── EcbExchangeRateClient.java     # Cliente HTTP para ECB XML feed
    │   │       ├── MarketPriceClient.java         # Interfaz para proveedor de precios
    │   │       └── exception/
    │   │           ├── InvestmentNotFoundException.java
    │   │           └── InvestmentValidationException.java
    │   └── resources/
    │       ├── application.properties
    │       └── db/migration/                      # Migraciones Flyway V1–V3
    └── test/
        ├── java/.../
        │   ├── InvestmentsApplicationTests.java
        │   └── service/OperationServiceTest.java
        └── resources/application.properties      # Config H2 para tests
```

---

## 🔧 Troubleshooting

### Puerto 8082 ya en uso

```bash
# Windows
netstat -ano | findstr :8082
taskkill /PID <PID> /F

# Linux/Mac
lsof -i :8082
kill -9 <PID>
```

### Error de conexión a la base de datos

Verificar que:
1. PostgreSQL está apuntando al host correcto en `DATABASE_URL`.
2. El schema `investments` existe: `CREATE SCHEMA investments;`
3. El usuario tiene permisos: `GRANT ALL ON SCHEMA investments TO postgres;`
4. La red Docker `finance-net` está creada: `docker network create finance-net`

### Variables de entorno faltantes al arrancar

El log mostrará algo como:
```
java.lang.IllegalArgumentException: Could not resolve placeholder 'INVESTMENTS_PRICES_PROVIDER_TWELVEDATA_TIME_SERIES_URL'
```

Solución: asegurarse de que el archivo `.env` está cargado o las variables están disponibles en el entorno del proceso.

### Flyway: schema "investments" not found

```
FlywayException: Unable to obtain connection from database
```

Crear el schema manualmente antes del primer arranque:

```sql
CREATE SCHEMA IF NOT EXISTS investments;
```

O asegurarse de que `baseline-on-migrate=true` está configurado y el usuario tiene permisos DDL.

### FIFO: "Insufficient stock" al registrar una venta

El sistema comprueba que la `quantity` total de BUYs no consumidos sea ≥ a la cantidad a vender. Verificar que:
1. La posición tiene suficientes unidades en `investments.quantity`.
2. No hay lotes FIFO huérfanos. Si es así, usar `POST /operations/rebuild-fifo?instrumentId=X`.

### Tipos de cambio no encontrados para una fecha antigua

Si una operación histórica no tiene tasa ECB disponible, el sistema usa la última tasa disponible hacia atrás. Para cargar tasas de un día específico:

```bash
curl -X POST "http://localhost:8082/v1/api/investments/forex/refresh-day?asOf=2025-03-15" \
  -H "Authorization: Bearer <TOKEN>"
```

El feed ECB histórico solo cubre los últimos ~90 días. Para fechas anteriores se necesita una fuente externa.

---

## 🚧 Mejoras futuras

### Técnicas

- [ ] Añadir OpenAPI/Swagger (`springdoc-openapi`) para documentación auto-generada de la API.
- [ ] Crear `SecurityConfig` explícita para configurar reglas de autorización por rol (actualmente todo acceso autenticado es válido).
- [ ] Implementar `Default*` para las interfaces de servicio existentes (`ExchangeRateResolver`, `FifoCalculator`, `OperationValidator`, `TaxSummaryCalculator`) — facilita el testing y extensibilidad.
- [ ] Cache en `ExchangeRateRepository` para lookups frecuentes de tasas históricas (Caffeine + `@Cacheable`).
- [ ] Soporte a fuentes históricas de forex para fechas > 90 días (ej. Open Exchange Rates, Fixer.io).
- [ ] Añadir tests de integración con Testcontainers (PostgreSQL real) para los flujos FIFO y migraciones Flyway.
- [ ] Métricas custom con Micrometer (número de operaciones registradas, latencia de llamadas ECB/TwelveData).

### Funcionales

- [ ] Soporte a dividendos como tipo de operación (`DIVIDEND`).
- [ ] Splits de acciones con recálculo automático de lotes FIFO.
- [ ] Valoración de cartera en tiempo real con soporte multi-divisa (conversión al vuelo a EUR).
- [ ] Export a CSV/PDF del resumen fiscal para declaración de renta.
- [ ] Notificaciones (email/push) ante variaciones de precio significativas.

---

## 🤝 Contribución

### Convención de ramas

```
feature/descripcion-corta
fix/descripcion-del-bug
refactor/descripcion
```

### Pull Requests

1. Crear rama desde `develop`.
2. Asegurarse de que `./mvnw -pl investments test` pasa completamente.
3. Abrir PR contra `develop` con descripción del cambio y tests añadidos/modificados.
4. Requerir al menos 1 revisión aprobada antes de merge.

### Estilo de código

- Sigue las convenciones estándar de Java (nombres en camelCase, clases en PascalCase).
- No añadir lógica de negocio en los controladores.
- Los DTOs de la capa API deben ser records o usar Lombok para reducir boilerplate.
- Cualquier query JPQL no trivial debe ir documentada con su propósito.

---

## 📄 Licencia

Este proyecto es de uso personal/educativo. No tiene licencia open source asignada actualmente.

---

*Generado el 2026-05-01 | finance-app v0.0.1-SNAPSHOT | Java 21 + Spring Boot 4.0.1*
