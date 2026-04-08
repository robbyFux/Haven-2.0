"""
Health check endpoint.

Used by Docker health checks, load balancers, and monitoring tools.
"""

from fastapi import APIRouter

router = APIRouter(tags=["health"])


@router.get("/health")
async def health_check() -> dict:
    """Return server health status and version."""
    return {"status": "ok", "version": "1.0.0"}
