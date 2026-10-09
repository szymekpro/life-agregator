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

## Code style — keep it simple

General (all services in this project):

- Prefer the simplest solution that works (YAGNI). No abstractions "for later".
- No custom generics, no clever type tricks, no metaprogramming.
- Plain, conventional constructs over fancy ones — readable by someone new to the code.

Python (this service):

- Do **not** define generics (`TypeVar`, `Generic`, `Protocol`). Built-in hints are enough.
- Type hints are optional help, not a goal: plain `dict` / `list` / `str | None` are fine — avoid nested types like `dict[str, dict[str, Any]]`.
- Prefer small plain functions and explicit `if`s over lambdas, dispatch tables and callbacks.
- Return only fields that actually have data — do not return `null` placeholders.

Java (Core service, other modules):

- Target **Java 17** language features, even though the runtime is Java 21.
- Use: `record`, `var`, text blocks, `switch` expressions, `sealed` types only if clearly needed.
- Do **not** use Java 21-only features: virtual threads, pattern matching for `switch`, record patterns, sequenced collections.
- Avoid custom generic classes/methods unless there is no simpler option; use concrete types.

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
