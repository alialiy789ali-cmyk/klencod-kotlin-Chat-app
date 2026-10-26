package com.example.data

/** Stored only inside EncryptedStateStore. A blank token means re-authentication is required. */
data class GatewaySession(
    val accessToken: String = "",
    val handle: String,
    val displayName: String,
    val signingPublicKeyPem: String,
    val signingPrivateKeyPkcs8Base64: String,
    val encryptionPublicKeyPem: String,
    val encryptionPrivateKeyPkcs8Base64: String
)
