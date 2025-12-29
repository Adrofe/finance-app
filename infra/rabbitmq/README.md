# RabbitMQ Infrastructure

This directory contains the configuration for running a RabbitMQ instance using Docker Compose.

## Environment Variables

Credentials and configuration are managed via environment variables. Do not commit sensitive values to the repository. Use the provided `.env.example` as a template:

1. Copy `.env.example` to `.env`.
2. Edit `.env` with your real values.

Required variables:

- `RABBITMQ_DEFAULT_USER`: RabbitMQ default username
- `RABBITMQ_DEFAULT_PASS`: RabbitMQ default password

The `.env` file is listed in `.gitignore` and will not be committed to the repository.

## Usage

To start RabbitMQ:

```sh
docker-compose up -d
```

To stop RabbitMQ:

```sh
docker-compose down
```

The management UI will be available at `http://localhost:15672/`.
