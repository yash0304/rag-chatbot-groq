from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_name: str = "MindQuest"
    environment: str = "development"  # development | test | production
    api_v1_prefix: str = "/api/v1"

    database_url: str = "sqlite:///./mindquest.db"
    auto_create_tables: bool = True  # dev/test convenience; prod uses alembic

    jwt_secret: str = "change-me-in-production"
    jwt_algorithm: str = "HS256"
    access_token_minutes: int = 30
    refresh_token_days: int = 30

    cors_origins: str = "http://localhost:3000"

    # AI
    ai_provider: str = "stub"  # stub | openai | groq
    openai_api_key: str = ""
    openai_model: str = "gpt-4o-mini"
    openai_embed_model: str = "text-embedding-3-small"
    groq_api_key: str = ""
    groq_model: str = "llama-3.3-70b-versatile"

    # Vectors
    vector_backend: str = "memory"  # memory | qdrant
    qdrant_url: str = "http://localhost:6333"
    qdrant_collection: str = "mindquest_chunks"

    redis_url: str = ""  # empty -> in-process fallbacks

    storage_dir: str = "./data/uploads"
    max_upload_mb: int = 25

    # Rate limits (requests per minute); generous defaults, relaxed further in tests
    rate_limit_global: int = 120
    rate_limit_ai: int = 20
    rate_limit_auth: int = 10

    leaderboard_enabled: bool = True

    # Gamification tuning
    xp_document_uploaded: int = 15
    xp_document_processed: int = 20
    xp_habit_base: int = 15
    xp_milestone: int = 40
    xp_goal_bonus: int = 150
    xp_knowledge_consulted: int = 5
    knowledge_consulted_daily_cap: int = 5
    xp_weekly_review: int = 30

    @property
    def cors_origin_list(self) -> list[str]:
        return [o.strip() for o in self.cors_origins.split(",") if o.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
