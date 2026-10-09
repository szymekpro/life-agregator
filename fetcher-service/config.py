import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

load_dotenv()

BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"


@dataclass(frozen=True)
class AppConfig:
    port: int
    debug: bool
    log_level: str
    database_path: Path
    fitatu_email: str
    fitatu_password: str

    @property
    def fitatu_configured(self) -> bool:
        return bool(self.fitatu_email and self.fitatu_password)


def load_config() -> AppConfig:
    debug = os.getenv("DEBUG", "0") == "1"
    return AppConfig(
        port=int(os.getenv("PORT", "5000")),
        debug=debug,
        log_level=os.getenv("LOG_LEVEL", "DEBUG" if debug else "INFO"),
        database_path=Path(os.getenv("DATABASE_PATH", str(DATA_DIR / "fetcher.sqlite"))),
        fitatu_email=os.getenv("FITATU_EMAIL", ""),
        fitatu_password=os.getenv("FITATU_PASSWORD", ""),
    )
