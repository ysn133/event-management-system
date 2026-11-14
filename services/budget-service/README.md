# Budget Service

Spring Boot REST service for managing event budgets, expenses, and financial reports.

## Requirements

- Java 21
- Maven 3.6.3 or newer
- PostgreSQL

## Configuration

Copy `.env.example` values into your environment and replace the placeholders.
The application reads the following variables:

- `PORT` (defaults to `5004`)
- `DATABASE_URL` (must be a JDBC PostgreSQL URL)
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `EVENTS_VALIDATION_MODE` (`http` by default, `mock` for isolated tests)
- `EVENT_SERVICE_URL` (defaults to `http://localhost:5001`)
- `FRONTEND_ORIGIN` (defaults to `http://localhost:5173`)

Never commit real database credentials.

Before creating a budget, the service validates its event by calling
`GET {EVENT_SERVICE_URL}/api/evenements/{evenementId}`. The Budget service only
reads and writes the `budgets` and `depenses` tables; it never queries the
`evenements` table directly. Mock mode treats every positive event ID as
existing and is reserved for isolated tests.

## Run

```bash
mvn spring-boot:run
```

The health endpoint is available at:

```text
GET http://localhost:5004/actuator/health
```

## Test

```bash
mvn test
```

Tests use an in-memory H2 database and do not connect to the shared PostgreSQL database.

## Planned backend and database evolution

The detailed target schema, migration strategy, API additions, business rules,
and cross-service integration plan for the enhanced Budget frontend are
documented in [`README_DATABASE_EVOLUTION.md`](README_DATABASE_EVOLUTION.md).
