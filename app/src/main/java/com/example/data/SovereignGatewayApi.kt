package com.example.data

import com.example.crypto.EncryptedEnvelope
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GatewayApiException(val statusCode: Int, message: String) : IOException(message)

data class GatewayAuthResult(
    val token: String, val handle: String, val displayName: String,
    val identityPublicKey: String, val encryptionPublicKey: String
)
data class GatewayPeerKeys(val identityPublicKey: String, val encryptionPublicKey: String)
data class GatewayRemoteMessage(
    val serverId: String,
    val clientMessageId: String,
    val senderHandle: String,
    val recipientHandle: String,
    val envelope: EncryptedEnvelope,
    val deliveredAt: String?,
    val readAt: String?
)

/** Thin authenticated API client for the self-hosted gateway. */
class SovereignGatewayApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    private val envelopeAdapter = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
        .adapter(EncryptedEnvelope::class.java)
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun register(
        baseUrl: String,
        handle: String,
        displayName: String,
        password: String,
        signingPublicKey: String,
        encryptionPublicKey: String
    ): GatewayAuthResult = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("handle", handle)
            .put("displayName", displayName)
            .put("password", password)
            .put("identityPublicKey", signingPublicKey)
            .put("encryptionPublicKey", encryptionPublicKey)
        val response = execute(jsonRequest(baseUrl, "v1/auth/register", body, token = null, method = "POST"))
        val user = response.getJSONObject("user")
        GatewayAuthResult(response.getString("token"), user.getString("handle"), user.getString("display_name"), user.getString("identity_public_key"), user.getString("encryption_public_key"))
    }

    suspend fun login(baseUrl: String, handle: String, password: String): GatewayAuthResult = withContext(Dispatchers.IO) {
        val body = JSONObject().put("handle", handle).put("password", password)
        val response = execute(jsonRequest(baseUrl, "v1/auth/login", body, token = null, method = "POST"))
        val user = response.getJSONObject("user")
        GatewayAuthResult(response.getString("token"), user.getString("handle"), user.getString("display_name"), user.getString("identity_public_key"), user.getString("encryption_public_key"))
    }

    suspend fun getPeerKeys(baseUrl: String, token: String, handle: String): GatewayPeerKeys = withContext(Dispatchers.IO) {
        val response = execute(Request.Builder()
            .url(url(baseUrl, "v1/users/${handle.removePrefix("@").lowercase()}/key"))
            .header("Authorization", "Bearer $token")
            .get().build())
        GatewayPeerKeys(response.getString("identityPublicKey"), response.getString("encryptionPublicKey"))
    }

    suspend fun sendEncryptedMessage(
        baseUrl: String,
        token: String,
        recipient: String,
        envelope: EncryptedEnvelope,
        expiresInSeconds: Int
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("recipient", recipient)
            .put("envelope", JSONObject(envelopeAdapter.toJson(envelope)))
            .put("expiresInSeconds", expiresInSeconds.coerceIn(0, 86_400))
        val response = execute(jsonRequest(baseUrl, "v1/messages", body, token, "POST"))
        response.getJSONObject("message").getString("id")
    }

    suspend fun listMessages(baseUrl: String, token: String, limit: Int = 100): List<GatewayRemoteMessage> = withContext(Dispatchers.IO) {
        val url = url(baseUrl, "v1/messages").newBuilder().addQueryParameter("limit", limit.coerceIn(1, 100).toString()).build()
        val response = execute(Request.Builder().url(url).header("Authorization", "Bearer $token").get().build())
        val records = response.getJSONArray("messages")
        buildList(records.length()) {
            for (index in 0 until records.length()) {
                val item = records.getJSONObject(index)
                val payload = item.getJSONObject("encrypted_payload")
                val envelope = envelopeAdapter.fromJson(payload.toString()) ?: continue
                add(
                    GatewayRemoteMessage(
                        serverId = item.getString("id"),
                        clientMessageId = item.optString("client_message_id", envelope.messageId),
                        senderHandle = item.getString("senderHandle"),
                        recipientHandle = item.getString("recipientHandle"),
                        envelope = envelope,
                        deliveredAt = item.optString("delivered_at").takeIf { it.isNotBlank() && it != "null" },
                        readAt = item.optString("read_at").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
    }

    suspend fun markRead(baseUrl: String, token: String, serverId: String) = withContext(Dispatchers.IO) {
        execute(jsonRequest(baseUrl, "v1/messages/$serverId/read", JSONObject(), token, "PATCH"))
        Unit
    }

    fun openEvents(baseUrl: String, token: String, onEvent: (String) -> Unit): WebSocket {
        val httpUrl = url(baseUrl, "v1/events")
        val websocketUrl = httpUrl.toString()
            .replaceFirst("^https://".toRegex(), "wss://")
            .replaceFirst("^http://".toRegex(), "ws://")
        val request = Request.Builder().url(websocketUrl).build()
        return client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(JSONObject().put("type", "auth").put("token", token).toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { JSONObject(text).optString("type") }
                    .getOrNull()?.takeIf { it.isNotBlank() }?.let(onEvent)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onEvent("connection.error")
            }
        })
    }

    fun closeEvents(socket: WebSocket?) {
        socket?.close(1000, "Client closed")
    }

    private fun jsonRequest(baseUrl: String, path: String, json: JSONObject, token: String?, method: String): Request {
        val builder = Request.Builder().url(url(baseUrl, path)).header("Accept", "application/json")
        if (token != null) builder.header("Authorization", "Bearer $token")
        val body = json.toString().toRequestBody(jsonMediaType)
        return when (method) {
            "PATCH" -> builder.patch(body).build()
            else -> builder.post(body).build()
        }
    }

    private fun execute(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            if (!response.isSuccessful) {
                throw GatewayApiException(response.code, json.optString("error").ifBlank { "Gateway request failed (${response.code})" })
            }
            return json
        }
    }

    private fun url(baseUrl: String, path: String): HttpUrl {
        val base = baseUrl.trim().trimEnd('/').toHttpUrlOrNull()
            ?: throw IOException("Invalid gateway URL")
        if (base.scheme != "https" && base.host !in setOf("localhost", "10.0.2.2")) {
            throw IOException("Use HTTPS for a non-local gateway")
        }
        return base.newBuilder().addPathSegments(path).build()
    }
}
