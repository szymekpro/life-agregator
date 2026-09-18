from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.logging_config import setup_logging
from app.routes import fitatu, system
from app.services.fitatu import FitatuService
from config import AppConfig, load_config
from db import init_db


def create_app(config: AppConfig | None = None) -> FastAPI:
    app_config = config or load_config()
    setup_logging(app_config.log_level)

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        init_db(app_config.database_path)
        app.state.config = app_config
        app.state.fitatu = FitatuService(app_config)
        yield

    app = FastAPI(
        title="Fetcher Service (Fitatu)",
        version="1.0.0",
        lifespan=lifespan,
        docs_url="/docs",
    )

    app.include_router(system.router)
    app.include_router(fitatu.router, prefix="/api/v1")

    return app
