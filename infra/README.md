# Infrastructure Overview

This directory contains all infrastructure-related code and configuration for the project.

## Structure

- `db/` — Database infrastructure (PostgreSQL, environment variables, Docker Compose, etc.)
- `keycloak/` — Keycloak identity and access management infrastructure (environment variables, Docker Compose, etc.)
- `rabbitmq/` — RabbitMQ messaging infrastructure (environment variables, Docker Compose, etc.)

## General Guidelines

- Each subdirectory should contain its own README.md with specific setup and usage instructions.
- Sensitive information (such as credentials) must not be committed to the repository. Use environment variable files (e.g., `.env`) and ensure they are listed in `.gitignore`.
- Use Docker Compose or similar tools to manage infrastructure services for local development and testing.

## Getting Started

Refer to each subdirectory's README for detailed setup instructions.

---

For database setup, see [db/README.md](db/README.md).
For Keycloak setup, see [keycloak/README.md](keycloak/README.md).
For RabbitMQ setup, see [rabbitmq/README.md](rabbitmq/README.md).
