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

Never commit real database credentials.

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

Tests use an in-memory H2 database and do not connect to Supabase.
