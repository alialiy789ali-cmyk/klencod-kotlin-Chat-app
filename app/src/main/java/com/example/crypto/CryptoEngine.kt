package com.example.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

object CryptoEngine {

    private val secureRandom = SecureRandom()

    fun generateSafetyNumber(peerA: String, peerB: String): String {
        val combined = listOf(peerA, peerB).sorted().joinToString("::")
        val hash = sha256(combined.toByteArray())
        // Format into 4 chunks of 4 digits
        val digits = hash.filter { it.isDigit() }.padEnd(16, '7').take(16)
        return "${digits.substring(0, 4)} ${digits.substring(4, 8)} ${digits.substring(8, 12)} ${digits.substring(12, 16)}"
    }

    fun generateKeyFingerprint(handle: String): String {
        val randomSalt = ByteArray(16).apply { secureRandom.nextBytes(this) }
        val input = "$handle:${bytesToHex(randomSalt)}"
        val hash = sha256(input.toByteArray())
        return hash.take(16).chunked(4).joinToString(" ").uppercase(Locale.ROOT)
    }

    fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return bytesToHex(hash)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789ABCDEF"
        val result = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val i = b.toInt() and 0xFF
            result.append(hexChars[i shr 4])
            result.append(hexChars[i and 0x0F])
        }
        return result.toString()
    }

}
