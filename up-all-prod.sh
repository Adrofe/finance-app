#!/bin/bash

set -euo pipefail

# Ensure shared external network exists
if ! docker network inspect finance-net >/dev/null 2>&1; then
  docker network create finance-net
fi

# Start infrastructure services
(cd infra/db && docker compose up -d)
(cd infra/keycloak && docker compose up -d)
(cd infra/rabbitmq && docker compose up -d)

# Start application stack with production overlay (includes Caddy/HTTPS)
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build

echo "Production stack is up."
