# Keycloak Infrastructure

This directory contains the configuration for running a Keycloak instance using Docker Compose.

## Environment Variables

Credentials and configuration are managed via environment variables. Do not commit sensitive values to the repository. Use the provided `.env.example` as a template:

1. Copy `.env.example` to `.env`.
2. Edit `.env` with your real values.

Required variables:

- `KEYCLOAK_ADMIN`: Keycloak admin username
- `KEYCLOAK_ADMIN_PASSWORD`: Keycloak admin password

The `.env` file is listed in `.gitignore` and will not be committed to the repository.

## Usage

To start Keycloak:

```sh
docker-compose up -d
```

To stop Keycloak:

```sh
docker-compose down
```
