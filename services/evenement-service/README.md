# Event Service

Spring Boot REST service for creating, retrieving, updating, listing, and
deleting events.

## Requirements

- Java 21
- Maven 3.6.3 or newer
- PostgreSQL

## Configuration

The service reads these environment variables:

- `PORT` (defaults to `5001`)
- `DATABASE_URL` (JDBC PostgreSQL URL)
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `ORGANIZERS_VALIDATION_MODE` (`database` by default, `mock` for local tests)

Never commit real database credentials.

## Run

```bash
mvn spring-boot:run
```

Health endpoint:

```text
GET http://localhost:5001/actuator/health
```

## Test

```bash
mvn test
```

Tests use H2 and do not connect to the shared PostgreSQL database.
