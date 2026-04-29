#!/bin/bash

set -euo pipefail

# Crear la red externa si no existe
docker network inspect finance-net >/dev/null 2>&1 || docker network create finance-net

# Parar y eliminar contenedores de cada servicio si están activos
(cd infra/db && docker-compose down)
(cd infra/keycloak && docker-compose down)
(cd infra/rabbitmq && docker-compose down)
docker-compose -f docker-compose.yml down

# Iniciar los servicios
(cd infra/db && docker-compose up --build -d)
(cd infra/keycloak && docker-compose up --build -d)
(cd infra/rabbitmq && docker-compose up --build -d)
docker-compose -f docker-compose.yml up --build -d

echo "All services are up and running."
echo "Frontend available on http://localhost:${FRONTEND_PORT}"

sleep 10
