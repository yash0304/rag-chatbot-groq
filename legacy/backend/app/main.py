import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.v1 import auth, chat, documents, gamification, goals, habits, misc, quests, users
from app.core import ratelimit
from app.core.config import get_settings
from app.db.base import Base
from app.db.session import SessionLocal, engine
from app.services.gamification import seed_catalogs

logging.basicConfig(
    level=logging.INFO,
    format='{"level":"%(levelname)s","logger":"%(name)s","msg":"%(message)s"}',
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    if settings.auto_create_tables:
        Base.metadata.create_all(bind=engine)
    db = SessionLocal()
    try:
        seed_catalogs(db)
    finally:
        db.close()
    yield


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(
        title="MindQuest API",
        version="1.0.0",
        description="AI-first Second Brain with RPG progression.",
        lifespan=lifespan,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origin_list,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    @app.middleware("http")
    async def security_and_rate_limit(request: Request, call_next):
        if request.url.path.startswith(settings.api_v1_prefix):
            client_ip = request.client.host if request.client else "unknown"
            if not ratelimit.allow(f"global:{client_ip}", settings.rate_limit_global):
                return JSONResponse(
                    {"detail": "Rate limit exceeded"},
                    status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                    headers={"Retry-After": "60"},
                )
        response = await call_next(request)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["X-Frame-Options"] = "DENY"
        response.headers["Referrer-Policy"] = "no-referrer"
        return response

    for router in (
        auth.router, users.router, documents.router, chat.router, quests.router,
        habits.router, goals.router, gamification.router, misc.router,
    ):
        app.include_router(router, prefix=settings.api_v1_prefix)

    @app.get("/healthz", include_in_schema=False)
    def healthz():
        return {"status": "ok"}

    @app.get("/readyz", include_in_schema=False)
    def readyz():
        checks = {"database": False, "vectors": False, "ai_provider": settings.ai_provider}
        try:
            db = SessionLocal()
            db.execute(__import__("sqlalchemy").text("SELECT 1"))
            db.close()
            checks["database"] = True
        except Exception:
            pass
        try:
            from app.services.vector import get_vector_store

            get_vector_store()
            checks["vectors"] = True
        except Exception:
            pass
        if not checks["database"]:
            raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, detail=checks)
        return checks

    return app


app = create_app()
