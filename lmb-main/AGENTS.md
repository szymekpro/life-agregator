# AGENTS.md — Personal Board (lmb-main / Core Service)

Instructions for AI agents working on this repository. For a human-readable project overview, see [README.md](./README.md).

## What this repo is

- **Service:** Core Service — auth, users, dashboard layout & widgets (skeleton phase).
- **Repo:** `lmb-main` — first module of a larger Personal Dashboard system.
- **Other modules** (Food/Fitness, Diabetes, Finance) will be separate services/repos later. Do not implement their domain logic here unless explicitly asked.

## Architecture rules

1. **Evolutionary Architecture + YAGNI** — add features only when real usage requires them. Prefer the smallest correct change.
2. **Database per service** — Core Service uses its own PostgreSQL database. Never share DB schemas with other modules.
3. **Async between modules** — cross-module side effects go through **Kafka events**, not direct DB access or tight coupling.
4. **Sync for reads/UI** — REST API for real-time dashboard data and direct queries.
5. **Failure isolation** — a bug in one module must not corrupt another module's data.

## Tech stack (Core Service)

| Item | Value |
|------|-------|
| Java | 21 (Amazon Corretto 21 LTS) |
| Framework | Spring Boot 4.1.x |
| Database | PostgreSQL |
| Events | Apache Kafka |
| Config | YAML (`application.yaml` / `application.yml`), environment profiles (local vs Kubernetes) |
| Build (current) | **Maven** (`pom.xml`, `./mvnw`) |
| Build (planned) | Gradle + multi-stage Dockerfile |

When adding dependencies or build steps, follow what is already in the repo (Maven today).

## Repository layout

Maven multi-module project — everything under `modules/`:

```
modules/
  core/              ← runnable app (Boot entry point, wiring, application.yaml)
  database/          ← Flyway migrations (all SQL lives here)
  api-documentation/ ← OpenAPI YAML (all REST docs live here)
  auth/              ← core domain module
  *-adapter/         ← add when an external service API is integrated (YAGNI until then)
```

Base Java package: `com.szymek.board.lmb`

| Module | Package / path | Responsibility |
|--------|----------------|----------------|
| `core` | `...lmb.core` | Boot entry point, wiring, datasource + Flyway config, Swagger UI |
| `database` | `src/main/resources/db/migration/` | Versioned SQL migrations only |
| `api-documentation` | `src/main/resources/static/openapi/` | OpenAPI YAML — design-first, no `@Operation` on controllers |
| Domain | `...lmb.auth`, `...lmb.dashboard` | Core Service logic (no SQL, no Swagger annotations) |
| Adapter | `...lmb.integration.fitness` | HTTP/Kafka client, DTO mapping for an external service |

### Database & migrations

- **All migrations** go in `modules/database/src/main/resources/db/migration/`.
- Naming: `V{version}__{snake_case_description}.sql` (e.g. `V2__create_users_table.sql`).
- When a domain module needs a table, add a new migration in `database` — do not put SQL in `auth` or other domain modules.
- Flyway runs on startup from `modules/core` (migrations are on the classpath via `lmb-database` dependency).
- Override connection via env: `DB_URL`, `DB_USER`, `DB_PASSWORD`.

### API documentation

- **All OpenAPI specs** go in `modules/api-documentation/src/main/resources/static/openapi/`.
- Split large APIs with `$ref` into `paths/*.yaml` if needed.
- `core` serves Swagger UI at `/swagger-ui.html` and loads the static spec (`springdoc.api-docs.enabled: false`).
- Domain controllers implement endpoints — they do **not** carry Swagger/OpenAPI annotations.
- Keep spec and implementation aligned with contract tests when endpoints exist.

The `core` module scans `com.szymek.board.lmb` — new modules register via `@Configuration` or component scanning under that base package.

**Adapter modules** call external services (Fitness, Finance, Diabetes) over REST. They map external API contracts to Core models. Never copy external domain logic into adapters.

## Event contracts (cross-module)

Use these topic/event names consistently when publishing or consuming:

| Event | Publisher | Consumer | Effect |
|-------|-----------|----------|--------|
| `food.meal_logged` | Food Service | Diabetes Service | Suggested insulin dose & carb exchanges (WW/WBT) |
| `workout.completed` | Food/Fitness Service | Diabetes Service | Adjust insulin sensitivity coefficients |
| `diabetes.prescription_bought` | Diabetes Service | Finance Service | Auto-create expense entry |
| `food.groceries_logged` | Food Service | Finance Service | Auto-create expense entry |

Core Service may orchestrate or relay events later, but do not invent new event names without aligning with this table.

## Coding guidelines

- Match existing Spring Boot / Java conventions in the repo.
- Minimize scope — no unrelated refactors or speculative abstractions.
- Comments only for non-obvious business or integration logic.
- Configuration belongs in YAML with profiles, not hardcoded environment values.
- Each future module boundary = potential microservice; avoid shortcuts that break that separation.

## Local development

```bash
./mvnw test
./mvnw -pl modules/core -am spring-boot:run
```

Application name: `lmb-core` (see `modules/core/src/main/resources/application.yaml`).

When adding a new module: create under `modules/`, add to root `pom.xml` `<modules>`, declare in `dependencyManagement`, depend on it from `modules/core`.

When adding a database change: new Flyway script in `modules/database` only — never in domain modules.

When adding or changing a REST endpoint: update OpenAPI in `modules/api-documentation` only — no Swagger annotations on controllers.

## What not to do

- Do not implement Food, Diabetes, or Finance domain logic in this repo (unless explicitly requested for prototyping).
- Do not share databases across services.
- Do not replace Kafka with synchronous calls for cross-module side effects.
- Do not over-engineer for future modules that do not exist yet.
- Do not migrate Maven → Gradle unless the user asks.

## When unsure

1. Check [README.md](./README.md) for project vision and module map.
2. Prefer extending Core Service skeleton (auth, users, widgets) over building entire future modules.
3. Ask before introducing new infrastructure (new brokers, shared libs, monorepo restructure).
