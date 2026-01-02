
# Banking Microservice

## Environment Variable Configuration

Copy the `.env.example` file to `.env` and fill in the values according to your environment:

``` bash
cp .env.example .env
```

Edit `.env` and complete:

- `SERVER_PORT`: Port where the microservice runs (example: 8081)
- `DATABASE_URL`: PostgreSQL database connection URL
- `DATABASE_USERNAME`: Database username
- `DATABASE_PASSWORD`: Database password
- `DATABASE_SCHEMA`: Database schema (example: public)

## Building the Docker Container

From the project root, run:

``` bash
docker build -t banking-app -f banking/Dockerfile .
```

## Running the Container

From the project root, using the banking `.env` file:

``` bash
docker run -it -p 8081:8081 --env-file banking/.env banking-app --name banking-app
```

This will start the microservice with the configuration defined in `.env`.
