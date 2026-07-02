#!/bin/bash

set -euo pipefail

require_running_service() {
  local service_name="$1"
  shift
  local running_services="$1"

  if ! grep -qx "$service_name" <<< "$running_services"; then
    return 1
  fi

  return 0
}

check_stack_services() {
  local stack_label="$1"
  local list_cmd="$2"
  local logs_cmd_prefix="$3"
  shift 3
  local required_services=("$@")

  local running_services
  running_services="$(eval "$list_cmd" || true)"

  local failed=0
  for svc in "${required_services[@]}"; do
    if ! require_running_service "$svc" "$running_services"; then
      failed=1
      echo "[$stack_label] Service '$svc' is not running." >&2
      echo "[$stack_label] Last logs for '$svc':" >&2
      eval "$logs_cmd_prefix $svc" || true
      echo >&2
    fi
  done

  return "$failed"
}

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

echo "Waiting for services to stabilize..."
sleep 8

db_ok=0
keycloak_ok=0
rabbitmq_ok=0
app_ok=0

if check_stack_services \
  "infra/db" \
  "cd infra/db && docker compose ps --status running --services" \
  "cd infra/db && docker compose logs --tail=120" \
  finance-db; then
  db_ok=1
fi

if check_stack_services \
  "infra/keycloak" \
  "cd infra/keycloak && docker compose ps --status running --services" \
  "cd infra/keycloak && docker compose logs --tail=120" \
  keycloak; then
  keycloak_ok=1
fi

if check_stack_services \
  "infra/rabbitmq" \
  "cd infra/rabbitmq && docker compose ps --status running --services" \
  "cd infra/rabbitmq && docker compose logs --tail=120" \
  rabbitmq; then
  rabbitmq_ok=1
fi

if check_stack_services \
  "app" \
  "docker compose -f docker-compose.yml -f docker-compose.prod.yml ps --status running --services" \
  "docker compose -f docker-compose.yml -f docker-compose.prod.yml logs --tail=120" \
  banking investments wealth budget gateway frontend caddy; then
  app_ok=1
fi

if [[ "$db_ok" -eq 1 && "$keycloak_ok" -eq 1 && "$rabbitmq_ok" -eq 1 && "$app_ok" -eq 1 ]]; then
  echo "Production stack is up and all required services are running."
  exit 0
fi

echo "Production stack started with failures. Check the logs above." >&2
exit 1
