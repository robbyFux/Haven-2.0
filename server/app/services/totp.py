"""
TOTP (Time-based One-Time Password) service for Haven Cloud 2FA.

Handles secret generation, QR code creation, code verification, and
Fernet-based encryption of the TOTP secret at rest.

The Fernet key is derived from settings.SECRET_KEY via SHA-256 hash
truncated to 32 bytes, then base64url-encoded — making it a valid Fernet key
without requiring a separate secret configuration value.
"""

import base64
import hashlib
from io import BytesIO

import pyotp
import qrcode
from cryptography.fernet import Fernet

from app.config import settings


def _get_fernet() -> Fernet:
    """
    Derive a stable Fernet key from settings.SECRET_KEY.

    SHA-256 of the key → 32 bytes → base64url → valid Fernet key.
    This is deterministic: the same SECRET_KEY always yields the same Fernet key.
    """
    key_bytes = hashlib.sha256(settings.SECRET_KEY.encode()).digest()  # always 32 bytes
    fernet_key = base64.urlsafe_b64encode(key_bytes)
    return Fernet(fernet_key)


def generate_totp_secret() -> str:
    """Generate a random base32 TOTP secret."""
    return pyotp.random_base32()


def get_totp_uri(secret: str, username: str, issuer: str = "Haven") -> str:
    """
    Build the otpauth:// provisioning URI for use with authenticator apps.

    @param secret: The base32 TOTP secret.
    @param username: The user's username (shown in authenticator app).
    @param issuer: The issuer name shown in authenticator apps.
    """
    totp = pyotp.TOTP(secret)
    return totp.provisioning_uri(name=username, issuer_name=issuer)


def get_totp_qr_png(uri: str) -> bytes:
    """
    Render the otpauth:// URI as a QR code PNG.

    @param uri: The provisioning URI from get_totp_uri().
    @return: PNG image bytes.
    """
    img = qrcode.make(uri)
    buf = BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def verify_totp(secret: str, token: str) -> bool:
    """
    Verify a TOTP code against the given secret.

    valid_window=1 allows one step of clock drift (±30 s).

    @param secret: The plaintext base32 TOTP secret.
    @param token: The 6-digit code from the authenticator app.
    """
    return pyotp.TOTP(secret).verify(token, valid_window=1)


def encrypt_totp_secret(secret: str) -> str:
    """
    Encrypt a plaintext TOTP secret with Fernet.

    @param secret: Plaintext base32 TOTP secret.
    @return: Base64-encoded Fernet ciphertext (safe to store in DB).
    """
    fernet = _get_fernet()
    return fernet.encrypt(secret.encode()).decode()


def decrypt_totp_secret(encrypted: str) -> str:
    """
    Decrypt a Fernet-encrypted TOTP secret.

    @param encrypted: The ciphertext produced by encrypt_totp_secret().
    @return: Plaintext base32 TOTP secret.
    """
    fernet = _get_fernet()
    return fernet.decrypt(encrypted.encode()).decode()
