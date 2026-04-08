"""
Async database engine, session factory, and FastAPI dependency.

Usage in endpoints:
    async def my_endpoint(db: DbSession) -> ...:
        result = await db.execute(select(User))
"""

from typing import Annotated, AsyncGenerator

from fastapi import Depends
from sqlalchemy.ext.asyncio import (
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

from app.config import settings

engine = create_async_engine(
    settings.DATABASE_URL,
    pool_pre_ping=True,
    echo=False,
)

AsyncSessionLocal = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False,
)


async def get_db() -> AsyncGenerator[AsyncSession, None]:
    """FastAPI dependency that yields an AsyncSession and closes it after the request."""
    async with AsyncSessionLocal() as session:
        yield session


# Convenience type alias for use in endpoint signatures
DbSession = Annotated[AsyncSession, Depends(get_db)]
