#!/bin/bash

set -euo pipefail

# Stop application stack first
docker compose -f docker-compose.yml -f docker-compose.prod.yml down

# Stop infrastructure services
(cd infra/rabbitmq && docker compose down)
(cd infra/keycloak && docker compose down)
(cd infra/db && docker compose down)

echo "Production stack is down."
