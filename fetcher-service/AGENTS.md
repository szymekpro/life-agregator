# AGENTS.md — Fetcher Service

Fitatu-only FastAPI microservice. See [README.md](./README.md).

## Architecture

| Layer | Location | Responsibility |
|-------|----------|----------------|
| **entry** | `main.py` | uvicorn entry point |
| **factory** | `app/factory.py` | app setup, lifespan, router registration |
| **routes** | `app/routes/` | HTTP only — no business logic |
| **schemas** | `app/schemas.py` | Pydantic models (Swagger) |
| **services** | `app/services/` | business logic (Fitatu auth + fetch) |
| **storage** | `db.py`, `schema.sql` | SQLite token persistence |
| **config** | `config.py` | env vars |

## Rules

1. Routes stay thin — delegate to `app/services/`.
2. Fitatu logic in `app/services/fitatu.py` — auth + data fetch via `fitatu-api`.
3. SQLite schema in `schema.sql` — table `fitatu_tokens`, singleton row `id = 1`.
4. Never let exceptions escape routes — return `{"status": "error", "error": "..."}`.
5. Do not return bearer/refresh tokens in API responses — only store in DB.
6. New endpoints → new file in `app/routes/`, register in `app/factory.py`.

## Endpoints

| Method | Path |
|--------|------|
| GET | `/` |
| GET | `/health` |
| POST | `/api/v1/fitatu/token` |
| GET | `/api/v1/fitatu/daily` |
| GET | `/api/v1/fitatu/targets` |
| GET | `/api/v1/fitatu/plan` |
| GET | `/docs` |
| GET | `/openapi.json` |
