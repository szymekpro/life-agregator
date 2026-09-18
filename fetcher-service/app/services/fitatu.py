import json
import logging
from datetime import date
from typing import Any, Callable

from fitatu_api import FitatuApiClient, FitatuAuthContext, FitatuLibrary
from fitatu_api.exceptions import FitatuApiError

from config import AppConfig
from db import FitatuTokens

logger = logging.getLogger(__name__)

MACRO_FIELDS = ("protein", "fat", "carbohydrate", "fiber", "sugars", "salt")

GOAL_LABELS = {
    "REDUCTION": "Odchudzanie",
    "LOSE_WEIGHT": "Odchudzanie",
    "WEIGHT_LOSS": "Odchudzanie",
    "LOSS": "Odchudzanie",
    "DECREASE": "Odchudzanie",
    "DOWN": "Odchudzanie",
    "MASS": "Przybieranie masy",
    "GAIN_WEIGHT": "Przybieranie masy",
    "WEIGHT_GAIN": "Przybieranie masy",
    "GAIN": "Przybieranie masy",
    "INCREASE": "Przybieranie masy",
    "UP": "Przybieranie masy",
    "BULK": "Przybieranie masy",
    "MAINTENANCE": "Utrzymanie wagi",
    "KEEP_WEIGHT": "Utrzymanie wagi",
    "MAINTAIN": "Utrzymanie wagi",
    "0": "Odchudzanie",
    "1": "Przybieranie masy",
    "2": "Utrzymanie wagi",
    "3": "Przybieranie masy",
}

WEIGHT_DIRECTION_LABELS = {
    -1: ("LOSS", "Odchudzanie"),
    0: ("LOSS", "Odchudzanie"),
    1: ("GAIN", "Przybieranie masy"),
    2: ("MAINTAIN", "Utrzymanie wagi"),
}

KCAL_PER_GRAM = {
    "protein": 4,
    "fat": 9,
    "carbohydrate": 4,
}


def _round_num(value: Any, places: int = 1) -> float:
    try:
        return round(float(value or 0), places)
    except (TypeError, ValueError):
        return 0.0


def _grams_from_percent(kcal: float, percent: float, kcal_per_gram: float) -> float:
    return _round_num(kcal * percent / 100 / kcal_per_gram)


