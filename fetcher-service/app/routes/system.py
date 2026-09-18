from fastapi import APIRouter, Request

from app.schemas import HealthResponse, WelcomeResponse

router = APIRouter(tags=["System"])


@router.get("/", response_model=WelcomeResponse)
def root(request: Request) -> dict:
    """Welcome endpoint with list of available routes."""
    endpoints = sorted(
        {
            route.path
            for route in request.app.routes
            if getattr(route, "path", None) and not route.path.startswith("/openapi")
        },
    )
    return {
        "name": "Fetcher Service",
        "status": "running",
        "docs": "/docs",
        "endpoints": endpoints,
    }


@router.get("/health", response_model=HealthResponse)
def health() -> dict:
    """Liveness check."""
    return {"status": "ok"}
