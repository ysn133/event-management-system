# API authentication

All business APIs require `Authorization: Bearer <JWT>`, including room/provider
directory reads, updates, deletes, reservations, contracts and guest occupancy.
Get a token from `POST http://localhost:5005/api/auth/login`.

| Service | Validation |
|---|---|
| Auth, Events, Budget | Spring Security checks HS256 signature, issuer, expiry and required user subject |
| Salles | ASP.NET authentication handler calls Auth `/api/auth/me`; fallback policy protects all controller routes |
| Prestataires | Global `/api` middleware calls Auth `/api/auth/me` before parsing bodies or accessing repositories |
| Invités | Validates signature, issuer, expiry, optional `nbf` and positive subject with the shared JWT key |

Salles and Prestataires refuse protected operations with `503` when Auth cannot
verify a session. Missing, invalid or expired sessions receive `401`. Sending a
Bearer-looking string is not sufficient. An empty/invalid Auth identity is also
rejected. Invités refuses to start without authentication/key configuration outside
the explicit test environment.

## Calls between services

The calling service forwards the user's JWT; it does not create another user
session. This applies to Budget → Events/Salles/Providers, Salles → Auth/Events/
Budget/Invités, Providers → Auth/Events/Budget, and Invités → Events/Salles.
The receiving API validates the forwarded token just as for a frontend request.
Room/provider Budget commitment writes additionally require their separate
private `X-Service-Key`; a service key alone cannot bypass JWT validation.

The frontend attaches the session JWT to every service client. `401` clears the
session and returns the user to login; a temporary Auth outage does not discard it.

## Local startup

Use `./scripts/run-local.sh`. It generates/reuses ignored `.local` signing and
service keys, enables authentication for Dockerized Invités, and uses public
health endpoints for startup checks. Do not commit keys or put them in frontend
environment variables. Salles and Providers need `AUTH_SERVICE_URL` (default
`http://localhost:5005`). Login, health, and CORS preflight do not require a token;
Invités' API documentation contains schemas only, not unauthenticated business data.

## Scope and deployment

This is authentication for the existing **shared team workspace**, not per-event
ownership/RBAC. User JWTs are forwarded; separate service-account identities,
token revocation/refresh, MFA and login rate limiting are not implemented here.
Do not describe this change as a full production security audit.

Local development uses HTTP. A deployment must use HTTPS (including service calls
outside a trusted host), private networking, strong deployment-specific keys,
non-demo passwords and appropriately restricted database access. HS256 verifiers
share a signing secret, so every holder must be trusted; asymmetric signing is a
future option if stronger separation between services is required.
