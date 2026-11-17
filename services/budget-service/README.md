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

## Budget update rules

Budget updates must cover the current committed total when overspending is
disabled. Pending and paid expenses count; planned and cancelled expenses do
not. Setting the allocation exactly to that total is allowed. A conflicting
update returns `409 BUDGET_EXCEEDED` and leaves the previous amount and
overspending setting unchanged. The same rule applies when turning overspending
off. Updates share the budget lock used by expense, room and provider approvals.

## Authentication

All API requests now require `Authorization: Bearer <JWT>`. Obtain a token from
`POST http://localhost:5005/api/auth/login`. Health checks remain public.
Configure the same `JWT_SECRET` and `JWT_ISSUER` as the Auth service.
See [Authentication service](../auth-service/README.md) for local credentials,
startup and the current authentication scope.

## Financial history

Paid linked expenses survive source cancellation; no refund is inferred.
Historical sources are reviewed in the Budget page's reconciliation panel rather
than auto-matched. See [rules and endpoints](../../docs/EXPENSE_HISTORY.md).
