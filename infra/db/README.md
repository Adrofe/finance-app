# Database Infrastructure (infra/db)

This directory contains the PostgreSQL database configuration for the project.

## Environment Variables

Database credentials and parameters are managed using environment variables. Do not commit sensitive values to the repository. Use the `.env.example` file as a template:

1. Copy `infra/db/.env.example` to `infra/db/.env`.
2. Edit `infra/db/.env` with your real values.

Required variables:

- `POSTGRES_USER`: PostgreSQL superuser username
- `POSTGRES_PASSWORD`: PostgreSQL superuser password
- `POSTGRES_DB`: Name of the database to create when the container starts

The `.env` file is included in `.gitignore` and will not be committed to the repository.

## Using Docker Compose

To start the database:

```sh
docker-compose up -d
```

To stop it:

```sh
docker-compose down
```
