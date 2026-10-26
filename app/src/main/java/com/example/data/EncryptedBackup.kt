package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Portable password-protected export. Voice-note files are intentionally not included. */
object EncryptedBackup {
    private const val MAGIC = "SVBK001!"
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 310_000
    private val random = SecureRandom()
    private val snapshotAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(RepositorySnapshot::class.java)

    fun encrypt(snapshot: RepositorySnapshot, password: CharArray): ByteArray {
        require(password.size >= 10) { "Backup password must be at least 10 characters" }
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val plaintext = snapshotAdapter.toJson(snapshot).toByteArray(Charsets.UTF_8)
        require(plaintext.size <= MAX_BACKUP_BYTES - 64) { "Backup is too large" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt))
        val header = MAGIC.toByteArray(Charsets.US_ASCII)
        cipher.updateAAD(header)
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArrayOutputStream().use { output ->
            DataOutputStream(output).use { data ->
                data.write(header)
                data.write(salt)
                data.write(cipher.iv)
                data.writeInt(ciphertext.size)
                data.write(ciphertext)
            }
            output.toByteArray()
        }
    }

    fun decrypt(bytes: ByteArray, password: CharArray): RepositorySnapshot? = runCatching {
        require(password.size >= 10) { "Backup password must be at least 10 characters" }
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val header = ByteArray(MAGIC.length).also(input::readFully)
            require(String(header, Charsets.US_ASCII) == MAGIC) { "Unsupported backup format" }
            val salt = ByteArray(SALT_SIZE).also(input::readFully)
            val iv = ByteArray(IV_SIZE).also(input::readFully)
            val size = input.readInt()
            require(size in 16..MAX_BACKUP_BYTES && size == input.available()) { "Invalid backup length" }
            val ciphertext = ByteArray(size).also(input::readFully)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(header)
            snapshotAdapter.fromJson(cipher.doFinal(ciphertext).toString(Charsets.UTF_8))
                ?: error("Backup is empty")
        }
    }.getOrNull()

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, 256)
        return try {
            // SHA-1 is retained here for API 24 compatibility; the high iteration count and
            // AES-GCM authentication protect the portable backup. The on-device vault uses Keystore.
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
            SecretKeySpec(key, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private const val MAX_BACKUP_BYTES = 64 * 1024 * 1024
}
