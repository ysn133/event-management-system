# Event Management System — Team 20

Distributed event management application developed for the Web Services module.

## Project Structure

- `frontend/` — Shared frontend application
- `gateway/` — API Gateway
- `services/` — Independent backend services
- `database/` — Database scripts and migrations
- `docs/` — UML diagrams, architecture and API documentation

## Architecture

- Frontend: React
- API Gateway: Node.js
- Backend: Spring Boot, ASP.NET Core, FastAPI, Node.js
- Database: PostgreSQL
- Communication: REST/JSON
- Containerization: Docker and Docker Compose

## Shared database

The shared PostgreSQL database runs as a standalone Docker Compose service.
Setup instructions, schema details, seed records, and connection strings are in
[`database/README.md`](database/README.md).

## Authentication and local development

All business APIs require JWT authentication. See
[API authentication](docs/API_AUTHENTICATION.md) for service-call validation and scope.

Run `./scripts/run-local.sh` to start PostgreSQL, Auth (5005), Event (5001),
Prestataires (5002), Invités (5003), Budget (5004), Salles (5242) and the frontend
(5173). Invités runs through Docker Compose; the other backends run locally.
Missing Prestataires dependencies are installed automatically. On the first
run Docker builds the Invités image. Ctrl+C stops the processes and the Invités
container started by the script, while PostgreSQL stays running.

Login, development credentials and JWT setup are documented in
[the Auth service README](services/auth-service/README.md).
Budget and Event require JWTs; teammate services have not yet integrated them.
