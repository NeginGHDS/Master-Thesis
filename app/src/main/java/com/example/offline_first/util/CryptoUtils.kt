package com.example.offline_first.util

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object CryptoUtils {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hashPassword(password: String, salt: String): String {
        val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
        val spec = PBEKeySpec(password.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH)
        val hash = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun verify(password: String, salt: String, expectedHash: String): Boolean {
        val actual = Base64.decode(hashPassword(password, salt), Base64.NO_WRAP)
        val expected = Base64.decode(expectedHash, Base64.NO_WRAP)
        return MessageDigest.isEqual(actual, expected)
    }
}