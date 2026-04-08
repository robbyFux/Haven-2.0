"""
Cryptographic primitives for Haven Cloud.

Provides:
  - derive_key: Argon2id key derivation from password + username (deterministic 256-bit key)
  - encrypt_file: AES-256-GCM encryption with random 12-byte nonce
  - decrypt_file: AES-256-GCM decryption

Key Derivation Design:
  Salt is derived deterministically from the username (lowercased, 16-byte UTF-8 padded)
  so the same user+password always yields the same key — no salt storage required.

  Argon2id parameters (OWASP recommendation for medium-security use case):
    time_cost=2, memory_cost=65536 (64 MB), parallelism=2, hash_len=32

Encryption Format:
  encrypt_file returns: nonce (12 bytes) || ciphertext+tag
  decrypt_file expects this exact layout.
"""

import os

from argon2.low_level import Type, hash_secret_raw
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

# ---------------------------------------------------------------------------
# Argon2id constants
# ---------------------------------------------------------------------------

ARGON2_TIME_COST = 2
ARGON2_MEMORY_COST = 65536  # 64 MB
ARGON2_PARALLELISM = 2
ARGON2_HASH_LEN = 32  # 256-bit key


def derive_key(password: str, username: str) -> bytes:
    """
    Derive a deterministic 256-bit AES key from a password and username using Argon2id.

    The salt is derived from the username (lowercased, zero-padded or truncated to 16 bytes)
    so the same (password, username) pair always produces the same key — no salt needs to be stored.

    @param password: plaintext password string (UTF-8)
    @param username: account username used as deterministic salt material (case-insensitive)
    @return: 32-byte key suitable for AES-256-GCM
    """
    # 16-byte deterministic salt from username (lower-cased, zero-padded to exactly 16 bytes)
    salt = username.lower().encode("utf-8").ljust(16, b"\x00")[:16]

    return hash_secret_raw(
        secret=password.encode("utf-8"),
        salt=salt,
        time_cost=ARGON2_TIME_COST,
        memory_cost=ARGON2_MEMORY_COST,
        parallelism=ARGON2_PARALLELISM,
        hash_len=ARGON2_HASH_LEN,
        type=Type.ID,
    )


def encrypt_file(data: bytes, key: bytes) -> bytes:
    """
    Encrypt bytes with AES-256-GCM.

    A fresh 12-byte random nonce is generated for every call.
    The nonce is prepended to the ciphertext so decrypt_file can locate it.

    @param data: plaintext bytes to encrypt
    @param key: 32-byte AES-256 key (e.g. from derive_key)
    @return: nonce (12 bytes) || ciphertext || GCM tag (16 bytes)
    """
    nonce = os.urandom(12)
    ct = AESGCM(key).encrypt(nonce, data, None)
    return nonce + ct


def decrypt_file(data: bytes, key: bytes) -> bytes:
    """
    Decrypt AES-256-GCM ciphertext produced by encrypt_file.

    @param data: nonce (12 bytes) || ciphertext || GCM tag, as returned by encrypt_file
    @param key: 32-byte AES-256 key (same key used during encryption)
    @return: original plaintext bytes
    @raises cryptography.exceptions.InvalidTag: if the key is wrong or data is tampered
    """
    nonce, ct = data[:12], data[12:]
    return AESGCM(key).decrypt(nonce, ct, None)
