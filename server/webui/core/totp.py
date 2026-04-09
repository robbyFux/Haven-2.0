"""
TOTP helper for Haven Web UI.

Thin wrapper that replicates server/app/services/totp.py exactly — the Fernet
key derivation MUST be identical so that TOTP secrets encrypted by FastAPI can
be decrypted here and vice versa.

Key derivation: SHA-256(SECRET_KEY) → 32 bytes → base64url → Fernet key.
This is deterministic: the same SECRET_KEY always yields the same Fernet key.
"""

import base64
import hashlib
import os
from io import BytesIO

import pyotp
import qrcode
from cryptography.fernet import Fernet


def _get_fernet() -> Fernet:
    """
    Derive a stable Fernet key from the SECRET_KEY environment variable.

    Matches server/app/services/totp.py exactly for cross-service compatibility.
    """
    secret_key = os.environ.get("SECRET_KEY", "change-me-to-random-64-chars")
    key_bytes = hashlib.sha256(secret_key.encode()).digest()  # always 32 bytes
    fernet_key = base64.urlsafe_b64encode(key_bytes)
    return Fernet(fernet_key)


def generate_totp_secret() -> str:
    """Generate a random base32 TOTP secret."""
    return pyotp.random_base32()


def get_totp_uri(secret: str, username: str, issuer: str = "Haven") -> str:
    """
    Build the otpauth:// provisioning URI for authenticator apps.

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
