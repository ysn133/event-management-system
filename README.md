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
