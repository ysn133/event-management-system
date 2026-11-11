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
- `ORGANIZERS_VALIDATION_MODE` (`local` until an Organizer HTTP API is available)
- `FRONTEND_ORIGIN` (defaults to `http://localhost:5173`)

The Event service only reads and writes the `evenements` table. It does not
query the Organizer or Budget tables. Organizer validation is intentionally
kept behind `OrganisateurValidationPort`; the current local adapter accepts a
positive organizer ID and must be replaced by an HTTP adapter when the team
provides an Organizer service contract.

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
