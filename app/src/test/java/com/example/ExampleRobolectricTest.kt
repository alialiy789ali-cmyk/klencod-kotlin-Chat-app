package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.E2eeEngine
import com.example.crypto.EncryptedEnvelope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Sovereign", appName)
  }

  @Test
  fun `ecdh envelope encrypts authenticates and rejects tampering`() {
    val senderSigning = E2eeEngine.generateKeyMaterial()
    val recipientEncryption = E2eeEngine.generateKeyMaterial()
    val envelope = E2eeEngine.encrypt(
      plaintext = "رسالة خاصة",
      senderHandle = "@alice",
      recipientHandle = "@bob",
      recipientEncryptionPublicKeyPem = recipientEncryption.publicKeyPem,
      senderSigningPrivateKeyPkcs8Base64 = senderSigning.privateKeyPkcs8Base64,
      sentAt = 1_750_000_000_000L
    )

    assertTrue(E2eeEngine.verifySignature(envelope, "@alice", "@bob", senderSigning.publicKeyPem))
    assertEquals(
      "رسالة خاصة",
      E2eeEngine.decrypt(
        envelope,
        "@alice",
        "@bob",
        recipientEncryption.privateKeyPkcs8Base64,
        senderSigning.publicKeyPem
      )
    )
    assertFalse(E2eeEngine.verifySignature(envelope.copy(ciphertext = envelope.ciphertext + "A"), "@alice", "@bob", senderSigning.publicKeyPem))
  }

  @Test
  fun `decrypts the shared gateway protocol vector`() {
    val json = javaClass.classLoader!!.getResourceAsStream("e2ee-v1-vector.json")!!
      .bufferedReader(Charsets.UTF_8).use { it.readText() }
    val vector = JSONObject(json)
    val envelope = Moshi.Builder()
      .add(KotlinJsonAdapterFactory())
      .build()
      .adapter(EncryptedEnvelope::class.java)
      .fromJson(vector.getJSONObject("envelope").toString())!!

    assertTrue(E2eeEngine.verifySignature(
      envelope,
      vector.getString("senderHandle"),
      vector.getString("recipientHandle"),
      vector.getString("senderSigningPublicKeyPem")
    ))
    assertEquals(
      vector.getString("plaintext"),
      E2eeEngine.decrypt(
        envelope,
        vector.getString("senderHandle"),
        vector.getString("recipientHandle"),
        vector.getString("recipientEncryptionPrivateKeyPkcs8Base64"),
        vector.getString("senderSigningPublicKeyPem")
      )
    )
  }
}
