"""
Application configuration loaded from environment variables / .env file.

All settings are read once at import time via the module-level `settings` instance.
"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    # Database
    DATABASE_URL: str = "postgresql+asyncpg://haven:haven@localhost:5432/haven"

    # Redis / Celery broker
    REDIS_URL: str = "redis://localhost:6379/0"

    # JWT secret — MUST be changed in production
    SECRET_KEY: str = "change-me-to-random-64-chars"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 30
    REFRESH_TOKEN_EXPIRE_DAYS: int = 7

    # Media storage
    MEDIA_ROOT: str = "./media"

    # AI backend: none | tflite | openrouter
    AI_BACKEND: str = "none"
    TFLITE_MODEL_PATH: str = "./efficientdet_lite0.tflite"
    OPENROUTER_API_KEY: str = ""
    OPENROUTER_MODEL: str = "google/gemini-flash-1.5"

    # SMTP email notifications
    SMTP_HOST: str = ""
    SMTP_PORT: int = 587
    SMTP_USER: str = ""
    SMTP_PASSWORD: str = ""
    SMTP_FROM: str = "haven@example.com"

    # Signal-cli REST API notifications
    SIGNAL_API_URL: str = ""
    SIGNAL_SENDER: str = ""
    SIGNAL_AUTH_TOKEN: str = ""

    # Pushover notifications
    PUSHOVER_APP_TOKEN: str = ""


settings = Settings()
