package com.example.offline_first.util

import org.kotlincrypto.macs.hmac.sha2.HmacSHA256
import org.kotlincrypto.random.CryptoRand
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * PBKDF2-HMAC-SHA256 password hashing used for the offline (local) credential store.
 *
 * Ported from the Android-only `javax.crypto`/`android.util.Base64` implementation to pure
 * multiplatform primitives — [HmacSHA256] (org.kotlincrypto) for the PRF, [CryptoRand] for the
 * salt's secure randomness, and [Base64] (Kotlin stdlib, unwrapped like Android's `NO_WRAP`) for
 * encoding — so the algorithm, iteration count, key length and encoding are byte-for-byte
 * identical to the original on every platform: existing local `User` rows keep validating after
 * the migration.
 */
@OptIn(ExperimentalEncodingApi::class)
object CryptoUtils {

    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH_BYTES = 256 / 8
    private const val SALT_LENGTH_BYTES = 16
    private const val HASH_LENGTH_BYTES = 32 // SHA-256 output size

    fun generateSalt(): String {
        val bytes = CryptoRand.Default.nextBytes(ByteArray(SALT_LENGTH_BYTES))
        return Base64.encode(bytes)
    }

    fun hashPassword(password: String, salt: String): String {
        val saltBytes = Base64.decode(salt)
        val derived = pbkdf2HmacSha256(password.encodeToByteArray(), saltBytes, ITERATIONS, KEY_LENGTH_BYTES)
        return Base64.encode(derived)
    }

    fun verify(password: String, salt: String, expectedHash: String): Boolean {
        val actual = Base64.decode(hashPassword(password, salt))
        val expected = Base64.decode(expectedHash)
        return constantTimeEquals(actual, expected)
    }

    /** RFC 8018 PBKDF2 with HMAC-SHA256 as the pseudorandom function. */
    private fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, keyLengthBytes: Int): ByteArray {
        val blockCount = (keyLengthBytes + HASH_LENGTH_BYTES - 1) / HASH_LENGTH_BYTES
        val output = ByteArray(blockCount * HASH_LENGTH_BYTES)

        for (blockIndex in 1..blockCount) {
            val block = deriveBlock(password, salt, iterations, blockIndex)
            block.copyInto(output, destinationOffset = (blockIndex - 1) * HASH_LENGTH_BYTES)
        }
        return output.copyOf(keyLengthBytes)
    }

    private fun deriveBlock(password: ByteArray, salt: ByteArray, iterations: Int, blockIndex: Int): ByteArray {
        val blockIndexBytes = byteArrayOf(
            (blockIndex ushr 24).toByte(),
            (blockIndex ushr 16).toByte(),
            (blockIndex ushr 8).toByte(),
            blockIndex.toByte()
        )

        var u = hmacSha256(password, salt + blockIndexBytes)
        val result = u.copyOf()
        for (iteration in 2..iterations) {
            u = hmacSha256(password, u)
            for (i in result.indices) {
                result[i] = (result[i].toInt() xor u[i].toInt()).toByte()
            }
        }
        return result
    }

    private fun hmacSha256(key: ByteArray, message: ByteArray): ByteArray {
        val mac = HmacSHA256(key)
        mac.update(message)
        return mac.doFinal()
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
        }
        return diff == 0
    }
}
