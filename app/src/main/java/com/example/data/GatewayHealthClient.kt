package com.example.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.TimeUnit

/** Performs a real HTTPS readiness probe against the configured self-hosted gateway. */
class GatewayHealthClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    fun probe(configuredUrl: String): Long? {
        val base = configuredUrl.trim().trimEnd('/')
        val url = (if (base.endsWith("/health")) base else "$base/health").toHttpUrlOrNull() ?: return null
        if (url.scheme != "https" && url.host !in setOf("localhost", "10.0.2.2")) return null

        val request = Request.Builder().url(url).get().header("Accept", "application/json").build()
        val started = System.nanoTime()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val payload = response.body?.string().orEmpty()
                if (!payload.contains("\"status\":\"ok\"")) return null
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started).coerceAtLeast(1)
            }
        }.getOrNull()
    }
}
