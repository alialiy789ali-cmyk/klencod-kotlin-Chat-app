package com.example.crypto

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/** Exportable P-256 key material. Persist private keys only inside the encrypted local vault. */
data class E2eeKeyMaterial(
    val publicKeyPem: String,
    val privateKeyPkcs8Base64: String
)

data class EncryptedEnvelope(
    val version: Int = 1,
    val messageId: String,
    val sentAt: Long,
    val ephemeralPublicKey: String,
    val nonce: String,
    val ciphertext: String,
    val tag: String,
    val signature: String
)

/**
 * P-256 ephemeral-static ECDH + HKDF-SHA256 + AES-256-GCM and ECDSA sender authentication.
 * This is an interoperable prototype envelope, not Signal/Double Ratchet and not forward-secret
 * after compromise of the recipient's long-term encryption private key.
 */
object E2eeEngine {
    private const val CURVE = "secp256r1"
    private const val GCM_NONCE_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val AES_KEY_BYTES = 32
    private const val INFO = "sovereign-message-v1"
    private val random = SecureRandom()

    fun generateKeyMaterial(): E2eeKeyMaterial {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec(CURVE), random)
        val pair = generator.generateKeyPair()
        val publicPem = "-----BEGIN PUBLIC KEY-----\n" +
            encodeBase64(pair.public.encoded).chunked(64).joinToString("\n") +
            "\n-----END PUBLIC KEY-----"
        return E2eeKeyMaterial(publicPem, encodeBase64(pair.private.encoded))
    }

    fun encrypt(
        plaintext: String,
        senderHandle: String,
        recipientHandle: String,
        recipientEncryptionPublicKeyPem: String,
        senderSigningPrivateKeyPkcs8Base64: String,
        messageId: String = UUID.randomUUID().toString(),
        sentAt: Long = System.currentTimeMillis()
    ): EncryptedEnvelope {
        require(plaintext.isNotEmpty()) { "Message cannot be empty" }
        val sender = normalizeHandle(senderHandle)
        val recipient = normalizeHandle(recipientHandle)
        val recipientKey = publicKeyFromPem(recipientEncryptionPublicKeyPem)
        val ephemeral = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec(CURVE), random)
        }.generateKeyPair()
        val aad = associatedData(sender, recipient, sentAt)
        val aesKey = deriveAesKey(sharedSecret(ephemeral.private, recipientKey), aad)
        val nonce = ByteArray(GCM_NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aad)
        val sealed = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val ciphertext = sealed.copyOfRange(0, sealed.size - GCM_TAG_BITS / 8)
        val tag = sealed.copyOfRange(sealed.size - GCM_TAG_BITS / 8, sealed.size)
        require(runCatching { UUID.fromString(messageId) }.isSuccess) { "Message ID must be a UUID" }
        val unsigned = EncryptedEnvelope(
            messageId = messageId,
            sentAt = sentAt,
            ephemeralPublicKey = encodeBase64(ephemeral.public.encoded),
            nonce = encodeBase64(nonce),
            ciphertext = encodeBase64(ciphertext),
            tag = encodeBase64(tag),
            signature = ""
        )
        val signature = sign(canonical(unsigned, sender, recipient), privateKeyFromBase64(senderSigningPrivateKeyPkcs8Base64))
        return unsigned.copy(signature = encodeBase64(signature))
    }

    fun decrypt(
        envelope: EncryptedEnvelope,
        senderHandle: String,
        recipientHandle: String,
        recipientEncryptionPrivateKeyPkcs8Base64: String,
        senderSigningPublicKeyPem: String
    ): String {
        require(envelope.version == 1) { "Unsupported encrypted envelope version" }
        val sender = normalizeHandle(senderHandle)
        val recipient = normalizeHandle(recipientHandle)
        require(verifySignature(envelope, sender, recipient, senderSigningPublicKeyPem)) { "Invalid sender signature" }
        val ephemeralKey = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(decodeBase64(envelope.ephemeralPublicKey))
        )
        val recipientPrivate = privateKeyFromBase64(recipientEncryptionPrivateKeyPkcs8Base64)
        val aad = associatedData(sender, recipient, envelope.sentAt)
        val aesKey = deriveAesKey(sharedSecret(recipientPrivate, ephemeralKey), aad)
        val nonce = decodeBase64(envelope.nonce)
        require(nonce.size == GCM_NONCE_BYTES) { "Invalid GCM nonce" }
        val ciphertext = decodeBase64(envelope.ciphertext)
        val tag = decodeBase64(envelope.tag)
        require(tag.size == GCM_TAG_BITS / 8) { "Invalid GCM authentication tag" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext + tag).toString(Charsets.UTF_8)
    }

    fun verifySignature(envelope: EncryptedEnvelope, senderHandle: String, recipientHandle: String, senderPublicKeyPem: String): Boolean =
        runCatching {
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(publicKeyFromPem(senderPublicKeyPem))
            verifier.update(canonical(envelope, normalizeHandle(senderHandle), normalizeHandle(recipientHandle)))
            verifier.verify(decodeBase64(envelope.signature))
        }.getOrDefault(false)

    fun canonical(envelope: EncryptedEnvelope, senderHandle: String, recipientHandle: String): ByteArray =
        listOf(
            "sovereign-envelope-v1", envelope.messageId, normalizeHandle(senderHandle), normalizeHandle(recipientHandle),
            envelope.sentAt.toString(), envelope.ephemeralPublicKey, envelope.nonce,
            envelope.ciphertext, envelope.tag
        ).joinToString("\n").toByteArray(Charsets.UTF_8)

    private fun sign(data: ByteArray, privateKey: PrivateKey): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey, random)
            update(data)
            sign()
        }

    private fun sharedSecret(privateKey: PrivateKey, publicKey: java.security.PublicKey): ByteArray =
        KeyAgreement.getInstance("ECDH").run {
            init(privateKey)
            doPhase(publicKey, true)
            generateSecret()
        }

    private fun deriveAesKey(secret: ByteArray, aad: ByteArray): ByteArray {
        val salt = MessageDigest.getInstance("SHA-256").digest(aad)
        val extract = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(salt, "HmacSHA256"))
        }.doFinal(secret)
        return Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(extract, "HmacSHA256"))
            update(INFO.toByteArray(Charsets.UTF_8))
            update(1.toByte())
            doFinal().copyOf(AES_KEY_BYTES)
        }
    }

    private fun associatedData(sender: String, recipient: String, sentAt: Long) =
        "sovereign-aes-gcm-v1\n$sender\n$recipient\n$sentAt".toByteArray(Charsets.UTF_8)

    private fun encodeBase64(value: ByteArray) = Base64.encodeToString(value, Base64.NO_WRAP)

    private fun decodeBase64(value: String) = Base64.decode(value, Base64.DEFAULT)

    private fun normalizeHandle(value: String) = value.trim().removePrefix("@").lowercase(java.util.Locale.ROOT)

    private fun publicKeyFromPem(pem: String) = KeyFactory.getInstance("EC").generatePublic(
        X509EncodedKeySpec(decodeBase64(pem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "").filterNot(Char::isWhitespace)))
    )

    private fun privateKeyFromBase64(encoded: String) = KeyFactory.getInstance("EC")
        .generatePrivate(PKCS8EncodedKeySpec(decodeBase64(encoded)))
}