def _pick_number(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _pick_positive_number(value: Any) -> float | None:
    number = _pick_number(value)
    if number is None or number <= 0:
        return None
    return number


def _round_optional(value: Any, places: int = 1) -> float | None:
    number = _pick_number(value)
    if number is None:
        return None
    return round(number, places)


class FitatuAuthError(Exception):
    pass


def _parse_response_body(body: str | None) -> str | dict | list | None:
    if not body:
        return None
    try:
        return json.loads(body)
    except json.JSONDecodeError:
        return body


class FitatuService:
    def __init__(self, config: AppConfig) -> None:
        self._config = config
        self._tokens = FitatuTokens(config.database_path)

    def obtain_token(self) -> dict[str, Any]:
        return self._execute(self._obtain_token)

    def fetch_daily(self, target_date: date | None = None) -> dict[str, Any]:
        day = target_date or date.today()
        return self._execute(lambda: self._fetch_daily(day))

    def fetch_plan(self, target_date: date | None = None) -> dict[str, Any]:
        day = target_date or date.today()
        return self._execute(lambda: self._fetch_plan(day))

    def fetch_targets(self, target_date: date | None = None) -> dict[str, Any]:
        day = target_date or date.today()
        return self._execute(lambda: self._fetch_targets(day))

    def _execute(self, action: Callable[[], dict[str, Any]]) -> dict[str, Any]:
        if not self._config.fitatu_configured and not self._tokens.exists():
            return {
                "status": "skipped",
                "error": "Set FITATU_EMAIL and FITATU_PASSWORD in .env",
            }

        try:
            return action()
        except FitatuAuthError as exc:
            return self._error_response(exc)
        except FitatuApiError as exc:
            return self._api_error_response(exc)
        except Exception as exc:  # noqa: BLE001
            logger.exception("Unexpected Fitatu error")
            return self._error_response(exc)

    def _obtain_token(self) -> dict[str, Any]:
        session = self._resolve_session()
        logger.info("Fitatu token obtained for user_id=%s", session.get("fitatu_user_id"))
        return {
            "status": "ok",
            "fitatu_user_id": session.get("fitatu_user_id"),
        }

    def _fetch_targets(self, target_date: date) -> dict[str, Any]:
        library = self._get_library()
        client = library._build_client()
        user_id = self._resolve_user_id(library)
        if not user_id:
            raise FitatuAuthError("Fitatu user id missing")

        sources = self._collect_plan_sources(client, user_id, target_date)
        return {
            "status": "ok",
            "data": self._build_targets(sources, target_date),
        }

    def _fetch_plan(self, target_date: date) -> dict[str, Any]:
        library = self._get_library()
        client = library._build_client()
        user_id = self._resolve_user_id(library)
        if not user_id:
            raise FitatuAuthError("Fitatu user id missing")

        sources = self._collect_plan_sources(client, user_id, target_date)
        return {
            "status": "ok",
            "data": self._build_plan(sources, target_date),
        }

    def _fetch_daily(self, target_date: date) -> dict[str, Any]:
        library = self._get_library()

        summary = library.get_day_summary_via_api(target_date=target_date)
        if summary.get("status") != "ok":
            return self._library_error(summary)

        result = summary["result"]
        totals = result["totals"]
        client = library._build_client()
        user_id = self._resolve_user_id(library)
        targets = (
            self._build_targets(self._collect_plan_sources(client, user_id, target_date), target_date)
            if user_id
            else None
        )
        calories_target = targets.get("calories") if targets else None

        return {
            "status": "ok",
            "data": {
                "date": target_date.isoformat(),
                "calories": {
                    "consumed": _round_num(totals.get("energy")),
                    "target": calories_target,
                },
                "macros": self._macros_from_totals(totals),
                "meals": [self._meal_info(meal) for meal in result.get("meals", [])],
            },
        }

    @staticmethod
    def _macros_from_totals(totals: dict[str, Any]) -> dict[str, float]:
        return {field: _round_num(totals.get(field)) for field in MACRO_FIELDS}

    @classmethod
    def _meal_info(cls, meal: dict[str, Any]) -> dict[str, Any]:
        meal_totals = meal.get("totals", {})
        return {
            "name": meal.get("meal_name") or meal.get("meal_key", "unknown"),
            "item_count": meal.get("item_count", 0),
            "calories": _round_num(meal_totals.get("energy")),
            "macros": cls._macros_from_totals(meal_totals),
            "items": [cls._meal_item_info(item) for item in meal.get("items", [])],
        }

    @staticmethod
    def _meal_item_info(item: dict[str, Any]) -> dict[str, Any]:
        return {
            "name": item.get("name", "Unknown"),
            "calories": _round_num(item.get("energy")),
            "protein": _round_num(item.get("protein")),
            "fat": _round_num(item.get("fat")),
            "carbohydrate": _round_num(item.get("carbohydrate")),
            "fiber": _round_num(item.get("fiber")),
            "eaten": bool(item.get("eaten", False)),
        }

    def _library_error(self, payload: dict[str, Any]) -> dict[str, Any]:
        logger.error("Fitatu library error: %s", payload)
        response: dict[str, Any] = {
            "status": "error",
            "error": payload.get("message") or "Fitatu request failed",
            "status_code": payload.get("status_code"),
        }
        if self._config.debug:
            response["details"] = _parse_response_body(payload.get("body"))
        return response

    def _error_response(self, exc: Exception) -> dict[str, Any]:
        logger.error("Fitatu auth error: %s", exc)
        return {"status": "error", "error": str(exc)}

    def _api_error_response(self, exc: FitatuApiError) -> dict[str, Any]:
        details = _parse_response_body(exc.body)
        logger.error(
            "Fitatu API error: %s status_code=%s body=%s",
            exc,
            exc.status_code,
            exc.body,
        )
        response: dict[str, Any] = {
            "status": "error",
            "error": str(exc),
            "status_code": exc.status_code,
        }
        if self._config.debug:
            response["details"] = details
        return response

    def _get_library(self) -> FitatuLibrary:
        session = self._resolve_session()
        return FitatuLibrary(session_data=session)

    @staticmethod
    def _resolve_user_id(library: FitatuLibrary) -> str | None:
        user_id = library.session_data.get("fitatu_user_id") or library.describe_session().get(
            "fitatu_user_id",
        )
        return str(user_id) if user_id else None

    def _collect_plan_sources(
        self,
        client: FitatuApiClient,
        user_id: str,
        target_date: date,
    ) -> dict[str, dict[str, Any]]:
        sources: dict[str, dict[str, Any]] = {}

        fetchers: dict[str, Callable[[], Any]] = {
            "user_settings_day": lambda: client.get_user_settings_for_day(user_id, target_date),
            "user_settings": lambda: client.get_user_settings(user_id, day=target_date),
            "diet_plan_settings": lambda: client.get_diet_plan_settings(user_id),
        }

        for name, fetch in fetchers.items():
            try:
                payload = fetch()
            except FitatuApiError:
                logger.debug("Plan source %s failed", name, exc_info=True)
                continue
            if isinstance(payload, dict) and payload.get("status") == "not_supported":
                continue
            if isinstance(payload, dict) and payload:
                sources[name] = payload

        return sources

    @staticmethod
    def _diet_plan_settings(sources: dict[str, dict[str, Any]]) -> dict[str, Any]:
        return sources.get("diet_plan_settings", {})

    @staticmethod
    def _user_settings(sources: dict[str, dict[str, Any]]) -> dict[str, Any]:
        return sources.get("user_settings_day") or sources.get("user_settings", {})

    @staticmethod
    def _extract_calories(diet_plan: dict[str, Any]) -> float | None:
        return (
            _pick_positive_number(diet_plan.get("energy"))
            or _pick_positive_number(diet_plan.get("calculatedEnergy"))
            or _pick_positive_number(diet_plan.get("manualEnergyTarget"))
        )

    def _build_macro_overrides(self, calories: float) -> dict[str, float] | None:
        overrides = self._config.fitatu_macro_overrides
        if not overrides:
            return None

        macros: dict[str, float] = {
            key: _round_num(value) for key, value in overrides.items()
        }
        percent_to_gram = {
            "protein_percent": ("protein_g", "protein"),
            "fat_percent": ("fat_g", "fat"),
            "carbohydrate_percent": ("carbohydrate_g", "carbohydrate"),
        }
        for percent_key, (gram_key, nutrient) in percent_to_gram.items():
            percent = macros.get(percent_key)
            if percent is None or gram_key in macros:
                continue
            macros[gram_key] = _grams_from_percent(calories, percent, KCAL_PER_GRAM[nutrient])

        return macros or None

    def _build_targets(
        self,
        sources: dict[str, dict[str, Any]],
        target_date: date,
    ) -> dict[str, Any]:
        diet_plan = self._diet_plan_settings(sources)
        calories = self._extract_calories(diet_plan)

        data: dict[str, Any] = {"date": target_date.isoformat()}
        if calories is not None:
            data["calories"] = _round_num(calories)
            macros = self._build_macro_overrides(calories)
            if macros:
                data["macros"] = macros
        return data

    def _resolve_goal_label(
        self,
        diet_plan: dict[str, Any],
        user_settings: dict[str, Any],
    ) -> str | None:
        start = _pick_number(user_settings.get("weightStartKg"))
        target = _pick_number(user_settings.get("weightTargetKg"))
        if start is not None and target is not None:
            if target > start + 0.1:
                return "Przybieranie masy"
            if target < start - 0.1:
                return "Odchudzanie"
            return "Utrzymanie wagi"

        direction = diet_plan.get("weightChangeDirection")
        if isinstance(direction, int) and direction in WEIGHT_DIRECTION_LABELS:
            return WEIGHT_DIRECTION_LABELS[direction][1]

        goal_key = str(direction).upper() if direction is not None else None
        return GOAL_LABELS.get(goal_key, goal_key) if goal_key else None

    def _build_plan(
        self,
        sources: dict[str, dict[str, Any]],
        target_date: date,
    ) -> dict[str, Any]:
        diet_plan = self._diet_plan_settings(sources)
        user_settings = self._user_settings(sources)

        data: dict[str, Any] = {"date": target_date.isoformat()}

        goal_label = self._resolve_goal_label(diet_plan, user_settings)
        if goal_label:
            data["goal_label"] = goal_label

        calories = self._extract_calories(diet_plan)
        if calories is not None:
            data["calories_target"] = _round_num(calories)

        for source_key, output_key in (
            ("weightStartKg", "start_weight_kg"),
            ("weightCurrentKg", "current_weight_kg"),
            ("weightTargetKg", "target_weight_kg"),
        ):
            value = _round_optional(user_settings.get(source_key))
            if value is not None:
                data[output_key] = value

        pace = _pick_number(diet_plan.get("weightChangeSpeedKg"))
        if pace is not None:
            data["weight_change_pace_kg"] = _round_num(pace)

        activity = _pick_number(diet_plan.get("activityTraining"))
        if activity is not None:
            data["activity_training"] = _round_num(activity)

        return data

    def _login(self) -> FitatuApiClient:
        logger.info("Logging in to Fitatu as %s", self._config.fitatu_email)
        return FitatuApiClient.login(
            self._config.fitatu_email,
            self._config.fitatu_password,
            persist_tokens=False,
        )

    def _resolve_session(self) -> dict[str, Any]:
        stored = self._tokens.load()
        if stored is not None:
            logger.info("Found stored Fitatu session, checking auth state")
            library = FitatuLibrary(session_data=stored.session_data)
            if self._ensure_authenticated(library):
                session = library.export_session_context(include_tokens=True)
                self._tokens.save(session)
                return session

        if not self._config.fitatu_configured:
            raise FitatuAuthError("Fitatu credentials missing")

        client = self._login()
        session = client.auth.to_session_data(include_tokens=True)
        self._tokens.save(session)
        return session

    def _ensure_authenticated(self, library: FitatuLibrary) -> bool:
        client = library._build_client(persist_tokens=False)
        state = client.describe_auth_state()
        lifecycle = state.get("lifecycle_state", "")
        logger.info("Fitatu auth lifecycle=%s", lifecycle)

        if lifecycle == "healthy":
            library.session_data = client.auth.to_session_data(include_tokens=True)
            return True

        if lifecycle in {"token_only", "refresh_only"} or client.auth.refresh_token:
            logger.info("Refreshing Fitatu session")
            refresh = client.reauthenticate(relogin_callback=self._relogin)
            if refresh.get("status") == "ok":
                library.session_data = client.auth.to_session_data(include_tokens=True)
                return True
            logger.warning("Fitatu refresh failed: %s", refresh)

        if self._config.fitatu_configured:
            logger.info("Re-logging in to Fitatu")
            login_client = self._login()
            library.session_data = login_client.auth.to_session_data(include_tokens=True)
            return True

        return False

    def _relogin(self, _auth: FitatuAuthContext) -> dict[str, Any]:
        if not self._config.fitatu_configured:
            return {}

        client = self._login()
        return {
            "bearer_token": client.auth.bearer_token,
            "refresh_token": client.auth.refresh_token,
        }

