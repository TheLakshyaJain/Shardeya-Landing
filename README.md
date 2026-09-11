# Shardeya

Multi-tenant SaaS for Indian real estate brokers and builders. See `CLAUDE.md` for
the full working agreement, `00-ARCHITECTURE.md` through `05-MILESTONES.md` for the
architecture, and `05-MILESTONES.md` for what's built vs. what's next.

**Status:** Milestone 0 — Project Bootstrap & Design System.

## Quick start

```
cp .env.example .env
docker compose up -d
cd frontend && npm install && npm run dev
```

That brings up Postgres, Redis, MinIO, MailHog, a WhatsApp API stub, and the
Spring Boot backend via Docker, and starts the frontend dev server separately (for
fast HMR). Once everything is up:

| Service | URL |
|---|---|
| Frontend | http://localhost:5173 |
| Backend health | http://localhost:8080/actuator/health |
| Backend API docs (Swagger UI) | http://localhost:8080/api/v1/docs |
| MailHog (caught emails) | http://localhost:8025 |
| MinIO console | http://localhost:9001 |
| WhatsApp stub inbox | http://localhost:4001/v1/messages |

## Running tests

```
cd backend && mvn verify        # unit tests + ArchUnit boundary tests
cd frontend && npm run test     # Vitest unit tests
cd frontend && npm run lint     # oxlint
cd frontend && npm run audit    # dependency vulnerability gate
```

## Project structure

```
backend/    Spring Boot 3.3 (Java 21) — see CLAUDE.md "Package Structure"
frontend/   React 18 + TypeScript + Vite — see CLAUDE.md "Package Structure"
infra/      Local-dev-only services (WhatsApp API stub)
docker-compose.yml   Postgres 16, Redis 7, MinIO, MailHog, WhatsApp stub, backend
```

## Requirements

- Java 21, Maven 3.9+ (or use the Docker build for the backend — no local Maven
  needed if you only run `docker compose up`)
- Node 22+
- Docker with Compose v2
