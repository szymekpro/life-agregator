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
- `core` serves Swagger UI at `/swagger-ui.html` and loads the static spec (`springdoc.swagger-ui.url`; generated `/v3/api-docs` is off via `springdoc.enable-default-api-docs: false`). Do **not** set `springdoc.api-docs.enabled: false` - it switches off all of springdoc including Swagger UI (404 on `/swagger-ui.html`).
- Domain controllers implement endpoints — they do **not** carry Swagger/OpenAPI annotations.
- Keep spec and implementation aligned with contract tests when endpoints exist.

The `core` module scans `com.szymek.board.lmb` — new modules register via `@Configuration` or component scanning under that base package.

**Adapter modules** call external services (Fitness, Finance, Diabetes) over REST. They map external API contracts to Core models. Never copy external domain logic into adapters.

### Syncing data from the fetcher into the dashboard

The dashboard keeps its own copy of per-day data in `day_data` (key: `dataset` + `day`, payload as JSONB) so widgets never call the fetcher. `DaySyncService` fills it day by day, only what is missing.

To sync a new kind of data (no migration needed):

1. **Payload** in `modules/dashboard`: a class implementing `DayPayload` (Lombok `@Data` + `@NoArgsConstructor`). New fields must be nullable - old rows are not rewritten.
2. **Response** in `modules/fitness-adapter`: a class extending `FetcherResponse` with the `data` shape of the fetcher endpoint (only the fields you need), implementing `hasData()` and `toPayload()`.
3. **Provider** in `modules/fitness-adapter`: a `@Component` extending `FetcherDayDataProvider` - only `dataset()` (stable key, never renamed), `path()` and `responseType()`. The fetch flow and error handling live in the base class.
4. **Expose it**: read it with `DayDataRepository.findBetween(dataset, from, to, PayloadClass.class)` and cast the payload (extend `FoodDayService` / `FoodDay`, or add a new endpoint) and describe it in OpenAPI.

A provider that does not use the fetcher implements `DayDataProvider` directly.

Existing days are back-filled for the new dataset on the next `POST /api/v1/dashboard/sync`.

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
- Minimize scope of **features** — no unrelated refactors. This does not apply to code structure: abstractions, interfaces and patterns are wanted (see "Design").
- Comments only for non-obvious business or integration logic.
- Configuration belongs in YAML with profiles, not hardcoded environment values.
- Each future module boundary = potential microservice; avoid shortcuts that break that separation.

### Java language level

The build runs on Java 21. The owner **prefers Java 17-style code** - this is a preference, not a ban. Java 21 features are fine when they make the code clearly simpler (e.g. `getFirst()` / `getLast()`); just do not reach for the fancy ones for their own sake.

- Prefer plain classes with Lombok (`@Getter` / `@Data` / `@AllArgsConstructor`) over `record` - the owner finds records unfamiliar.
- Prefer plain `switch` / `if` over pattern matching in `switch`, record patterns, `sealed` hierarchies; keep `var` rare.
- **Prefer no custom generics** in our own code (`<T>` classes, interfaces, methods). Model variation with an interface or abstract base class instead, and cast in one well-documented place if needed. Using JDK/Spring generic types (`List<Meal>`, `Map<String, X>`, `Class<? extends X>`) is fine. A generic is acceptable if the non-generic alternative is clearly worse.

### Design

The owner wants well-structured OO code: use abstractions, inheritance and design patterns **where they fit** - not forced. YAGNI applies to *features*; for structure, ask "does this make the code easier to extend, swap or test?".

- Follow **SOLID**: one reason to change per class, extend by adding classes (new provider / payload) rather than editing a central `switch`, depend on abstractions at boundaries.
- Add an **interface** when there is a real reason: a boundary to something external (DB, HTTP), several implementations or a likely second one, a swappable rule/strategy, or a consumer that should not know the implementation. Do not add one to a class that is just a plain data holder or a one-off helper.
- Use **abstract base classes** (inheritance) when classes share a flow and differ in a few steps (Template Method - e.g. `FetcherDayDataProvider`, `FetcherResponse`).
- Use a **design pattern** when one clearly fits (Strategy, Template Method, Factory, Builder, Adapter, Decorator, Facade...). Prefer composing small collaborators over one big class.
- When touching code and you see a cleaner, more SOLID shape, refactor it - keep the change small and tested.
- Comment only non-obvious rules (what a rename or a new field would break, which exception stops a run). Do not restate the code or name the pattern in a class Javadoc. Patterns already in the code:

| Pattern | Where |
|---------|-------|
| Strategy | `DayDataProvider` - one implementation per dataset, `DaySyncService` runs them all |
| Template Method | `FetcherDayDataProvider.fetch(...)`, `FetcherResponse.toPayload()` |
| Repository | `DayDataRepository` / `JdbcDayDataRepository` |
| Builder | `DaySyncReport` builds `DaySyncResult` |
| Policy (Strategy) | `DayFreshnessPolicy` (interface) / `ConfiguredDayFreshnessPolicy` decides if a stored day must be re-fetched |
| Adapter | `fitness-adapter` maps fetcher JSON to dashboard models; `FetcherClient` (interface) / `HttpFetcherClient` is the HTTP boundary |

- Keep business rules out of controllers. Controllers only map HTTP to a service call; errors go through `@RestControllerAdvice` (e.g. `DashboardExceptionHandler`, `ProblemDetail` bodies), not `try/catch` in each endpoint.
- Throw specific exceptions (`InvalidRequestException`, `FoodDayUnavailableException`) instead of generic `IllegalArgumentException` for HTTP-relevant errors.
- Keep methods short; extract a well-named private method instead of nesting loops or using labeled `break` / `continue`.
- Prefer constructor injection and `final` fields; no field injection.

## Local development

Ports: **Core 8000**, **fetcher-service 5000** (`../fetcher-service`), PostgreSQL 5432 (`docker-compose.yml` in this folder; db/user/password `postgres`). Defaults in `application.yaml` match, so core needs no extra env. Flyway runs on startup. Start the fetcher with `python main.py` (port 5000) before syncing; a bare `uvicorn main:app` would use 8000, which is Core's port.

```bash
docker compose up -d
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
- Do not build features or modules that do not exist yet (clean abstractions in existing code are fine and encouraged).
- Do not migrate Maven → Gradle unless the user asks.

## When unsure

1. Check [README.md](./README.md) for project vision and module map.
2. Prefer extending Core Service skeleton (auth, users, widgets) over building entire future modules.
3. Ask before introducing new infrastructure (new brokers, shared libs, monorepo restructure).
