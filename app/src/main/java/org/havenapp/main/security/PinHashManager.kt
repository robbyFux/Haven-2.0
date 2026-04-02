package org.havenapp.main.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * SHA-256 + salt PIN hashing.
 *
 * Salt is a random 16-byte value generated per PIN setup.
 * Uses [MessageDigest.isEqual] for constant-time comparison to prevent timing attacks.
 */
object PinHashManager {

    private const val SALT_SIZE = 16

    data class HashResult(
        val hash: String,  // Base64-encoded SHA-256 hash
        val salt: String,  // Base64-encoded random salt
    )

    /** Generates a salted SHA-256 hash of the given PIN. */
    fun hashPin(pin: String): HashResult {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray(Charsets.UTF_8) + salt)
        return HashResult(
            hash = Base64.encodeToString(hash, Base64.NO_WRAP),
            salt = Base64.encodeToString(salt, Base64.NO_WRAP),
        )
    }

    /** Verifies a PIN against a stored hash and salt. Constant-time comparison. */
    fun verifyPin(pin: String, storedHash: String, storedSalt: String): Boolean {
        val salt = Base64.decode(storedSalt, Base64.NO_WRAP)
        val inputHash = MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray(Charsets.UTF_8) + salt)
        val expectedHash = Base64.decode(storedHash, Base64.NO_WRAP)
        return MessageDigest.isEqual(inputHash, expectedHash)
    }
}
