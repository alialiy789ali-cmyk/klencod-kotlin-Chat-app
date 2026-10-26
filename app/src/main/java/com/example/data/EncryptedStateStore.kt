package com.example.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A snapshot of the local app state. It is encrypted before being written to preferences. */
data class RepositorySnapshot(
    val currentUser: UserProfile,
    val threads: List<ChatThread>,
    val messages: Map<String, List<MessageItem>>,
    val channels: List<ChannelItem>,
    val channelPosts: Map<String, List<ChannelPost>>,
    val feedPosts: List<SocialPost>,
    val gatewaySession: GatewaySession? = null
)

/**
 * Encrypted, device-local persistence for the prototype. The AES key is generated and retained
 * by Android Keystore; only AES-GCM ciphertext and its nonce are stored in SharedPreferences.
 * This protects data at rest on this device. It is not end-to-end encryption between users.
 */
class EncryptedStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val adapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(RepositorySnapshot::class.java)
    private val lock = Any()

    fun read(): RepositorySnapshot? = synchronized(lock) {
        val encoded = preferences.getString(STATE_KEY, null) ?: return@synchronized null
        runCatching {
            val envelope = Base64.decode(encoded, Base64.NO_WRAP)
            require(envelope.size > NONCE_SIZE) { "Invalid encrypted state" }
            val nonce = envelope.copyOfRange(0, NONCE_SIZE)
            val ciphertext = envelope.copyOfRange(NONCE_SIZE, envelope.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, nonce))
            val json = cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
            adapter.fromJson(json)
        }.getOrNull()
    }

    fun write(snapshot: RepositorySnapshot) = synchronized(lock) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(adapter.toJson(snapshot).toByteArray(Charsets.UTF_8))
        val envelope = cipher.iv + ciphertext
        check(preferences.edit().putString(STATE_KEY, Base64.encodeToString(envelope, Base64.NO_WRAP)).commit()) {
            "Unable to persist encrypted local state"
        }
    }

    fun clear() = synchronized(lock) {
        preferences.edit().clear().commit()
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        Unit
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES = "sovereign_encrypted_state"
        const val STATE_KEY = "encrypted_snapshot"
        const val KEY_ALIAS = "sovereign_local_state_key_v1"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val NONCE_SIZE = 12
        const val TAG_LENGTH_BITS = 128
    }
}
