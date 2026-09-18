import json
import sqlite3
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any, Iterator

SCHEMA_PATH = Path(__file__).resolve().parent / "schema.sql"


@contextmanager
def connect(db_path: Path) -> Iterator[sqlite3.Connection]:
    db_path.parent.mkdir(parents=True, exist_ok=True)
    connection = sqlite3.connect(db_path)
    connection.row_factory = sqlite3.Row
    try:
        yield connection
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def init_db(db_path: Path) -> None:
    schema = SCHEMA_PATH.read_text(encoding="utf-8")
    with connect(db_path) as connection:
        connection.executescript(schema)


@dataclass(frozen=True)
class FitatuTokenRecord:
    bearer_token: str | None
    refresh_token: str | None
    fitatu_user_id: str | None
    session_data: dict[str, Any]
    updated_at: str | None


class FitatuTokens:
    def __init__(self, db_path: Path) -> None:
        self._db_path = db_path

    def exists(self) -> bool:
        with connect(self._db_path) as connection:
            row = connection.execute("SELECT 1 FROM fitatu_tokens WHERE id = 1").fetchone()
        return row is not None

    def load(self) -> FitatuTokenRecord | None:
        with connect(self._db_path) as connection:
            row = connection.execute(
                """
                SELECT bearer_token, refresh_token, fitatu_user_id, session_json, updated_at
                FROM fitatu_tokens
                WHERE id = 1
                """,
            ).fetchone()

        if row is None:
            return None

        session_data = json.loads(row["session_json"])
        if not isinstance(session_data, dict):
            return None

        return FitatuTokenRecord(
            bearer_token=row["bearer_token"],
            refresh_token=row["refresh_token"],
            fitatu_user_id=row["fitatu_user_id"],
            session_data=session_data,
            updated_at=row["updated_at"],
        )

    def save(self, session_data: dict[str, Any]) -> None:
        now = datetime.now(UTC).isoformat()
        with connect(self._db_path) as connection:
            connection.execute(
                """
                INSERT INTO fitatu_tokens (
                    id, bearer_token, refresh_token, fitatu_user_id, session_json, updated_at
                ) VALUES (1, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    bearer_token = excluded.bearer_token,
                    refresh_token = excluded.refresh_token,
                    fitatu_user_id = excluded.fitatu_user_id,
                    session_json = excluded.session_json,
                    updated_at = excluded.updated_at
                """,
                (
                    session_data.get("bearer_token"),
                    session_data.get("refresh_token"),
                    session_data.get("fitatu_user_id"),
                    json.dumps(session_data, ensure_ascii=True),
                    now,
                ),
            )
