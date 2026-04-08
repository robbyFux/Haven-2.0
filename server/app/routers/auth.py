"""
Authentication router for Haven Cloud.

Endpoints:
  POST /register       — create account, returns user_key
  POST /login          — username+password (+ optional TOTP), returns JWT pair
  POST /refresh        — rotate tokens using refresh token
  GET  /me             — return current user info
  POST /2fa/setup      — generate TOTP secret + QR code
  POST /2fa/verify     — confirm TOTP code and enable 2FA
"""

import base64
import secrets

from fastapi import APIRouter, Depends, HTTPException, status
from passlib.context import CryptContext
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import DbSession, get_db
from app.dependencies.auth import get_current_user
from app.models.user import User
from app.schemas.auth import (
    LoginRequest,
    RefreshRequest,
    RegisterRequest,
    RegisterResponse,
    TokenResponse,
    TotpSetupResponse,
    TotpVerifyRequest,
    UserResponse,
)
from app.services.jwt import JWTError, create_access_token, create_refresh_token, decode_token
from app.services.totp import (
    decrypt_totp_secret,
    encrypt_totp_secret,
    generate_totp_secret,
    get_totp_qr_png,
    get_totp_uri,
    verify_totp,
)

router = APIRouter(tags=["auth"])

# Module-level bcrypt context — reused across requests
pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


@router.post("/register", response_model=RegisterResponse, status_code=status.HTTP_201_CREATED)
async def register(body: RegisterRequest, db: DbSession) -> RegisterResponse:
    """
    Register a new user account.

    Returns the generated user_key which is used for device-to-server authentication.
    """
    # Check username uniqueness
    existing = await db.execute(select(User).where(User.username == body.username))
    if existing.scalar_one_or_none() is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Username already taken",
        )

    password_hash = pwd_context.hash(body.password)
    user_key = f"haven_u_{secrets.token_hex(32)}"

    user = User(
        username=body.username,
        password_hash=password_hash,
        user_key=user_key,
    )
    db.add(user)
    await db.commit()
    await db.refresh(user)

    return RegisterResponse(id=user.id, username=user.username, user_key=user.user_key)


@router.post("/login", response_model=TokenResponse)
async def login(body: LoginRequest, db: DbSession) -> TokenResponse:
    """
    Authenticate with username + password. Requires TOTP code if 2FA is enabled.

    Returns JWT access + refresh token pair on success.
    """
    result = await db.execute(select(User).where(User.username == body.username))
    user = result.scalar_one_or_none()

    if user is None or not pwd_context.verify(body.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid username or password",
        )

    if user.totp_enabled:
        if body.totp_code is None:
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="TOTP code required",
                headers={"X-TOTP-Required": "true"},
            )
        plaintext_secret = decrypt_totp_secret(user.totp_secret)
        if not verify_totp(plaintext_secret, body.totp_code):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid TOTP code",
            )

    subject = str(user.id)
    return TokenResponse(
        access_token=create_access_token(subject),
        refresh_token=create_refresh_token(subject),
    )


@router.post("/refresh", response_model=TokenResponse)
async def refresh(body: RefreshRequest) -> TokenResponse:
    """
    Issue a new JWT pair using a valid refresh token.

    The old refresh token is consumed; both access and refresh tokens are rotated.
    """
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Invalid or expired refresh token",
    )
    try:
        payload = decode_token(body.refresh_token)
        if payload.get("type") != "refresh":
            raise credentials_exception
        subject: str | None = payload.get("sub")
        if subject is None:
            raise credentials_exception
    except JWTError:
        raise credentials_exception

    return TokenResponse(
        access_token=create_access_token(subject),
        refresh_token=create_refresh_token(subject),
    )


@router.get("/me", response_model=UserResponse)
async def me(current_user: User = Depends(get_current_user)) -> UserResponse:
    """Return the authenticated user's profile."""
    return UserResponse.model_validate(current_user)


@router.post("/2fa/setup", response_model=TotpSetupResponse)
async def setup_2fa(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> TotpSetupResponse:
    """
    Generate a TOTP secret and QR code for 2FA enrollment.

    The secret is stored encrypted. 2FA is NOT yet active — the user must call
    /2fa/verify with a valid code to enable it.
    """
    secret = generate_totp_secret()
    uri = get_totp_uri(secret, current_user.username)
    qr_png = get_totp_qr_png(uri)
    qr_base64 = base64.b64encode(qr_png).decode()

    current_user.totp_secret = encrypt_totp_secret(secret)
    await db.commit()

    return TotpSetupResponse(secret=secret, qr_code_base64=qr_base64)


@router.post("/2fa/verify")
async def verify_2fa(
    body: TotpVerifyRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
) -> dict:
    """
    Confirm a TOTP code and enable 2FA for the current user.

    Returns {"status": "2fa_enabled"} on success.
    """
    if current_user.totp_secret is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="2FA setup not initiated — call /2fa/setup first",
        )

    plaintext_secret = decrypt_totp_secret(current_user.totp_secret)
    if not verify_totp(plaintext_secret, body.code):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Invalid TOTP code",
        )

    current_user.totp_enabled = True
    await db.commit()

    return {"status": "2fa_enabled"}
