# Event Management System — Team 20

Distributed event management application developed for the Web Services module.

## Project Structure

- `frontend/` — Shared frontend application
- `gateway/` — NGINX API gateway configuration and smoke test
- `services/` — Independent backend services
- `database/` — Database scripts and migrations
- `docs/` — UML diagrams, architecture and API documentation

## Architecture

- Frontend: React
- API Gateway: NGINX (Docker)
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
(5173), with the API gateway on **8081**. The frontend calls only the gateway.
NGINX and Invités run through Docker Compose; the other backends run locally.
Routing is configured in `gateway/templates/default.conf.template`.
Missing Prestataires dependencies are installed automatically. On the first
run Docker builds the Invités image. Ctrl+C stops the processes and the Invités
container started by the script, while PostgreSQL stays running.

Login, development credentials and JWT setup are documented in
[the Auth service README](services/auth-service/README.md).

## VPS deployment with Docker Compose

The standalone `compose.production.yaml` builds the frontend and all six
microservices as containers. It exposes only **127.0.0.1:43871**, for your existing
Nginx virtual host to proxy to. PostgreSQL and backend ports stay unpublished.
Local development continues to use `compose.yaml` and `scripts/run-local.sh`.

Copy `deploy/production.env.example` to `.env.production`, fill in the domain,
independent random secrets and initial login credentials, then run:

```bash
bash scripts/deploy-production.sh
```

Configure your existing host Nginx using `deploy/nginx-site.conf.example` and
enable HTTPS. Never commit `.env.production` or delete the production DB volume.
