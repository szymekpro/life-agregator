# Personal Board / Dashboard

A personal **Command Center** for everyday use — a modular dashboard built with evolutionary architecture and YAGNI principles. Features and fixes are added in response to real usage, not upfront speculation.

> AI agents: see [AGENTS.md](./AGENTS.md) for repository-specific instructions and conventions.

## Project Goal

Build a modular Personal Dashboard that centralizes day-to-day tracking and decision-making across health, fitness, diabetes management, and personal finance — with room to grow into habits, notes, and more over time.

## Architecture

### Infrastructure

- Target deployment: **Kubernetes** cluster (Minikube for local development)
- Pattern: **Microservices / modular services**
- Each module owns an **independent database** — failures and data are isolated between services

### Communication

| Type | Technology | Purpose |
|------|------------|---------|
| **Asynchronous (Event-Driven)** | Apache Kafka | Cross-module events (e.g. a logged meal triggers diabetes calculations) |
| **Synchronous** | REST API | Real-time UI data and direct service-to-service queries |

### Design Principles

- **Evolutionary Architecture** — structure evolves with actual needs
- **YAGNI (features)** — add capabilities only when real usage demands them
- **Clean OO code** — SOLID, abstractions, inheritance and design patterns where they fit (interfaces at real boundaries, not forced); prefer Java 17-style code and no custom generics, Java 21 is fine when it is simpler. Details in [AGENTS.md](./AGENTS.md)

## Planned Modules

| Module | Responsibility |
|--------|----------------|
| **Core Service** | System skeleton — auth, users, dashboard layout & widgets |
| **Food / Fitness** | Macro & calorie tracking, workout journal |
| **Diabetes (Type 1)** | Glucose readings, carb/exchange calculator (WW/WBT), stats (TIR, estimated HbA1c), insulin doses |
| **Personal Finance** | Expenses, income, budgeting |
| **Future** | Habit tracker, notes / Second Brain |

## Tech Stack (Core Service)

| Layer | Choice |
|-------|--------|
| Language / Runtime | Java 21 (Amazon Corretto 21 LTS) |
| Framework | Spring Boot 4.1.x (latest stable) |
| Database | PostgreSQL (dedicated DB per service) |
| Event Broker | Apache Kafka |
| Configuration | YAML (`application.yml`) with environment profiles (local IDE vs Kubernetes) |
| Build & Containers | Gradle, multi-stage Dockerfile |

> **Note:** This repository (`lmb-main`) is the initial Core Service scaffold.

## Event Flows

Cross-module integration is driven by Kafka events:

```
food.meal_logged
  └─► Diabetes Service — suggested insulin dose & carb exchanges (WW/WBT)

workout.completed
  └─► Diabetes Service — adjust insulin sensitivity coefficients

diabetes.prescription_bought
food.groceries_logged
  └─► Finance Service — auto-create expense entries
```

## Repository Structure

Single `modules/` folder — every Maven module lives here, including the runnable app.

```
lmb-main/
├── pom.xml                         ← parent aggregator
└── modules/
    ├── core/                       ← runnable Spring Boot app (wires other modules)
    ├── database/                   ← Flyway SQL migrations (PostgreSQL)
    ├── api-documentation/          ← OpenAPI specs (design-first, no inline annotations)
    ├── auth/                       ← core domain: users, sessions, permissions
    ├── dashboard/                  ← dashboard read model: day-based sync engine + widget endpoints
    ├── fitness-adapter/            ← REST client + DTO mapping for the fetcher service (Fitatu)
    ├── finance-adapter/            ← (planned) REST client + DTO mapping for Finance service
    └── diabetes-adapter/           ← (planned) REST client + DTO mapping for Diabetes service
```

**`core`** — thin bootstrap module: entry point, config, dependency wiring. No domain logic.

**`database`** — all Core Service schema changes as Flyway migrations (`V1__`, `V2__`, …). Other modules never hold SQL files.

**`api-documentation`** — OpenAPI YAML only. Swagger UI in `core` reads this spec; controllers stay annotation-free.

**Core domain modules** (`auth`, `dashboard`, …) — Core Service logic and its PostgreSQL schema.

**Adapter modules** — translate external service API contracts into Core-friendly models. No Fitness/Finance/Diabetes business rules here.

External domain services stay separate deployables; this repo is only the Core Service and its integration adapters.

## Local run

| Service | Port | Where |
|---------|------|-------|
| Core (this repo) | **8000** | `lmb-main`, Swagger: http://localhost:8000/swagger-ui.html |
| Fetcher (Fitatu) | **5000** | `../fetcher-service`, Swagger: http://localhost:5000/docs |
| PostgreSQL | 5432 | `docker-compose.yml` (db / user / password: `postgres`) |

`application.yaml` has the same defaults (Postgres on `localhost:5432`, fetcher on `http://localhost:5000`), so nothing needs configuring. Override with `SERVER_PORT`, `FETCHER_BASE_URL`, `DB_URL`, `DB_USER`, `DB_PASSWORD`. Flyway applies migrations on startup.

Start in this order, each in its own terminal:

```bash
# 1. database
docker compose up -d

# 2. fetcher (needs .env with FITATU_EMAIL / FITATU_PASSWORD, see ../fetcher-service/README.md)
cd ../fetcher-service && python main.py

# 3. core
./mvnw -pl modules/core -am spring-boot:run
```
