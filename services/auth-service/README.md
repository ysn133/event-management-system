# Authentication service

Spring Boot service on port **5005**. Owns `app_users` in the shared PostgreSQL
instance. Budget and Event do not query this table.

## Local startup

From the repository root:

```bash
./scripts/run-local.sh
```

The script applies migration `006_auth_users.sql`, starts Auth, Event, Budget,
Salles and the frontend, and generates a persistent signing key in the ignored
`.local/jwt-secret` file. The key is shared by the three Java services.

Development login (created only when the email does not already exist):

- Email: `admin@eventflow.local`
- Password: `EventFlow-dev-2026!`

Override `BOOTSTRAP_USER_EMAIL` and `BOOTSTRAP_USER_PASSWORD` before startup to
choose different credentials. Existing passwords are never overwritten.
The development credentials are for local use. For other environments provide a
private `JWT_SECRET` of at least 32 bytes and your own bootstrap credentials.

For standalone startup, export the variables in `.env.example` (Spring does not
load that file automatically) and run `mvn spring-boot:run` using Java 21.

## Endpoints

| Method | Endpoint | Authentication | Result |
|---|---|---|---|
| POST | `/api/auth/login` | Public | Checks credentials and returns a JWT and user details |
| GET | `/api/auth/me` | Bearer JWT | Returns the current user's public profile |
| GET | `/actuator/health` | Public | Health check |

```bash
curl -X POST http://localhost:5005/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@eventflow.local","password":"EventFlow-dev-2026!"}'
```

Use the returned `accessToken` as `Authorization: Bearer <token>` for Auth,
Budget and Event calls. Tokens expire after one hour; sign in again afterwards.

## Storage and validation

`app_users` contains `id`, unique normalized `email`, `nom` and a BCrypt
`password_hash`. Passwords and hashes never appear in API responses. There is no
public registration endpoint. The bootstrap account is created by Auth at startup.
The existing `organisateurs` table represents event organizers and is independent
of application login accounts.

JWTs use HS256, a common issuer (`eventflow` by default), and an expiry date.
All Budget and Event routes require a valid token except health checks and CORS
preflight. Budget forwards the caller's JWT when validating an event through the
Event API. Teammate services remain unchanged; their callers must integrate JWT
validation before their APIs have equivalent protection.

This release provides shared access to events and budgets for authenticated
users. It does not yet implement organizer ownership, roles, refresh tokens,
password reset or immediate token revocation. Logging out clears the browser
session; an issued token remains valid until expiry.

The frontend stores its token in session storage, adds it only to the Budget
and Event Axios clients, and clears its session on expiry or a 401 response.

## Tests

```bash
mvn test
```

Tests cover valid login, wrong and unknown credentials, hashed password storage,
current-user access, and missing, forged, expired and wrong-issuer JWTs.
