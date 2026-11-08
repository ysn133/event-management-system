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
- `EVENTS_VALIDATION_MODE` (defaults to `mock` until the Events API is available)

Never commit real database credentials.

In mock mode, every positive event ID is treated as an existing event. The
adapter is isolated behind `EventValidationPort` so it can be replaced by an
HTTP integration later.

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
