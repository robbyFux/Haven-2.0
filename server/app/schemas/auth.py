"""
Pydantic v2 schemas for authentication endpoints.

Covers registration, login, token response, 2FA setup/verification, and user info.
"""

from pydantic import BaseModel, Field


class RegisterRequest(BaseModel):
    username: str = Field(..., min_length=3, max_length=50)
    password: str = Field(..., min_length=8)


class RegisterResponse(BaseModel):
    id: int
    username: str
    user_key: str


class LoginRequest(BaseModel):
    username: str
    password: str
    totp_code: str | None = None


class TokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class RefreshRequest(BaseModel):
    refresh_token: str


class TotpSetupResponse(BaseModel):
    secret: str
    qr_code_base64: str


class TotpVerifyRequest(BaseModel):
    code: str = Field(..., min_length=6, max_length=6)


class UserResponse(BaseModel):
    id: int
    username: str
    is_admin: bool
    totp_enabled: bool

    model_config = {"from_attributes": True}
