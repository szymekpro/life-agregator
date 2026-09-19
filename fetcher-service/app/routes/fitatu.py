from datetime import date

from fastapi import APIRouter, Query, Request

from app.schemas import (
    FitatuDailyResponse,
    FitatuPlanResponse,
    FitatuTargetsResponse,
    FitatuTokenResponse,
)

router = APIRouter(prefix="/fitatu", tags=["Fitatu"])


@router.post("/token", response_model=FitatuTokenResponse)
def obtain_token(request: Request) -> dict:
    """Obtain and store Fitatu token."""
    return request.app.state.fitatu.obtain_token()


@router.get("/daily", response_model=FitatuDailyResponse)
def daily_macros(
    request: Request,
    day: date | None = Query(default=None, description="Date (YYYY-MM-DD), defaults to today"),
) -> dict:
    """Fetch today's logged macros and calories from Fitatu."""
    return request.app.state.fitatu.fetch_daily(day)


@router.get("/plan", response_model=FitatuPlanResponse, response_model_exclude_none=True)
def current_plan(
    request: Request,
    day: date | None = Query(default=None, description="Date (YYYY-MM-DD), defaults to today"),
) -> dict:
    """Fetch current diet plan (goal, kcal target, weights) from Fitatu."""
    return request.app.state.fitatu.fetch_plan(day)


@router.get("/targets", response_model=FitatuTargetsResponse, response_model_exclude_none=True)
def daily_targets(
    request: Request,
    day: date | None = Query(default=None, description="Date (YYYY-MM-DD), defaults to today"),
) -> dict:
    """Fetch daily calorie and macro targets from Fitatu Daily Goals."""
    return request.app.state.fitatu.fetch_targets(day)