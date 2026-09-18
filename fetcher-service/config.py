import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

load_dotenv()

BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"


def _optional_float(name: str) -> float | None:
    raw = os.getenv(name, "").strip()
    if not raw:
        return None
    try:
        return float(raw)
    except ValueError:
        return None


@dataclass(frozen=True)
class AppConfig:
    debug: bool
    log_level: str
    database_path: Path
    fitatu_email: str
    fitatu_password: str
    fitatu_macro_protein_percent: float | None
    fitatu_macro_fat_percent: float | None
    fitatu_macro_carb_percent: float | None

    @property
    def fitatu_configured(self) -> bool:
        return bool(self.fitatu_email and self.fitatu_password)

    @property
    def fitatu_macro_overrides(self) -> dict[str, float]:
        overrides: dict[str, float] = {}
        if self.fitatu_macro_protein_percent is not None:
            overrides["protein_percent"] = self.fitatu_macro_protein_percent
        if self.fitatu_macro_fat_percent is not None:
            overrides["fat_percent"] = self.fitatu_macro_fat_percent
        if self.fitatu_macro_carb_percent is not None:
            overrides["carbohydrate_percent"] = self.fitatu_macro_carb_percent
        return overrides


def load_config() -> AppConfig:
    debug = os.getenv("DEBUG", "0") == "1"
    return AppConfig(
        debug=debug,
        log_level=os.getenv("LOG_LEVEL", "DEBUG" if debug else "INFO"),
        database_path=Path(os.getenv("DATABASE_PATH", str(DATA_DIR / "fetcher.sqlite"))),
        fitatu_email=os.getenv("FITATU_EMAIL", ""),
        fitatu_password=os.getenv("FITATU_PASSWORD", ""),
        fitatu_macro_protein_percent=_optional_float("FITATU_MACRO_PROTEIN_PERCENT"),
        fitatu_macro_fat_percent=_optional_float("FITATU_MACRO_FAT_PERCENT"),
        fitatu_macro_carb_percent=_optional_float("FITATU_MACRO_CARB_PERCENT"),
    )
