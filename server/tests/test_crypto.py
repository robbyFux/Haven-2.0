"""
Tests for server/app/services/crypto.py.

Covers:
  - derive_key: determinism, cross-user isolation, key length
  - encrypt_file / decrypt_file: round-trip, wrong key rejection, nonce randomness
"""

import pytest
from cryptography.exceptions import InvalidTag

from app.services.crypto import decrypt_file, derive_key, encrypt_file


def test_derive_key_deterministic() -> None:
    """Same password + username always produces the same key."""
    key1 = derive_key("s3cr3t", "alice")
    key2 = derive_key("s3cr3t", "alice")
    assert key1 == key2


def test_derive_key_different_users() -> None:
    """Different usernames produce different keys even with the same password."""
    key_alice = derive_key("s3cr3t", "alice")
    key_bob = derive_key("s3cr3t", "bob")
    assert key_alice != key_bob


def test_derive_key_length() -> None:
    """Key is exactly 32 bytes (256-bit AES key)."""
    key = derive_key("password", "user")
    assert len(key) == 32


def test_encrypt_decrypt_roundtrip() -> None:
    """encrypt_file followed by decrypt_file returns the original plaintext."""
    key = derive_key("roundtrip", "testuser")
    plaintext = b"Hello, Haven Cloud!"
    ciphertext = encrypt_file(plaintext, key)
    recovered = decrypt_file(ciphertext, key)
    assert recovered == plaintext


def test_decrypt_wrong_key_fails() -> None:
    """Decrypting with a different key raises InvalidTag (GCM authentication failure)."""
    key_correct = derive_key("correct_password", "user")
    key_wrong = derive_key("wrong_password", "user")
    ciphertext = encrypt_file(b"secret data", key_correct)
    with pytest.raises(InvalidTag):
        decrypt_file(ciphertext, key_wrong)


def test_encrypt_produces_different_nonces() -> None:
    """Two encryptions of the same plaintext produce different ciphertexts (random nonce)."""
    key = derive_key("nonce_test", "user")
    plaintext = b"same data"
    ct1 = encrypt_file(plaintext, key)
    ct2 = encrypt_file(plaintext, key)
    # Both should decrypt correctly but the raw bytes differ (different nonces)
    assert ct1 != ct2
    assert decrypt_file(ct1, key) == plaintext
    assert decrypt_file(ct2, key) == plaintext
