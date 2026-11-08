# Shared PostgreSQL database

The project uses one PostgreSQL 18 instance shared by the EventFlow services. The
database runs independently in Docker; application services can run either on
the host or later as containers on the same Compose network.

PostgreSQL data is persisted in the ignored local directory `database/data/`.
This bind mount is intentional because the Docker daemon stores named volumes
on a filesystem that does not support the Unix permissions required by
PostgreSQL. The container uses PostgreSQL 18's versioned data layout under
`/var/lib/postgresql`.

## Start the database

Copy the environment template once, then start PostgreSQL:

```bash
cp .env.example .env
docker-compose up -d postgres
docker-compose ps
```

The default local connection is:

```text
Host: localhost
Port: 5433
Database: eventflow
Username: eventflow
Password: eventflow_dev_password
```

Applications running inside the Compose network must use `postgres` instead of
`localhost` as the host.

## Service connection formats

Spring Boot JDBC:

```text
DATABASE_URL=jdbc:postgresql://localhost:5433/eventflow
DATABASE_USERNAME=eventflow
DATABASE_PASSWORD=eventflow_dev_password
```

Node.js and Python:

```text
DATABASE_URL=postgresql://eventflow:eventflow_dev_password@localhost:5433/eventflow
```

ASP.NET Core / Npgsql:

```text
Host=localhost;Port=5433;Database=eventflow;Username=eventflow;Password=eventflow_dev_password;SSL Mode=Disable
```

## Initialization

On the first startup, PostgreSQL executes these files in order:

1. `init/001_schema.sql` creates the shared schema, constraints, indexes, and
   update trigger.
2. `init/002_seed.sql` inserts organizer `1` and event `1` for cross-service
   development.

Initialization scripts only run when `database/data/` is empty. During local
development, if the schema scripts change, stop PostgreSQL and move the current
data directory to a backup before starting it again:

```bash
docker-compose down
mv database/data database/data.backup
docker-compose up -d postgres
```

The next startup creates a fresh database. The previous data remains recoverable
in `database/data.backup` until the team deliberately removes that backup.

## Useful commands

```bash
# Follow PostgreSQL logs
docker-compose logs -f postgres

# Open psql inside the container
docker-compose exec postgres psql -U eventflow -d eventflow

# Stop the container without deleting data
docker-compose stop postgres
```
