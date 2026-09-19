# Fetcher Service — Fitatu

Minimal FastAPI service — obtains and stores Fitatu auth tokens.

## Setup

```bash
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env   # fill FITATU_EMAIL / FITATU_PASSWORD
uvicorn main:app --reload --port 5000
```

Runs at **http://localhost:5000**.

## API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Welcome + list of endpoints |
| GET | `/health` | Liveness check |
| POST | `/api/v1/fitatu/token` | Login / refresh and store token |
| GET | `/api/v1/fitatu/daily` | Today's macros and kcal from Fitatu |
| GET | `/api/v1/fitatu/targets` | Daily kcal + macro targets (protein/fat/carbs) |
| GET | `/api/v1/fitatu/plan` | Diet plan metadata (experimental) |
| GET | `/docs` | Swagger UI |
| GET | `/openapi.json` | OpenAPI spec |

### Token response

```json
{
  "status": "ok",
  "fitatu_user_id": "12345"
}
```

### Daily macros response

```json
{
  "status": "ok",
  "data": {
    "date": "2026-09-16",
    "calories": { "consumed": 1850, "target": 2200 },
    "macros": {
      "protein": 120,
      "fat": 65,
      "carbohydrate": 180,
      "fiber": 25,
      "sugars": 30,
      "salt": 5
    },
    "meals": [
      {
        "name": "Śniadanie",
        "item_count": 2,
        "calories": 450,
        "macros": { "protein": 30, "fat": 15, "carbohydrate": 50, "fiber": 5, "sugars": 10, "salt": 1 },
        "items": [
          { "name": "Jajecznica", "calories": 250, "protein": 18, "fat": 18, "carbohydrate": 2, "fiber": 0, "eaten": true }
        ]
      }
    ]
  }
}
```

Optional query param: `?day=2026-09-15` for a specific date.

### Daily targets response

`GET /api/v1/fitatu/targets` reads Fitatu **Cele dzienne** (kcal + macro split). Nothing is filled in from `.env`.

- `mode: "automatic"` — Fitatu calculates the split; the app often shows **ranges** (e.g. protein 12–20%) rather than a single number.
- `mode: "manual"` — you set your own split in Fitatu (e.g. 40% carbs). Then `protein_percent` / `fat_percent` / `carbohydrate_percent` and gram targets come from the API.

```json
{
  "status": "ok",
  "data": {
    "date": "2026-09-19",
    "calories": 2200,
    "mode": "automatic",
    "macros": {
      "protein_g": 82.5,
      "fat_g": 73.3,
      "carbohydrate_g": 247.5,
      "protein_percent": 15.0,
      "fat_percent": 30.0,
      "carbohydrate_percent": 45.0,
      "protein_g_min": 66.0,
      "protein_g_max": 110.0,
      "fat_g_min": 61.1,
      "fat_g_max": 73.3,
      "carbohydrate_g_min": 247.5,
      "carbohydrate_g_max": 385.0,
      "protein_percent_min": 12.0,
      "protein_percent_max": 20.0,
      "fat_percent_min": 25.0,
      "fat_percent_max": 30.0,
      "carbohydrate_percent_min": 45.0,
      "carbohydrate_percent_max": 70.0
    }
  }
}
```

Fields that Fitatu does not send are omitted.

## Database

SQLite file: `data/fetcher.sqlite` (created on first run).

Schema: `schema.sql` — applied automatically in `init_db()` at startup.

### Table `fitatu_tokens`

| Column | Type | Description |
|--------|------|-------------|
| `id` | INTEGER | Always `1` (single user) |
| `bearer_token` | TEXT | Fitatu access token |
| `refresh_token` | TEXT | Fitatu refresh token |
| `fitatu_user_id` | TEXT | Fitatu user id |
| `session_json` | TEXT | Full session payload from fitatu-api |
| `created_at` | TEXT | First save timestamp |
| `updated_at` | TEXT | Last update timestamp |

## Config

| Variable | Description |
|----------|-------------|
| `DEBUG` | `1` for debug mode (includes API error body in responses) |
| `LOG_LEVEL` | `DEBUG`, `INFO`, `WARNING` (default: `DEBUG` when `DEBUG=1`) |
| `DATABASE_PATH` | SQLite file (default: `data/fetcher.sqlite`) |
| `FITATU_EMAIL` | Fitatu account email |
| `FITATU_PASSWORD` | Fitatu account password |

## Structure

```
main.py                  ← entry point (uvicorn main:app)
config.py                ← env config
db.py                    ← SQLite + token repo
schema.sql               ← DDL
app/
  factory.py             ← create_app(), router registration
  schemas.py             ← Pydantic response models
  routes/
    system.py            ← /, /health
    fitatu.py            ← /api/v1/fitatu/token
  services/
    fitatu.py            ← Fitatu login / refresh logic
data/                    ← SQLite file (gitignored)
```
