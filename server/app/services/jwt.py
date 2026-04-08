"""
JWT token creation and decoding for Haven Cloud authentication.

Uses python-jose with HS256 algorithm. Tokens carry a `type` claim
("access" or "refresh") to prevent refresh tokens from being used
as access tokens and vice versa.
"""

from datetime import datetime, timedelta, timezone

from jose import jwt, JWTError  # noqa: F401 — JWTError re-exported for callers

from app.config import settings


def create_access_token(subject: str) -> str:
    """
    Create a short-lived JWT access token.

    @param subject: The user ID as a string (str(user.id)).
    """
    expire = datetime.now(timezone.utc) + timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    payload = {
        "sub": subject,
        "exp": expire,
        "type": "access",
    }
    return jwt.encode(payload, settings.SECRET_KEY, algorithm="HS256")


def create_refresh_token(subject: str) -> str:
    """
    Create a long-lived JWT refresh token.

    @param subject: The user ID as a string (str(user.id)).
    """
    expire = datetime.now(timezone.utc) + timedelta(days=settings.REFRESH_TOKEN_EXPIRE_DAYS)
    payload = {
        "sub": subject,
        "exp": expire,
        "type": "refresh",
    }
    return jwt.encode(payload, settings.SECRET_KEY, algorithm="HS256")


def decode_token(token: str) -> dict:
    """
    Decode and verify a JWT token.

    Raises JWTError if the token is invalid, expired, or tampered with.
    """
    return jwt.decode(token, settings.SECRET_KEY, algorithms=["HS256"])
